package com.pdfcreator.signature;

import java.util.Date;

/**
 * Immutable result of a PDF signing operation.
 *
 * Returned by PdfSigner.sign(). Contains the identity details extracted
 * from the signing certificate and the outcome of the operation.
 *
 * Usage:
 *
 *   SignatureResult result = signer.sign(opts);
 *   System.out.println(result.getSummary());
 */
public final class SignatureResult {

    private final String  inputPath;
    private final String  outputPath;
    private final long    outputSizeBytes;
    private final String  signerName;       // CN from the signing certificate
    private final String  issuerName;       // DN of the issuing CA
    private final String  serialNumber;     // certificate serial (hex)
    private final Date    signedAt;         // signing time (local clock or TSA)
    private final Date    certNotBefore;    // certificate validity start
    private final Date    certNotAfter;     // certificate validity end
    private final boolean timestamped;      // true if a TSA timestamp was embedded
    private final String  tsaUrl;           // TSA URL used, or null
    private final boolean visible;          // true if a visible sig box was rendered
    private final int     signaturePage;    // page the visible box was placed on (0 = invisible)
    private final String  algorithm;        // e.g. "SHA256withRSA"

    private SignatureResult(Builder b) {
        this.inputPath       = b.inputPath;
        this.outputPath      = b.outputPath;
        this.outputSizeBytes = b.outputSizeBytes;
        this.signerName      = b.signerName;
        this.issuerName      = b.issuerName;
        this.serialNumber    = b.serialNumber;
        this.signedAt        = b.signedAt;
        this.certNotBefore   = b.certNotBefore;
        this.certNotAfter    = b.certNotAfter;
        this.timestamped     = b.timestamped;
        this.tsaUrl          = b.tsaUrl;
        this.visible         = b.visible;
        this.signaturePage   = b.signaturePage;
        this.algorithm       = b.algorithm;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public String  getInputPath()       { return inputPath; }
    public String  getOutputPath()      { return outputPath; }
    public long    getOutputSizeBytes() { return outputSizeBytes; }
    public String  getSignerName()      { return signerName; }
    public String  getIssuerName()      { return issuerName; }
    public String  getSerialNumber()    { return serialNumber; }
    public Date    getSignedAt()        { return signedAt; }
    public Date    getCertNotBefore()   { return certNotBefore; }
    public Date    getCertNotAfter()    { return certNotAfter; }
    public boolean isTimestamped()      { return timestamped; }
    public String  getTsaUrl()          { return tsaUrl; }
    public boolean isVisible()          { return visible; }
    public int     getSignaturePage()   { return signaturePage; }
    public String  getAlgorithm()       { return algorithm; }

    // -----------------------------------------------------------------------
    // Formatted summary
    // -----------------------------------------------------------------------

    public String getSummary() {
        String bar = "=".repeat(55);
        StringBuilder sb = new StringBuilder();
        sb.append(bar).append("\n");
        sb.append("  Signing Complete\n");
        sb.append(bar).append("\n");
        sb.append(String.format("  Input          : %s%n", inputPath));
        sb.append(String.format("  Output         : %s  (%,d bytes)%n", outputPath, outputSizeBytes));
        sb.append(String.format("  Signer         : %s%n", signerName));
        sb.append(String.format("  Issuer         : %s%n", issuerName));
        sb.append(String.format("  Serial         : %s%n", serialNumber));
        sb.append(String.format("  Algorithm      : %s%n", algorithm));
        sb.append(String.format("  Signed at      : %s%n", signedAt));
        sb.append(String.format("  Cert valid     : %s  →  %s%n", certNotBefore, certNotAfter));
        sb.append(String.format("  Timestamped    : %s%n",
            timestamped ? "YES (" + tsaUrl + ")" : "NO"));
        sb.append(String.format("  Visible sig    : %s%n",
            visible ? "YES (page " + signaturePage + ")" : "NO (invisible)"));
        sb.append(bar).append("\n");
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("SignatureResult[signer=%s, algo=%s, output=%s, ts=%b]",
            signerName, algorithm, outputPath, timestamped);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private String  inputPath       = "";
        private String  outputPath      = "";
        private long    outputSizeBytes = 0;
        private String  signerName      = "";
        private String  issuerName      = "";
        private String  serialNumber    = "";
        private Date    signedAt        = new Date();
        private Date    certNotBefore   = null;
        private Date    certNotAfter    = null;
        private boolean timestamped     = false;
        private String  tsaUrl          = null;
        private boolean visible         = false;
        private int     signaturePage   = 0;
        private String  algorithm       = "SHA256withRSA";

        public Builder inputPath(String v)       { this.inputPath       = v; return this; }
        public Builder outputPath(String v)      { this.outputPath      = v; return this; }
        public Builder outputSizeBytes(long v)   { this.outputSizeBytes = v; return this; }
        public Builder signerName(String v)      { this.signerName      = v; return this; }
        public Builder issuerName(String v)      { this.issuerName      = v; return this; }
        public Builder serialNumber(String v)    { this.serialNumber    = v; return this; }
        public Builder signedAt(Date v)          { this.signedAt        = v; return this; }
        public Builder certNotBefore(Date v)     { this.certNotBefore   = v; return this; }
        public Builder certNotAfter(Date v)      { this.certNotAfter    = v; return this; }
        public Builder timestamped(boolean v)    { this.timestamped     = v; return this; }
        public Builder tsaUrl(String v)          { this.tsaUrl          = v; return this; }
        public Builder visible(boolean v)        { this.visible         = v; return this; }
        public Builder signaturePage(int v)      { this.signaturePage   = v; return this; }
        public Builder algorithm(String v)       { this.algorithm       = v; return this; }

        public SignatureResult build() { return new SignatureResult(this); }
    }
}
