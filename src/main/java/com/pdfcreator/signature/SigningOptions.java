package com.pdfcreator.signature;

/**
 * Configuration for a PDF signing operation.
 *
 * Immutable once built. Used by PdfSigner.sign().
 *
 * NOTE: This class is named SigningOptions (not SignatureOptions) to avoid a
 * name collision with PDFBox's own
 * org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions class.
 *
 * ── KEYSTORE MODEL ────────────────────────────────────────────────────────
 *
 * Signing requires a PKCS#12 (.p12) or JKS keystore containing:
 *   - A private key
 *   - The signing certificate (leaf)
 *   - Optionally: the full certificate chain (intermediates + root CA)
 *
 * When the full chain is included, recipients can verify trust without
 * fetching intermediate certificates — critical for offline verification
 * and for customers opening statements in Adobe Acrobat.
 *
 * Generate a test keystore (self-signed):
 *   keytool -genkeypair \
 *     -storepass changeit -storetype PKCS12 -alias pdfcreator \
 *     -validity 365 -keyalg RSA -keysize 2048 \
 *     -dname "CN=MyOrg Statements, O=MyOrg, L=Chennai, ST=TN, C=IN" \
 *     -keystore test-keystore.p12
 *
 * ── TSA (TRUSTED TIMESTAMP) ───────────────────────────────────────────────
 *
 * Without a timestamp, a signature can only be verified while the signing
 * certificate is still valid. For customer statements that must be verifiable
 * years later, a Trusted Timestamp Authority (TSA) embeds a counter-signature
 * from an independent time server, proving the document was signed while the
 * cert was valid — regardless of when the cert later expires.
 *
 * Free TSA endpoints (for production use):
 *   DigiCert:   http://timestamp.digicert.com
 *   GlobalSign: http://timestamp.globalsign.com/scripts/timstamp.dll
 *   Sectigo:    http://timestamp.sectigo.com
 *
 * ── VISIBLE SIGNATURE ─────────────────────────────────────────────────────
 *
 * Set visible(true) to render a signature box on a specific page.
 * The box shows the signer name, date, and reason. Coordinates are in
 * PDF points (72 points = 1 inch), measured from the bottom-left corner.
 *
 * ── USAGE ─────────────────────────────────────────────────────────────────
 *
 *   // Invisible signature — server-side batch signing
 *   SigningOptions opts = new SigningOptions.Builder()
 *       .inputPath("statement.pdf")
 *       .outputPath("statement-signed.pdf")
 *       .keystorePath("bank.p12")
 *       .keystorePassword("secret")
 *       .reason("Monthly Account Statement - March 2026")
 *       .location("Mumbai, India")
 *       .contactInfo("statements@mybank.com")
 *       .tsaUrl("http://timestamp.digicert.com")
 *       .build();
 *
 *   // Visible signature on page 1, bottom-left
 *   SigningOptions opts = new SigningOptions.Builder()
 *       .inputPath("contract.pdf")
 *       .outputPath("contract-signed.pdf")
 *       .keystorePath("signing.p12")
 *       .keystorePassword("secret")
 *       .visible(true)
 *       .signaturePage(1)
 *       .signatureRect(50, 50, 200, 60)
 *       .build();
 */
public final class SigningOptions {

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final String  inputPath;
    private final String  outputPath;

    // Keystore
    private final String  keystorePath;
    private final String  keystorePassword;
    private final String  keystoreType;      // PKCS12 or JKS
    private final String  keyAlias;          // null = auto-detect first alias

    // Signature metadata (all optional but recommended)
    private final String  reason;
    private final String  location;
    private final String  contactInfo;
    private final String  signerName;        // null = read from certificate CN

    // TSA
    private final String  tsaUrl;            // null = no timestamp

    // Visible signature
    private final boolean visible;
    private final int     signaturePage;     // 1-based; -1 = last page
    private final float   sigX;             // bottom-left x in points
    private final float   sigY;             // bottom-left y in points
    private final float   sigWidth;
    private final float   sigHeight;

