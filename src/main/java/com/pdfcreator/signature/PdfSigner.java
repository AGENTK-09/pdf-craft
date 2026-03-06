package com.pdfcreator.signature;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.visible.PDVisibleSigProperties;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.visible.PDVisibleSignDesigner;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.logging.Logger;

/**
 * Core engine for applying digital signatures to PDF documents.
 *
 * Implements PDFBox's SignatureInterface — PDFBox calls sign(InputStream)
 * during saveIncremental() and this class produces the CMS SignedData bytes
 * that PDFBox embeds in the /Contents entry of the signature dictionary.
 *
 * ── SIGNING FLOW ──────────────────────────────────────────────────────────
 *
 *  1. Load PKCS12/JKS keystore → extract PrivateKey + Certificate[] chain
 *  2. Load input PDF via Loader.loadPDF()
 *  3. Create PDSignature — set Filter, SubFilter, Name, Reason, Location,
 *     Contact, SignDate
 *  4. Optionally create a visible signature widget (PDVisibleSignDesigner)
 *  5. doc.addSignature(sig, this [SignatureInterface], sigOpts)
 *  6. doc.saveIncremental(inputStream, outputStream)
 *     → PDFBox calls this.sign(contentStream) during save
 *     → sign() builds CMSSignedData from the byte ranges
 *     → Optionally fetches RFC 3161 timestamp from TSA
 *     → Returns DER-encoded CMS bytes
 *  7. Build SignatureResult from certificate fields
 *
 * ── INCREMENTAL SAVE ──────────────────────────────────────────────────────
 *
 * PDF signatures REQUIRE incremental save. The original bytes are never
 * rewritten — only a new revision is appended. The ByteRange array in the
 * signature dictionary describes exactly which byte ranges were signed,
 * excluding the /Contents placeholder itself. PDFBox handles this entirely
 * through saveIncremental().
 *
 * ── CONTENT ESTIMATION BUFFER ─────────────────────────────────────────────
 *
 * PDFBox reserves space for the /Contents value before the actual CMS bytes
 * are known. The SignatureOptions.setPreferredSignatureSize() must be large
 * enough to hold the final CMS blob. We default to 32768 bytes (32 KB) which
 * comfortably fits SHA256withRSA with a 2048-bit key + 3-cert chain + TSA token.
 * Increase if using larger keys or longer chains.
 *
 * ── THREAD SAFETY ─────────────────────────────────────────────────────────
 *
 * PdfSigner is NOT thread-safe — PDFBox PDDocument and SignatureInterface
 * state (privateKey, chain) are set per-call. Create one instance per thread
 * or per sign() call when signing in parallel batches.
 */
public class PdfSigner implements SignatureInterface {

    private static final Logger logger = Logger.getLogger(PdfSigner.class.getName());

    /**
     * Reserved space in bytes for the /Contents placeholder.
     * Must be larger than the actual CMS blob produced by sign().
     * 32 KB is sufficient for RSA-2048 + 3-cert chain + TSA token.
     * Increase to 65536 for ECC or very long certificate chains.
     */
    private static final int SIGNATURE_RESERVE_BYTES = 32768;

