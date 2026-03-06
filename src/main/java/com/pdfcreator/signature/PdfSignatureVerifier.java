package com.pdfcreator.signature;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.util.Store;

import java.io.*;
import java.security.KeyStore;
import java.security.cert.*;
import java.util.*;
import java.util.logging.Logger;
import javax.net.ssl.*;

/**
 * Verifies all digital signatures present in a PDF document.
 *
 * For each PDSignature found in the document, produces a VerificationResult
 * describing:
 *   - Whether the CMS digest matches the signed byte ranges (integrity)
 *   - Whether the ByteRange covers the entire file (no post-sign additions)
 *   - Whether the certificate chain validates to a trusted root
 *   - Whether the signing certificate was valid at signing time
 *   - Whether a TSA timestamp is embedded
 *
 * ── VERIFICATION FLOW ─────────────────────────────────────────────────────
 *
 *  For each PDSignature in doc.getSignatureDictionaries():
 *
 *  1. Extract /Contents bytes — the raw DER-encoded CMS SignedData blob
 *  2. Extract ByteRange — int[4]: [offset1, length1, offset2, length2]
 *     The signed bytes are: file[offset1..offset1+length1]
 *                         + file[offset2..offset2+length2]
 *     (the /Contents placeholder bytes between these two ranges are excluded)
 *  3. Read those exact bytes from the file on disk
 *  4. Construct CMSSignedData(cmsBytes) — parses the CMS blob
 *  5. Wrap signed bytes in CMSProcessableByteArray
 *  6. For each SignerInfo: JcaSimpleSignerInfoVerifierBuilder.verify()
 *     This checks: the SignerInfo digest matches the content bytes AND
 *     the signature bytes decrypt to the digest using the signer's public key
 *  7. Extract X509Certificate from CMS cert store
 *  8. Check certificate validity at signedAt time
 *  9. Attempt chain validation against trust anchors
 * 10. Check coverage: byteRange[2] + byteRange[3] == file.length()
 * 11. Check for TSA: look for id-aa-signatureTimeStampToken unsigned attribute
 *
 * ── TRUST MODEL ───────────────────────────────────────────────────────────
 *
 * certTrusted = true requires the full chain to validate against at least
 * one trust anchor. Trust anchors come from (in priority order):
 *   1. A truststore file supplied via verifyWithTruststore()
 *   2. The JVM default cacerts (used by verify() with no truststore)
 *
 * Self-signed certificates never produce certTrusted=true unless the cert
 * itself is added as a trust anchor. signatureValid can still be true for
 * self-signed certs — integrity is independent of trust.
 *
 * ── THREAD SAFETY ─────────────────────────────────────────────────────────
 *
 * Stateless — a single instance can be shared across threads.
 * Each verify() call opens its own PDDocument and file streams.
 */
public class PdfSignatureVerifier {

    private static final Logger logger = Logger.getLogger(PdfSignatureVerifier.class.getName());

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Verifies all signatures in the given PDF using the JVM's default
     * CA trust store (suitable for PDFs signed with commercially issued certs).
     *
     * @param pdfPath path to the signed PDF
     * @return list of VerificationResult, one per signature found (empty if none)
     * @throws IOException if the file cannot be read
     */
    public List<VerificationResult> verify(String pdfPath) throws IOException {
        return verify(pdfPath, null, null, null);
    }

    /**
     * Verifies all signatures using a custom truststore.
     *
     * Use this when the signing certificate was issued by a CA not in the
     * JVM default cacerts — e.g. a self-signed cert or an internal CA cert.
     *
     * @param pdfPath            path to the signed PDF
     * @param truststorePath     path to a JKS or PKCS12 truststore,
     *                           or a DER/PEM .cer file containing a single cert
     * @param truststorePassword truststore password (null if a .cer file)
     * @param truststoreType     "JKS", "PKCS12", or "CER" (null = auto-detect)
     * @return list of VerificationResult
     * @throws IOException if the file cannot be read
     */
    public List<VerificationResult> verify(String pdfPath,
                                            String truststorePath,
                                            String truststorePassword,
                                            String truststoreType) throws IOException {
        File pdfFile = requireFile(pdfPath);
        Set<TrustAnchor> trustAnchors = buildTrustAnchors(
            truststorePath, truststorePassword, truststoreType);

        List<VerificationResult> results = new ArrayList<>();

        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            List<PDSignature> signatures = doc.getSignatureDictionaries();
            if (signatures.isEmpty()) {
                logger.info("No signatures found in: " + pdfPath);
                return results;
            }

            logger.info("Found " + signatures.size() + " signature(s) in: " + pdfPath);

            for (int i = 0; i < signatures.size(); i++) {
                PDSignature sig = signatures.get(i);
                VerificationResult result = verifyOne(
                    pdfFile, sig, i, trustAnchors);
                results.add(result);
            }
        }

