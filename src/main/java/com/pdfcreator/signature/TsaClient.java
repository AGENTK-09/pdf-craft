package com.pdfcreator.signature;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.tsp.TSPException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.logging.Logger;

/**
 * RFC 3161 Trusted Timestamp Authority (TSA) client.
 *
 * Makes an HTTP POST to a TSA endpoint with a timestamp request containing
 * the SHA-256 digest of the CMS SignedData bytes. Returns a TimeStampToken
 * which is then embedded as an unsigned attribute in the SignerInfo.
 *
 * ── RFC 3161 PROTOCOL ─────────────────────────────────────────────────────
 *
 *   1. Hash the SignedData bytes using SHA-256
 *   2. Build a TimeStampRequest containing:
 *      - The hash algorithm OID (SHA-256)
 *      - The hash bytes
 *      - A random nonce (prevents replay attacks)
 *      - certReq=true (ask TSA to include its certificate)
 *   3. HTTP POST to the TSA URL
 *      Content-Type: application/timestamp-query
 *   4. Parse the TimeStampResponse
 *   5. Extract and validate the TimeStampToken
 *   6. Return the token DER bytes for embedding
 *
 * ── KNOWN FREE TSA ENDPOINTS ─────────────────────────────────────────────
 *
 *   DigiCert:   http://timestamp.digicert.com
 *   GlobalSign: http://timestamp.globalsign.com/scripts/timstamp.dll
 *   Sectigo:    http://timestamp.sectigo.com
 *   Freetsa:    https://freetsa.org/tsr  (free, no CA required)
 *
 * ── THREAD SAFETY ─────────────────────────────────────────────────────────
 *
 * Stateless — a single instance can be shared across threads.
 * Each getTimestamp() call opens its own HTTP connection.
 */
public class TsaClient {

    private static final Logger logger = Logger.getLogger(TsaClient.class.getName());

    /** SHA-256 OID for the timestamp request hash algorithm. */
    private static final ASN1ObjectIdentifier SHA256_OID =
        NISTObjectIdentifiers.id_sha256;

    /** Connection and read timeout in milliseconds (10 seconds each). */
    private static final int TIMEOUT_MS = 10_000;

    private final String tsaUrl;

    public TsaClient(String tsaUrl) {
        if (tsaUrl == null || tsaUrl.isBlank())
            throw new IllegalArgumentException("TsaClient: tsaUrl must not be blank");
        this.tsaUrl = tsaUrl;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Obtains a RFC 3161 timestamp token for the given data bytes.
     *
     * The data bytes should be the DER-encoded CMS SignedData produced by the
     * signing step. The TSA hashes these bytes, signs the hash with its own
     * private key, and returns a TimeStampToken.
     *
     * @param signedDataBytes the bytes to timestamp (CMS SignedData DER)
     * @return DER-encoded TimeStampToken bytes ready to embed in SignerInfo
     * @throws IOException if the TSA is unreachable or returns an error
     */
    public byte[] getTimestampToken(byte[] signedDataBytes) throws IOException {

        // Step 1: Hash the signed bytes with SHA-256
        byte[] hash = sha256(signedDataBytes);

        // Step 2: Build the timestamp request
        TimeStampRequestGenerator gen = new TimeStampRequestGenerator();
        gen.setCertReq(true);   // ask TSA to include its certificate in the response

        BigInteger nonce = new BigInteger(64, new SecureRandom());
        TimeStampRequest request = gen.generate(SHA256_OID, hash, nonce);
        byte[] requestBytes = request.getEncoded();

        logger.info("Sending timestamp request to: " + tsaUrl
            + " (nonce=" + nonce.toString(16) + ")");

        // Step 3: POST to TSA
        byte[] responseBytes = httpPost(requestBytes);

        // Step 4: Parse response — TSPException is a checked BC exception,
        // wrap it as IOException to keep our public API clean.
        try {
            TimeStampResponse response = new TimeStampResponse(responseBytes);
            response.validate(request);   // TSPException if nonce/OID mismatch

            int status = response.getStatus();
            if (status != 0 && status != 1) {
                throw new IOException(
                    "TSA returned error status " + status +
                    ": " + response.getStatusString());
            }

            // Step 5: Extract and validate token
            TimeStampToken token = response.getTimeStampToken();
            if (token == null)
                throw new IOException("TSA response contained no TimeStampToken");

            // Validate the token's signature using the TSA's own certificate
            validateToken(token);

            logger.info("Timestamp obtained from TSA: "
                + token.getTimeStampInfo().getGenTime());

            // Step 6: Return the DER-encoded token
            return token.getEncoded();

        } catch (TSPException e) {
            throw new IOException("TSA response validation failed: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Sends an RFC 3161 HTTP timestamp query and returns the raw response bytes.
     *
     * Content-Type must be "application/timestamp-query" per RFC 3161 §3.4.
     * The response Content-Type will be "application/timestamp-reply".
     */
    private byte[] httpPost(byte[] requestBytes) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(tsaUrl).openConnection();
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/timestamp-query");
        conn.setRequestProperty("Content-Length", String.valueOf(requestBytes.length));
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBytes);
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException(
                "TSA HTTP error " + responseCode + " from " + tsaUrl);
        }

        try (InputStream is = conn.getInputStream()) {
            return is.readAllBytes();
        }
    }

    /**
     * Validates the TimeStampToken's CMS signature using the TSA certificate
     * embedded in the token itself (certReq=true was set in the request).
     *
     * This ensures the timestamp was genuinely issued by the TSA and hasn't
     * been forged or corrupted in transit.
     */
    private static void validateToken(TimeStampToken token) throws IOException {
        try {
            // Get the TSA's signing certificate from the token
            var matches = token.getCertificates()
                .getMatches(token.getSID());

            if (matches.isEmpty())
                throw new IOException("TSA token contains no signer certificate");

            X509CertificateHolder tsaCert =
                (X509CertificateHolder) matches.iterator().next();

            // Build a verifier and check the token's signature
            SignerInformationVerifier verifier =
                new JcaSimpleSignerInfoVerifierBuilder()
                    .setProvider("BC")
                    .build(tsaCert);

            token.validate(verifier);
            logger.fine("TSA token signature validated successfully");

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("TSA token validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Computes SHA-256 digest of the given bytes.
     */
    private static byte[] sha256(byte[] data) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (Exception e) {
            throw new IOException("SHA-256 digest failed: " + e.getMessage(), e);
        }
    }
}
