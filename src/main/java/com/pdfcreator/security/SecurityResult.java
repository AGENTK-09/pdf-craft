package com.pdfcreator.security;

/**
 * Immutable result of a PDF security operation.
 *
 * Returned by PdfSecurityManager for ENCRYPT, DECRYPT, CHANGE_PASSWORD,
 * and UPDATE_PERMISSIONS. Also returned by inspect() with the inspected
 * document's security status populated.
 *
 * Usage:
 *
 *   SecurityResult result = securityManager.encrypt(opts);
 *   System.out.println(result.getSummary());
 *
 *   SecurityResult info = securityManager.inspect("report.pdf", null);
 *   if (info.isEncrypted()) {
 *       System.out.println("Algorithm : " + info.getAlgorithm());
 *       System.out.println("Permissions: " + info.getPermissions());
 *   }
 */
public final class SecurityResult {

    private final EncryptionOptions.Operation operation;
    private final String        inputPath;
    private final String        outputPath;      // null for INSPECT
    private final boolean       encrypted;       // true if the output / inspected doc is encrypted
    private final String        algorithm;       // e.g. "AES-256", "RC4-128", or "none"
    private final int           keyLengthBits;   // 256, 128, 40, or 0 for unencrypted
    private final PdfPermissions permissions;    // null if unencrypted and inspect not possible
    private final long          outputSizeBytes; // 0 for INSPECT

    private SecurityResult(Builder b) {
        this.operation       = b.operation;
        this.inputPath       = b.inputPath;
        this.outputPath      = b.outputPath;
        this.encrypted       = b.encrypted;
        this.algorithm       = b.algorithm;
        this.keyLengthBits   = b.keyLengthBits;
        this.permissions     = b.permissions;
        this.outputSizeBytes = b.outputSizeBytes;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public EncryptionOptions.Operation getOperation()     { return operation; }
    public String        getInputPath()                   { return inputPath; }
    public String        getOutputPath()                  { return outputPath; }
    public boolean       isEncrypted()                    { return encrypted; }
    public String        getAlgorithm()                   { return algorithm; }
    public int           getKeyLengthBits()               { return keyLengthBits; }
    public PdfPermissions getPermissions()                { return permissions; }
    public long          getOutputSizeBytes()             { return outputSizeBytes; }

    // -----------------------------------------------------------------------
    // Formatted summary
    // -----------------------------------------------------------------------

    /**
     * Returns a human-readable multi-line summary of this result.
     * Intended for CLI output.
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        String bar = "=".repeat(55);

        sb.append(bar).append("\n");
        sb.append("  Security Operation: ").append(operation).append("\n");
        sb.append(bar).append("\n");

        sb.append(String.format("  Input          : %s%n", inputPath));
        if (outputPath != null)
            sb.append(String.format("  Output         : %s  (%,d bytes)%n",
                outputPath, outputSizeBytes));

        sb.append(String.format("  Encrypted      : %s%n", encrypted ? "YES" : "NO"));
        if (encrypted) {
            sb.append(String.format("  Algorithm      : %s (%d-bit key)%n",
                algorithm, keyLengthBits));
        }

        if (permissions != null) {
            sb.append("\n  Permissions\n");
            sb.append(String.format("    %-28s %s%n", "Print:",              yn(permissions.canPrint())));
            sb.append(String.format("    %-28s %s%n", "Print (faithful):",   yn(permissions.canPrintFaithful())));
            sb.append(String.format("    %-28s %s%n", "Copy content:",       yn(permissions.canCopy())));
            sb.append(String.format("    %-28s %s%n", "Modify document:",    yn(permissions.canModify())));
            sb.append(String.format("    %-28s %s%n", "Modify annotations:", yn(permissions.canModifyAnnotations())));
            sb.append(String.format("    %-28s %s%n", "Fill forms:",         yn(permissions.canFillForms())));
            sb.append(String.format("    %-28s %s%n", "Accessibility:",      yn(permissions.canAccessibility())));
            sb.append(String.format("    %-28s %s%n", "Assemble pages:",     yn(permissions.canAssemble())));
        }

        sb.append(bar).append("\n");
        return sb.toString();
    }

    private static String yn(boolean v) { return v ? "allowed" : "DENIED"; }

    @Override
    public String toString() {
        return String.format("SecurityResult[op=%s, encrypted=%b, algo=%s, input=%s, output=%s]",
            operation, encrypted, algorithm, inputPath, outputPath);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private EncryptionOptions.Operation operation;
        private String         inputPath       = "";
        private String         outputPath      = null;
        private boolean        encrypted       = false;
        private String         algorithm       = "none";
        private int            keyLengthBits   = 0;
        private PdfPermissions permissions     = null;
        private long           outputSizeBytes = 0;

        public Builder operation(EncryptionOptions.Operation v) { this.operation       = v; return this; }
        public Builder inputPath(String v)                      { this.inputPath        = v; return this; }
        public Builder outputPath(String v)                     { this.outputPath       = v; return this; }
        public Builder encrypted(boolean v)                     { this.encrypted        = v; return this; }
        public Builder algorithm(String v)                      { this.algorithm        = v; return this; }
        public Builder keyLengthBits(int v)                     { this.keyLengthBits    = v; return this; }
        public Builder permissions(PdfPermissions v)            { this.permissions      = v; return this; }
        public Builder outputSizeBytes(long v)                  { this.outputSizeBytes  = v; return this; }

        public SecurityResult build() {
            if (operation == null)
                throw new IllegalStateException("SecurityResult: operation is required");
            return new SecurityResult(this);
        }
    }
}