        return results;
    }

    /**
     * Lists all signatures in a PDF without performing cryptographic
     * verification. Fast — does not read ByteRange bytes or validate digests.
     *
     * @param pdfPath path to the PDF
     * @return list of VerificationResult with signatureValid=false and
     *         only metadata fields populated
     * @throws IOException if the file cannot be read
     */
    public List<VerificationResult> listSignatures(String pdfPath) throws IOException {
        File pdfFile = requireFile(pdfPath);
        List<VerificationResult> results = new ArrayList<>();

        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            List<PDSignature> signatures = doc.getSignatureDictionaries();
            for (int i = 0; i < signatures.size(); i++) {
                PDSignature sig = signatures.get(i);
                results.add(buildMetadataOnly(sig, i, pdfFile.length()));
            }
        }
        return results;
    }

    // -----------------------------------------------------------------------
    // Per-signature verification
    // -----------------------------------------------------------------------

    private VerificationResult verifyOne(File pdfFile, PDSignature sig,
                                          int index,
                                          Set<TrustAnchor> trustAnchors) {

        VerificationResult.Builder result = new VerificationResult.Builder()
            .signatureIndex(index)
            .reason(sig.getReason())
            .location(sig.getLocation())
            .contactInfo(sig.getContactInfo());

        // Signed-at time from the signature dictionary
        Calendar signDate = sig.getSignDate();
        if (signDate != null) result.signedAt(signDate.getTime());

        try {
            // Step 1: Extract CMS bytes (/Contents)
            byte[] cmsBytes = sig.getContents();
            if (cmsBytes == null || cmsBytes.length == 0)
                return result.signatureValid(false)
                             .failureReason("Empty /Contents — no CMS data")
                             .build();

            // Step 2: Extract ByteRange and read signed bytes from disk
            int[] byteRange = sig.getByteRange();
            // byteRange = [offset1, length1, offset2, length2]
            // signed bytes = file[offset1 .. offset1+length1-1]
            //              + file[offset2 .. offset2+length2-1]
            byte[] signedBytes = readByteRange(pdfFile, byteRange);

            // Step 3: Coverage check — signed ranges must cover entire file
            long fileLength = pdfFile.length();
            boolean coverageComplete =
                ((long) byteRange[2] + byteRange[3]) >= fileLength;
            result.coverageComplete(coverageComplete);

            // Step 4: Parse CMS SignedData
            CMSSignedData signedData = new CMSSignedData(
                new org.bouncycastle.cms.CMSProcessableByteArray(signedBytes), cmsBytes);

            // Step 5: Get signer certificate from CMS cert store
            SignerInformationStore signerInfoStore = signedData.getSignerInfos();
            Collection<SignerInformation> signers = signerInfoStore.getSigners();

            if (signers.isEmpty())
                return result.signatureValid(false)
                             .failureReason("No SignerInfo in CMS blob")
                             .build();

            SignerInformation signerInfo = signers.iterator().next();

            @SuppressWarnings("unchecked")
            Store<X509CertificateHolder> certStore = signedData.getCertificates();
            Collection<X509CertificateHolder> certMatches =
                certStore.getMatches(signerInfo.getSID());

            if (certMatches.isEmpty())
                return result.signatureValid(false)
                             .failureReason("Signing certificate not found in CMS blob")
                             .build();

            X509CertificateHolder certHolder = certMatches.iterator().next();
            X509Certificate signerCert = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certHolder);

            // Populate certificate identity fields
            result.signerName(PdfSigner.extractCN(
                      signerCert.getSubjectX500Principal().getName()))
                  .issuerName(signerCert.getIssuerX500Principal().getName())
                  .serialNumber(signerCert.getSerialNumber().toString(16).toUpperCase())
                  .certNotBefore(signerCert.getNotBefore())
                  .certNotAfter(signerCert.getNotAfter());

            // Step 6: Verify CMS signature (digest + RSA check)
            boolean sigValid;
            String  failureReason = null;
            try {
                sigValid = signerInfo.verify(
                    new JcaSimpleSignerInfoVerifierBuilder()
                        .setProvider("BC")
                        .build(signerCert));
            } catch (Exception e) {
                sigValid = false;
                failureReason = "CMS verification exception: " + e.getMessage();
            }
            result.signatureValid(sigValid);
            if (failureReason != null) result.failureReason(failureReason);

            // Step 7: Certificate validity at signing time
            Date signingTime = signDate != null ? signDate.getTime() : new Date();
            boolean expiredAtSigning = signingTime.before(signerCert.getNotBefore())
                                    || signingTime.after(signerCert.getNotAfter());
            result.certExpiredAtSigning(expiredAtSigning);

            // Step 8: Certificate chain trust validation
            boolean trusted = validateChain(signedData, signerCert, trustAnchors);
            result.certTrusted(trusted);

            // Step 9: TSA timestamp — look for id-aa-signatureTimeStampToken
            boolean hasTimestamp = signerInfo.getUnsignedAttributes() != null
                && signerInfo.getUnsignedAttributes()
                    .get(org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
                        .id_aa_signatureTimeStampToken) != null;
            result.timestamped(hasTimestamp);

            logger.info("Signature #" + (index + 1) + ": valid=" + sigValid
                + ", trusted=" + trusted + ", coverage=" + coverageComplete);

        } catch (Exception e) {
            logger.warning("Signature #" + (index + 1) + " verification error: " + e.getMessage());
            result.signatureValid(false)
                  .failureReason("Verification error: " + e.getMessage());
        }

        return result.build();
    }

    // -----------------------------------------------------------------------
    // ByteRange reading
    // -----------------------------------------------------------------------

    /**
     * Reads the exact bytes covered by the ByteRange array from the PDF file.
     *
     * PDF ByteRange = [offset1, length1, offset2, length2]
     * These two ranges together describe all bytes that were signed.
     * The gap between them (offset1+length1 to offset2) contains the
     * /Contents placeholder and is intentionally excluded from the digest.
     */
    private static byte[] readByteRange(File file, int[] byteRange) throws IOException {
        int totalLength = byteRange[1] + byteRange[3];
        byte[] result = new byte[totalLength];

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            // First range
            raf.seek(byteRange[0]);
            raf.readFully(result, 0, byteRange[1]);

            // Second range
            raf.seek(byteRange[2]);
            raf.readFully(result, byteRange[1], byteRange[3]);
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Certificate chain validation
    // -----------------------------------------------------------------------

    /**
     * Attempts to build and validate the certificate chain for signerCert
     * against the given trust anchors.
     *
     * If trustAnchors is empty, falls back to the JVM default KeyStore (cacerts).
     * Returns false (not throws) on any chain validation failure so that
     * signatureValid can still be reported accurately alongside certTrusted.
     */
    private boolean validateChain(CMSSignedData signedData,
                                   X509Certificate signerCert,
                                   Set<TrustAnchor> trustAnchors) {
        try {
            Set<TrustAnchor> anchors = trustAnchors;

            // Fall back to JVM default cacerts if no explicit trust anchors
            if (anchors.isEmpty()) {
                anchors = loadDefaultTrustAnchors();
            }

            if (anchors.isEmpty()) {
                logger.warning("No trust anchors available — certTrusted will be false");
                return false;
            }

            // Collect all certificates: signer cert + any certs from CMS store
            List<X509Certificate> certList = new ArrayList<>();
            certList.add(signerCert);

            @SuppressWarnings("unchecked")
            Store<X509CertificateHolder> cmsStore = signedData.getCertificates();
            JcaX509CertificateConverter converter =
                new JcaX509CertificateConverter().setProvider("BC");

            for (X509CertificateHolder holder : cmsStore.getMatches(null)) {
                X509Certificate cert = converter.getCertificate(holder);
                if (!cert.equals(signerCert)) certList.add(cert);
            }

            // Build PKIX cert path
            CertPathBuilder builder = CertPathBuilder.getInstance("PKIX");
            X509CertSelector selector = new X509CertSelector();
            selector.setCertificate(signerCert);

            PKIXBuilderParameters params = new PKIXBuilderParameters(anchors, selector);
            params.setRevocationEnabled(false);  // skip CRL/OCSP for offline verification
            CollectionCertStoreParameters storeParams =
                new CollectionCertStoreParameters(certList);
            params.addCertStore(
                CertStore.getInstance("Collection", storeParams));

            builder.build(params);  // throws CertPathBuilderException if chain fails
            logger.fine("Certificate chain validated successfully for: "
                + signerCert.getSubjectX500Principal().getName());
            return true;

        } catch (CertPathBuilderException e) {
            logger.fine("Chain validation failed: " + e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warning("Chain validation error: " + e.getMessage());
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Trust anchor building
    // -----------------------------------------------------------------------

    /**
     * Builds trust anchors from a supplied truststore or .cer file.
     * Returns an empty set if truststorePath is null (caller uses JVM default).
     */
    private Set<TrustAnchor> buildTrustAnchors(String truststorePath,
                                                String password,
                                                String type) throws IOException {
        if (truststorePath == null || truststorePath.isBlank())
            return Collections.emptySet();

        File tsFile = requireFile(truststorePath);
        Set<TrustAnchor> anchors = new HashSet<>();

        try {
            // Auto-detect .cer / .crt files — treat as single DER/PEM certificate
            String lowerPath = truststorePath.toLowerCase();
            boolean isCertFile = lowerPath.endsWith(".cer")
                || lowerPath.endsWith(".crt")
                || lowerPath.endsWith(".pem")
                || "CER".equalsIgnoreCase(type);

            if (isCertFile) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                try (FileInputStream fis = new FileInputStream(tsFile)) {
                    X509Certificate cert = (X509Certificate) cf.generateCertificate(fis);
                    anchors.add(new TrustAnchor(cert, null));
                    logger.info("Loaded trust anchor from .cer: "
                        + cert.getSubjectX500Principal().getName());
                }
            } else {
                // JKS or PKCS12 truststore
                String ksType = type != null ? type : detectKeystoreType(lowerPath);
                KeyStore ts = KeyStore.getInstance(ksType);
                char[] pwd = password != null ? password.toCharArray() : null;
                try (FileInputStream fis = new FileInputStream(tsFile)) {
                    ts.load(fis, pwd);
                }
                Enumeration<String> aliases = ts.aliases();
                while (aliases.hasMoreElements()) {
                    String alias = aliases.nextElement();
                    Certificate cert = ts.getCertificate(alias);
                    if (cert instanceof X509Certificate x509) {
                        anchors.add(new TrustAnchor(x509, null));
                    }
                }
                logger.info("Loaded " + anchors.size()
                    + " trust anchor(s) from truststore: " + truststorePath);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to load truststore: " + e.getMessage(), e);
        }
        return anchors;
    }

    /**
     * Loads trust anchors from the JVM's default cacerts keystore.
     * This covers all commercially issued certificates trusted by the JVM.
     */
    private Set<TrustAnchor> loadDefaultTrustAnchors() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);   // null = use JVM default
            Set<TrustAnchor> anchors = new HashSet<>();
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager x509tm) {
                    for (X509Certificate ca : x509tm.getAcceptedIssuers()) {
                        anchors.add(new TrustAnchor(ca, null));
                    }
                }
            }
            logger.fine("Loaded " + anchors.size() + " default JVM trust anchors");
            return anchors;
        } catch (Exception e) {
            logger.warning("Could not load default JVM trust anchors: " + e.getMessage());
            return Collections.emptySet();
        }
    }

    // -----------------------------------------------------------------------
    // List-only (no crypto) helper
    // -----------------------------------------------------------------------

    private VerificationResult buildMetadataOnly(PDSignature sig, int index,
                                                   long fileLength) {
        int[] byteRange = sig.getByteRange();
        boolean coverageComplete = byteRange != null
            && ((long) byteRange[2] + byteRange[3]) >= fileLength;

        VerificationResult.Builder b = new VerificationResult.Builder()
            .signatureIndex(index)
            .reason(sig.getReason())
            .location(sig.getLocation())
            .contactInfo(sig.getContactInfo())
            .coverageComplete(coverageComplete);

        if (sig.getName() != null) b.signerName(sig.getName());
        Calendar signDate = sig.getSignDate();
        if (signDate != null) b.signedAt(signDate.getTime());

        return b.build();
    }

    // -----------------------------------------------------------------------
    // Utility helpers
    // -----------------------------------------------------------------------

    private static String detectKeystoreType(String lowerPath) {
        if (lowerPath.endsWith(".p12") || lowerPath.endsWith(".pfx")) return "PKCS12";
        return "JKS";
    }

    private static File requireFile(String path) throws IOException {
        File f = new File(path);
        if (!f.exists() || !f.isFile())
            throw new IOException("File not found: " + path);
        return f;
    }
}
