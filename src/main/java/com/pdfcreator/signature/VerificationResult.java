package com.pdfcreator.signature;

import java.util.Date;
import java.util.List;

/**
 * Immutable result of verifying one signature in a PDF.
 *
 * A PDF may contain multiple signatures (e.g. approval + counter-signature).
 * PdfSignatureVerifier.verify() returns a List<VerificationResult>, one per
 * PDSignature found in the document.
 *
 * ── FIELDS EXPLAINED ──────────────────────────────────────────────────────
 *
 *   signatureValid    — true if the CMS digest matches the ByteRange bytes.
 *                       If false, the document was modified after signing.
 *                       This is the most important field.
 *
 *   coverageComplete  — true if the ByteRange covers the entire file.
 *                       If false, content was appended after the signature
 *                       was applied (could be a legitimate second signature
 *                       or an unauthorised addition).
 *
 *   certTrusted       — true if the certificate chain validates to one of
 *                       the trust anchors supplied to verify() (or the JVM
 *                       default cacerts if no truststore was given).
 *                       false for self-signed certs unless explicitly trusted.
 *
 *   certExpiredAtSigning — true if the signing certificate was already
 *                          expired at the time recorded in signedAt.
 *                          Always false when a valid TSA timestamp is present
 *                          and the cert was valid at the TSA time.
 *
 *   timestamped       — true if a RFC 3161 timestamp is embedded, proving
 *                       the signing time was attested by a trusted third party.
 *
 * Usage:
 *
 *   List<VerificationResult> results = verifier.verify("signed.pdf", null);
 *   for (VerificationResult r : results) {
 *       System.out.println(r.getSummary());
 *   }
 */
public final class VerificationResult {

    private final int     signatureIndex;     // 0-based position in the document
    private final String  signerName;         // CN from the signing certificate
    private final String  issuerName;         // issuer DN
    private final String  serialNumber;       // certificate serial (hex)
    private final Date    signedAt;           // signing time from the signature dict
    private final Date    certNotBefore;
    private final Date    certNotAfter;
    private final boolean signatureValid;     // CMS digest matches ByteRange bytes
    private final boolean coverageComplete;   // ByteRange covers the entire file
    private final boolean certTrusted;        // chain validates to a trust anchor
    private final boolean certExpiredAtSigning;
    private final boolean timestamped;        // RFC 3161 TSA counter-signature present
    private final String  reason;             // from PDSignature dictionary
    private final String  location;           // from PDSignature dictionary
    private final String  contactInfo;        // from PDSignature dictionary
    private final String  failureReason;      // human-readable if signatureValid=false