    // Set per sign() call — used by the SignatureInterface callback
    private PrivateKey    privateKey;
    private Certificate[] certChain;
    private SigningOptions currentOpts;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Signs the PDF described by opts and writes the signed output.
     *
     * @param opts fully-configured SigningOptions
     * @return SignatureResult containing signer identity and operation details
     * @throws IOException              on file read/write errors
     * @throws GeneralSecurityException on keystore or cryptography errors
     */
    public SignatureResult sign(SigningOptions opts)
            throws IOException, GeneralSecurityException {

        logger.info("Signing: " + opts);
        this.currentOpts = opts;

        // 1. Load keystore and extract key material
        KeyStore ks = loadKeyStore(opts);
        String alias = resolveAlias(ks, opts);
        this.privateKey = (PrivateKey) ks.getKey(alias, opts.getKeystorePassword().toCharArray());
        this.certChain  = ks.getCertificateChain(alias);

        if (this.privateKey == null)
            throw new GeneralSecurityException(
                "No private key found for alias '" + alias + "' in keystore: " + opts.getKeystorePath());
        if (this.certChain == null || this.certChain.length == 0)
            throw new GeneralSecurityException(
                "No certificate chain for alias '" + alias + "' in keystore: " + opts.getKeystorePath());

        X509Certificate leafCert = (X509Certificate) certChain[0];
        logger.info("Signing with: " + leafCert.getSubjectX500Principal().getName());

        // 2. Prepare output file
        File inputFile  = new File(opts.getInputPath());
        File outputFile = new File(opts.getOutputPath());
        ensureParentDir(outputFile);

        // 3. Load PDF
        try (PDDocument doc = Loader.loadPDF(inputFile)) {

            // 4. Build PDSignature dictionary
            PDSignature sig = new PDSignature();
            sig.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            sig.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);

            // Signer name: explicit override, or CN from leaf certificate
            String signerDisplayName = opts.getSignerName() != null
                ? opts.getSignerName()
                : extractCN(leafCert.getSubjectX500Principal().getName());
            sig.setName(signerDisplayName);

            if (opts.getReason()      != null) sig.setReason(opts.getReason());
            if (opts.getLocation()    != null) sig.setLocation(opts.getLocation());
            if (opts.getContactInfo() != null) sig.setContactInfo(opts.getContactInfo());
            sig.setSignDate(Calendar.getInstance());

            // 5. Build SignatureOptions (PDFBox's, not ours)
            SignatureOptions pdfboxSigOpts = new SignatureOptions();
            pdfboxSigOpts.setPreferredSignatureSize(SIGNATURE_RESERVE_BYTES);

            // 5a. Visible signature widget
            int actualPage = 0; // 0-based page index used in result
            if (opts.isVisible()) {
                int totalPages = doc.getNumberOfPages();
                // signaturePage is 1-based; -1 = last page
                int pageIdx = opts.getSignaturePage() < 1
                    ? totalPages - 1
                    : Math.min(opts.getSignaturePage() - 1, totalPages - 1);
                actualPage = pageIdx + 1; // store 1-based for result

                PDVisibleSignDesigner designer = new PDVisibleSignDesigner(
                    doc, buildSignatureImage(signerDisplayName, opts), pageIdx);
                designer.xAxis(opts.getSigX())
                        .yAxis(opts.getSigY())
                        .width(opts.getSigWidth())
                        .height(opts.getSigHeight())
                        .zoom(0)
                        .signatureFieldName("Signature" + (pageIdx + 1));

                PDVisibleSigProperties props = new PDVisibleSigProperties();
                props.signerName(signerDisplayName)
                     .signerLocation(opts.getLocation() != null ? opts.getLocation() : "")
                     .signatureReason(opts.getReason()  != null ? opts.getReason()   : "")
                     .preferredSize(0)
                     .page(pageIdx)
                     .visualSignEnabled(true)
                     .setPdVisibleSignature(designer)
                     .buildSignature();

                pdfboxSigOpts.setVisualSignature(props.getVisibleSignature());
                pdfboxSigOpts.setPage(pageIdx);
            }

            // 6. Register signature — PDFBox calls this.sign() during saveIncremental
            doc.addSignature(sig, this, pdfboxSigOpts);

            // 7. Incremental save
            // PDFBox 3.x saveIncremental(OutputStream) takes only the output stream.
            // The original source bytes are read internally from the pdfSource captured
            // by Loader.loadPDF(file) at load time — no FileInputStream needed.
            // The output must NEVER point to the same file as the input, as PDFBox
            // reads from source while writing to output simultaneously.
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                doc.saveIncremental(fos);
            }
        }

        logger.info("Signed PDF saved: " + opts.getOutputPath());

        // 8. Build result from certificate data
        X509Certificate leaf = (X509Certificate) certChain[0];
        return new SignatureResult.Builder()
            .inputPath(opts.getInputPath())
            .outputPath(opts.getOutputPath())
            .outputSizeBytes(outputFile.length())
            .signerName(extractCN(leaf.getSubjectX500Principal().getName()))
            .issuerName(leaf.getIssuerX500Principal().getName())
            .serialNumber(leaf.getSerialNumber().toString(16).toUpperCase())
            .signedAt(Calendar.getInstance().getTime())
            .certNotBefore(leaf.getNotBefore())
            .certNotAfter(leaf.getNotAfter())
            .timestamped(opts.hasTsa())
            .tsaUrl(opts.getTsaUrl())
            .visible(opts.isVisible())
            .signaturePage(opts.isVisible() ? (opts.getSignaturePage() < 1
                ? -1 : opts.getSignaturePage()) : 0)
            .algorithm("SHA256withRSA")
            .build();
    }

    // -----------------------------------------------------------------------
    // SignatureInterface — called by PDFBox during saveIncremental
    // -----------------------------------------------------------------------

    /**
     * Called by PDFBox to produce the CMS SignedData bytes for the /Contents value.
     *
     * The content stream contains exactly the bytes described by the ByteRange
     * array — the original file bytes excluding the /Contents placeholder itself.
     * We digest these bytes and produce a CMS detached signature.
     *
     * PDFBox reads the returned byte array and writes it (hex-encoded) into the
     * /Contents entry. The array must fit within SIGNATURE_RESERVE_BYTES.
     */
    @Override
    public byte[] sign(InputStream content) throws IOException {
        try {
            // Read all signed bytes into memory
            byte[] contentBytes = content.readAllBytes();

            // Build CMS SignedData generator
            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();

            // Add our signing certificate and full chain to the CMS store
            // so verifiers can walk the chain without fetching intermediates
            JcaCertStore certStore = new JcaCertStore(Arrays.asList(certChain));
            generator.addCertificates(certStore);

            // Build signer info: SHA-256 digest + RSA signature
            ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC")
                .build(privateKey);

            generator.addSignerInfoGenerator(
                new JcaSignerInfoGeneratorBuilder(
                    new JcaDigestCalculatorProviderBuilder()
                        .setProvider("BC").build())
                    .build(contentSigner, (X509Certificate) certChain[0]));

            // Generate detached signature (encapsulate=false means the content
            // bytes are NOT embedded inside the CMS blob — they stay in the PDF
            // byte ranges, referenced by the ByteRange array)
            CMSTypedData cmsData = new org.bouncycastle.cms.CMSProcessableByteArray(contentBytes);
            CMSSignedData signedData = generator.generate(cmsData, false);

            // Optionally fetch and embed TSA timestamp
            if (currentOpts != null && currentOpts.hasTsa()) {
                signedData = embedTimestamp(signedData, currentOpts.getTsaUrl());
            }

            byte[] cmsBytes = signedData.getEncoded();

            // Safety check — if this fires, increase SIGNATURE_RESERVE_BYTES
            if (cmsBytes.length > SIGNATURE_RESERVE_BYTES) {
                logger.warning("CMS bytes (" + cmsBytes.length +
                    ") exceed reserved space (" + SIGNATURE_RESERVE_BYTES +
                    "). Signature may be corrupted. Increase SIGNATURE_RESERVE_BYTES.");
            }

            return cmsBytes;

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("CMS signing failed: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // TSA timestamp embedding
    // -----------------------------------------------------------------------

    /**
     * Fetches an RFC 3161 timestamp token from the TSA and embeds it as an
     * unsigned attribute in the first SignerInfo of the CMS SignedData.
     *
     * The timestamp covers the encrypted digest bytes of the SignerInfo,
     * proving the time at which the signature was produced. This is a
     * "signature timestamp" (id-aa-signatureTimeStampToken) per RFC 3161.
     *
     * Steps:
     *   1. Extract the first SignerInfo's signature bytes
     *   2. POST to TSA with SHA-256 hash of those bytes
     *   3. Receive TimeStampToken (another CMS object)
     *   4. Wrap in an unsigned attribute bag
     *   5. Re-encode the CMS SignedData with the new attribute
     */
    private CMSSignedData embedTimestamp(CMSSignedData signedData,
                                          String tsaUrl) throws IOException {
        try {
            TsaClient tsaClient = new TsaClient(tsaUrl);

            // Get the first (and typically only) signer
            var signerInfos = signedData.getSignerInfos().getSigners();
            if (signerInfos.isEmpty())
                throw new IOException("No SignerInfo found in CMS for TSA embedding");

            SignerInformation signerInfo = signerInfos.iterator().next();

            // POST to TSA with the encrypted digest bytes (the actual CMS signature bytes)
            byte[] tsTokenBytes = tsaClient.getTimestampToken(signerInfo.getSignature());

            // Build the ASN.1 vector for the new unsigned attributes.
            // Start by copying any pre-existing unsigned attributes so we don't lose them.
            ASN1EncodableVector unsignedAttrs = new ASN1EncodableVector();
            org.bouncycastle.asn1.cms.AttributeTable existing = signerInfo.getUnsignedAttributes();
            if (existing != null) {
                unsignedAttrs = existing.toASN1EncodableVector();
            }

            // Wrap the TSA token DER bytes in a timestamp attribute
            // OID: id-aa-signatureTimeStampToken (1.2.840.113549.1.9.16.2.14)
            Attribute tsAttr = new Attribute(
                PKCSObjectIdentifiers.id_aa_signatureTimeStampToken,
                new DERSet(org.bouncycastle.asn1.ASN1Primitive.fromByteArray(tsTokenBytes)));
            unsignedAttrs.add(tsAttr);

            // replaceUnsignedAttributes is a STATIC method — must NOT be called as instance method.
            // It returns a new SignerInformation with the updated unsigned attributes;
            // the original signerInfo is immutable and unchanged.
            SignerInformation updatedSigner = SignerInformation.replaceUnsignedAttributes(
                signerInfo,
                new org.bouncycastle.asn1.cms.AttributeTable(unsignedAttrs));

            // Rebuild the SignedData with the updated signer list
            List<SignerInformation> newSigners = new ArrayList<>();
            for (SignerInformation si : signedData.getSignerInfos().getSigners()) {
                // Replace only the first signer (the one we timestamped); keep any others unchanged
                newSigners.add(si == signerInfo ? updatedSigner : si);
            }

            CMSSignedData timestampedSignedData =
                CMSSignedData.replaceSigners(signedData,
                    new org.bouncycastle.cms.SignerInformationStore(newSigners));

            logger.info("TSA timestamp embedded from: " + tsaUrl);
            return timestampedSignedData;

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("TSA timestamp embedding failed: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Visible signature helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a minimal 1x1 white PNG image as the base for the visible
     * signature widget. PDFBox overlays the signature text on top of this.
     *
     * For production, replace with a proper branded image (company logo etc.)
     * loaded from a file path in SigningOptions.
     */
    private static InputStream buildSignatureImage(String signerName,
                                                    SigningOptions opts) throws IOException {
        // 1x1 white PNG (89 bytes) — minimal valid PNG, PDFBox renders text on top
        byte[] minimalWhitePng = {
            (byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A, // PNG magic
            0x00,0x00,0x00,0x0D,0x49,0x48,0x44,0x52,        // IHDR chunk
            0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x01,        // 1x1 dimensions
            0x08,0x02,0x00,0x00,0x00,(byte)0x90,0x77,0x53,(byte)0xDE, // bit depth/color/CRC
            0x00,0x00,0x00,0x0C,0x49,0x44,0x41,0x54,        // IDAT chunk
            0x08,(byte)0xD7,0x63,(byte)0xF8,(byte)0xFF,(byte)0xFF,0x3F,0x00,
            0x05,(byte)0xFE,0x02,(byte)0xFE,(byte)0xDC,(byte)0xCC,0x59,(byte)0xE7,
            0x00,0x00,0x00,0x00,0x49,0x45,0x4E,0x44,        // IEND chunk
            (byte)0xAE,0x42,0x60,(byte)0x82
        };
        return new ByteArrayInputStream(minimalWhitePng);
    }

    // -----------------------------------------------------------------------
    // Keystore helpers
    // -----------------------------------------------------------------------

    private static KeyStore loadKeyStore(SigningOptions opts)
            throws IOException, GeneralSecurityException {
        File ksFile = new File(opts.getKeystorePath());
        if (!ksFile.exists())
            throw new IOException("Keystore not found: " + opts.getKeystorePath());

        KeyStore ks = KeyStore.getInstance(opts.getKeystoreType());
        try (FileInputStream fis = new FileInputStream(ksFile)) {
            ks.load(fis, opts.getKeystorePassword().toCharArray());
        }
        logger.info("Loaded keystore: " + opts.getKeystorePath()
            + " (" + ks.size() + " entries)");
        return ks;
    }

    /**
     * Resolves the key alias to use for signing.
     *
     * If opts.getKeyAlias() is set, uses that alias directly (validates it exists).
     * Otherwise, finds the first alias that contains a private key entry.
     */
    private static String resolveAlias(KeyStore ks, SigningOptions opts)
            throws GeneralSecurityException, IOException {
        if (opts.hasAlias()) {
            if (!ks.containsAlias(opts.getKeyAlias()))
                throw new GeneralSecurityException(
                    "Alias '" + opts.getKeyAlias() + "' not found in keystore: "
                    + opts.getKeystorePath());
            return opts.getKeyAlias();
        }

        // Auto-detect: find first private key entry
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (ks.isKeyEntry(alias)) {
                logger.info("Auto-detected key alias: " + alias);
                return alias;
            }
        }
        throw new GeneralSecurityException(
            "No private key entry found in keystore: " + opts.getKeystorePath());
    }

    // -----------------------------------------------------------------------
    // Utility helpers
    // -----------------------------------------------------------------------

    /**
     * Extracts the CN value from a DN string like
     * "CN=John Smith, O=Org, C=IN".
     * Returns the full DN if CN is not found.
     */
    static String extractCN(String dn) {
        if (dn == null) return "";
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.toUpperCase().startsWith("CN=")) {
                return trimmed.substring(3).trim();
            }
        }
        return dn;
    }

    private static void ensureParentDir(File f) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IOException("Could not create output directory: " + parent);
    }
}