    private SigningOptions(Builder b) {
        this.inputPath       = b.inputPath;
        this.outputPath      = b.outputPath;
        this.keystorePath    = b.keystorePath;
        this.keystorePassword= b.keystorePassword;
        this.keystoreType    = b.keystoreType;
        this.keyAlias        = b.keyAlias;
        this.reason          = b.reason;
        this.location        = b.location;
        this.contactInfo     = b.contactInfo;
        this.signerName      = b.signerName;
        this.tsaUrl          = b.tsaUrl;
        this.visible         = b.visible;
        this.signaturePage   = b.signaturePage;
        this.sigX            = b.sigX;
        this.sigY            = b.sigY;
        this.sigWidth        = b.sigWidth;
        this.sigHeight       = b.sigHeight;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public String  getInputPath()        { return inputPath; }
    public String  getOutputPath()       { return outputPath; }
    public String  getKeystorePath()     { return keystorePath; }
    public String  getKeystorePassword() { return keystorePassword; }
    public String  getKeystoreType()     { return keystoreType; }
    public String  getKeyAlias()         { return keyAlias; }
    public String  getReason()           { return reason; }
    public String  getLocation()         { return location; }
    public String  getContactInfo()      { return contactInfo; }
    public String  getSignerName()       { return signerName; }
    public String  getTsaUrl()           { return tsaUrl; }
    public boolean isVisible()           { return visible; }
    public int     getSignaturePage()    { return signaturePage; }
    public float   getSigX()             { return sigX; }
    public float   getSigY()             { return sigY; }
    public float   getSigWidth()         { return sigWidth; }
    public float   getSigHeight()        { return sigHeight; }

    public boolean hasTsa()    { return tsaUrl != null && !tsaUrl.isBlank(); }
    public boolean hasAlias()  { return keyAlias != null && !keyAlias.isBlank(); }

    @Override
    public String toString() {
        return String.format(
            "SigningOptions[input=%s, output=%s, keystore=%s, alias=%s, " +
            "reason=%s, tsa=%s, visible=%b]",
            inputPath, outputPath, keystorePath,
            keyAlias != null ? keyAlias : "auto",
            reason, tsaUrl != null ? tsaUrl : "none", visible);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {

        private String  inputPath        = null;
        private String  outputPath       = null;
        private String  keystorePath     = null;
        private String  keystorePassword = null;
        private String  keystoreType     = "PKCS12";
        private String  keyAlias         = null;
        private String  reason           = null;
        private String  location         = null;
        private String  contactInfo      = null;
        private String  signerName       = null;
        private String  tsaUrl           = null;
        private boolean visible          = false;
        private int     signaturePage    = -1;   // -1 = last page
        private float   sigX             = 50f;
        private float   sigY             = 50f;
        private float   sigWidth         = 200f;
        private float   sigHeight        = 60f;

        /** Source PDF path (required). */
        public Builder inputPath(String v)        { this.inputPath        = v; return this; }

        /** Output PDF path (required). */
        public Builder outputPath(String v)       { this.outputPath       = v; return this; }

        /** Path to the PKCS12 or JKS keystore file (required). */
        public Builder keystorePath(String v)     { this.keystorePath     = v; return this; }

        /** Password to unlock the keystore (required). */
        public Builder keystorePassword(String v) { this.keystorePassword = v; return this; }

        /** Keystore type: "PKCS12" (default) or "JKS". */
        public Builder keystoreType(String v)     { this.keystoreType     = v; return this; }

        /**
         * Alias of the key entry to use for signing.
         * Null (default) = auto-detect: uses the first alias found in the keystore.
         */
        public Builder keyAlias(String v)         { this.keyAlias         = v; return this; }

        /** Reason for signing — appears in the signature panel of PDF viewers. */
        public Builder reason(String v)           { this.reason           = v; return this; }

        /** Signing location (city/country) — appears in the signature panel. */
        public Builder location(String v)         { this.location         = v; return this; }

        /** Signer's contact info (email/URL) — appears in the signature panel. */
        public Builder contactInfo(String v)      { this.contactInfo      = v; return this; }

        /**
         * Override the display name for the signer.
         * Default: the CN field from the signing certificate.
         */
        public Builder signerName(String v)       { this.signerName       = v; return this; }

        /**
         * URL of a RFC 3161 Trusted Timestamp Authority.
         * Null (default) = no timestamp embedded.
         *
         * Recommended for production to ensure signatures remain valid
         * after the signing certificate expires.
         *
         * Example: "http://timestamp.digicert.com"
         */
        public Builder tsaUrl(String v)           { this.tsaUrl           = v; return this; }

        /**
         * When true, renders a visible signature box on the page.
         * Default: false (invisible signature — metadata only).
         */
        public Builder visible(boolean v)         { this.visible          = v; return this; }

        /**
         * Page number (1-based) on which to render the visible signature box.
         * Default: -1 = last page of the document.
         * Only relevant when visible=true.
         */
        public Builder signaturePage(int v)       { this.signaturePage    = v; return this; }

        /**
         * Position and size of the visible signature box in PDF points.
         * Origin is bottom-left corner of the page.
         *   x, y     — bottom-left corner of the box
         *   width    — box width
         *   height   — box height
         * Default: 50, 50, 200, 60
         */
        public Builder signatureRect(float x, float y, float w, float h) {
            this.sigX = x; this.sigY = y;
            this.sigWidth = w; this.sigHeight = h;
            return this;
        }

        /**
         * Builds a fully-validated SigningOptions for single-file signing.
         * Requires inputPath, outputPath, keystorePath, and keystorePassword.
         */
        public SigningOptions build() {
            if (inputPath == null || inputPath.isBlank())
                throw new IllegalStateException("SigningOptions: inputPath is required");
            if (outputPath == null || outputPath.isBlank())
                throw new IllegalStateException("SigningOptions: outputPath is required");
            if (keystorePath == null || keystorePath.isBlank())
                throw new IllegalStateException("SigningOptions: keystorePath is required");
            if (keystorePassword == null)
                throw new IllegalStateException("SigningOptions: keystorePassword is required");
            return new SigningOptions(this);
        }

        /**
         * Builds a SigningOptions template for batch signing.
         *
         * inputPath and outputPath are intentionally omitted — BatchRunner sets
         * them per-job in buildJobSigningOptions(). All other required fields
         * (keystorePath, keystorePassword) are still validated.
         */
        public SigningOptions buildTemplate() {
            if (keystorePath == null || keystorePath.isBlank())
                throw new IllegalStateException("SigningOptions: keystorePath is required");
            if (keystorePassword == null)
                throw new IllegalStateException("SigningOptions: keystorePassword is required");
            return new SigningOptions(this);
        }
    }
}