    private VerificationResult(Builder b) {
        this.signatureIndex      = b.signatureIndex;
        this.signerName          = b.signerName;
        this.issuerName          = b.issuerName;
        this.serialNumber        = b.serialNumber;
        this.signedAt            = b.signedAt;
        this.certNotBefore       = b.certNotBefore;
        this.certNotAfter        = b.certNotAfter;
        this.signatureValid      = b.signatureValid;
        this.coverageComplete    = b.coverageComplete;
        this.certTrusted         = b.certTrusted;
        this.certExpiredAtSigning= b.certExpiredAtSigning;
        this.timestamped         = b.timestamped;
        this.reason              = b.reason;
        this.location            = b.location;
        this.contactInfo         = b.contactInfo;
        this.failureReason       = b.failureReason;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public int     getSignatureIndex()       { return signatureIndex; }
    public String  getSignerName()           { return signerName; }
    public String  getIssuerName()           { return issuerName; }
    public String  getSerialNumber()         { return serialNumber; }
    public Date    getSignedAt()             { return signedAt; }
    public Date    getCertNotBefore()        { return certNotBefore; }
    public Date    getCertNotAfter()         { return certNotAfter; }
    public boolean isSignatureValid()        { return signatureValid; }
    public boolean isCoverageComplete()      { return coverageComplete; }
    public boolean isCertTrusted()           { return certTrusted; }
    public boolean isCertExpiredAtSigning()  { return certExpiredAtSigning; }
    public boolean isTimestamped()           { return timestamped; }
    public String  getReason()               { return reason; }
    public String  getLocation()             { return location; }
    public String  getContactInfo()          { return contactInfo; }
    public String  getFailureReason()        { return failureReason; }

    /**
     * Convenience: true only when all critical checks pass.
     * signatureValid + coverageComplete are both required.
     * certTrusted may be false for self-signed certs.
     */
    public boolean isFullyValid() {
        return signatureValid && coverageComplete && !certExpiredAtSigning;
    }

    // -----------------------------------------------------------------------
    // Formatted summary
    // -----------------------------------------------------------------------

    public String getSummary() {
        String bar = "-".repeat(55);
        StringBuilder sb = new StringBuilder();
        sb.append(bar).append("\n");
        sb.append(String.format("  Signature #%d%n", signatureIndex + 1));
        sb.append(bar).append("\n");
        sb.append(String.format("  %-26s %s%n", "Integrity (digest):",
            signatureValid ? "✓ VALID" : "✗ INVALID — document was modified"));
        sb.append(String.format("  %-26s %s%n", "Full coverage:",
            coverageComplete ? "✓ Covers entire file"
                             : "⚠ Partial — content added after signing"));
        sb.append(String.format("  %-26s %s%n", "Certificate trusted:",
            certTrusted ? "✓ Trusted" : "⚠ Not trusted (self-signed or unknown CA)"));
        sb.append(String.format("  %-26s %s%n", "Cert expired at signing:",
            certExpiredAtSigning ? "✗ YES" : "✓ NO"));
        sb.append(String.format("  %-26s %s%n", "TSA Timestamp:", timestamped ? "✓ YES" : "NO"));
        sb.append("\n");
        sb.append(String.format("  %-26s %s%n", "Signer:",    nvl(signerName)));
        sb.append(String.format("  %-26s %s%n", "Issuer:",    nvl(issuerName)));
        sb.append(String.format("  %-26s %s%n", "Serial:",    nvl(serialNumber)));
        sb.append(String.format("  %-26s %s%n", "Signed at:", nvl(signedAt)));
        sb.append(String.format("  %-26s %s  →  %s%n", "Cert valid:", nvl(certNotBefore), nvl(certNotAfter)));
        if (reason      != null) sb.append(String.format("  %-26s %s%n", "Reason:",   reason));
        if (location    != null) sb.append(String.format("  %-26s %s%n", "Location:", location));
        if (contactInfo != null) sb.append(String.format("  %-26s %s%n", "Contact:",  contactInfo));
        if (failureReason != null)
            sb.append(String.format("%n  Failure detail: %s%n", failureReason));
        return sb.toString();
    }

    private static String nvl(Object v) { return v != null ? v.toString() : "(not set)"; }

    /**
     * Prints a formatted summary of a list of verification results.
     */
    public static String summariseAll(List<VerificationResult> results, String pdfPath) {
        String bar = "=".repeat(55);
        StringBuilder sb = new StringBuilder();
        sb.append(bar).append("\n");
        sb.append("  Verification Results\n");
        sb.append(String.format("  File: %s%n", pdfPath));
        sb.append(String.format("  Signatures found: %d%n", results.size()));
        sb.append(bar).append("\n");
        if (results.isEmpty()) {
            sb.append("  No signatures found in this document.\n");
        } else {
            for (VerificationResult r : results) sb.append(r.getSummary());
        }
        sb.append(bar).append("\n");

        long valid   = results.stream().filter(VerificationResult::isSignatureValid).count();
        long invalid = results.size() - valid;
        sb.append(String.format("  Summary: %d valid, %d invalid%n", valid, invalid));
        sb.append(bar).append("\n");
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("VerificationResult[#%d, signer=%s, valid=%b, trusted=%b]",
            signatureIndex, signerName, signatureValid, certTrusted);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private int     signatureIndex       = 0;
        private String  signerName           = null;
        private String  issuerName           = null;
        private String  serialNumber         = null;
        private Date    signedAt             = null;
        private Date    certNotBefore        = null;
        private Date    certNotAfter         = null;
        private boolean signatureValid       = false;
        private boolean coverageComplete     = false;
        private boolean certTrusted          = false;
        private boolean certExpiredAtSigning = false;
        private boolean timestamped          = false;
        private String  reason               = null;
        private String  location             = null;
        private String  contactInfo          = null;
        private String  failureReason        = null;

        public Builder signatureIndex(int v)           { this.signatureIndex       = v; return this; }
        public Builder signerName(String v)            { this.signerName           = v; return this; }
        public Builder issuerName(String v)            { this.issuerName           = v; return this; }
        public Builder serialNumber(String v)          { this.serialNumber         = v; return this; }
        public Builder signedAt(Date v)                { this.signedAt             = v; return this; }
        public Builder certNotBefore(Date v)           { this.certNotBefore        = v; return this; }
        public Builder certNotAfter(Date v)            { this.certNotAfter         = v; return this; }
        public Builder signatureValid(boolean v)       { this.signatureValid       = v; return this; }
        public Builder coverageComplete(boolean v)     { this.coverageComplete     = v; return this; }
        public Builder certTrusted(boolean v)          { this.certTrusted          = v; return this; }
        public Builder certExpiredAtSigning(boolean v) { this.certExpiredAtSigning = v; return this; }
        public Builder timestamped(boolean v)          { this.timestamped          = v; return this; }
        public Builder reason(String v)                { this.reason               = v; return this; }
        public Builder location(String v)              { this.location             = v; return this; }
        public Builder contactInfo(String v)           { this.contactInfo          = v; return this; }
        public Builder failureReason(String v)         { this.failureReason        = v; return this; }

        public VerificationResult build() { return new VerificationResult(this); }
    }
}
