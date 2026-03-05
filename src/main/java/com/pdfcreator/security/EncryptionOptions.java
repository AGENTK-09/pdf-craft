package com.pdfcreator.security;

/**
 * Configuration for a PDF security operation: encrypt, decrypt,
 * change-password, update-permissions, or inspect-security.
 *
 * Immutable once built. All operations that produce an output file read from
 * inputPath and write to outputPath — the source is never modified in place.
 *
 * ── TWO-PASSWORD MODEL (PDF SPECIFICATION) ───────────────────────────────
 *
 * Every encrypted PDF can have two distinct passwords:
 *
 *   Owner password  — grants full access. The owner can do anything,
 *                     including removing encryption or changing permissions.
 *                     Required for: encrypt, decrypt, change-password,
 *                     update-permissions. Must never be empty.
 *
 *   User password   — grants restricted access subject to AccessPermission
 *                     flags. May be empty string (""), in which case the PDF
 *                     opens without prompting. This is the standard way to
 *                     produce a "locked but opens freely" document.
 *
 * ── ALGORITHM ─────────────────────────────────────────────────────────────
 *
 * Only AES-256 is supported.
 *
 *   AES-256  — 256-bit key, PDF 2.0 standard. Requires BouncyCastle on the
 *               classpath (org.bouncycastle:bcprov-jdk18on).
 *               PDFBox key-length constant: 256.
 *
 * ── USAGE ─────────────────────────────────────────────────────────────────
 *
 *   // Encrypt — password-gate with full permissions
 *   EncryptionOptions opts = new EncryptionOptions.Builder(Operation.ENCRYPT)
 *       .inputPath("report.pdf")
 *       .outputPath("report-enc.pdf")
 *       .ownerPassword("ownerSecret")
 *       .userPassword("userSecret")      // or "" to open without prompting
 *       .permissions(PdfPermissions.allAllowed())
 *       .build();
 *
 *   // Encrypt — read-only restriction (open freely, but no copy/modify)
 *   EncryptionOptions opts = new EncryptionOptions.Builder(Operation.ENCRYPT)
 *       .inputPath("report.pdf")
 *       .outputPath("report-readonly.pdf")
 *       .ownerPassword("ownerSecret")
 *       .userPassword("")                // no prompt to open
 *       .permissions(PdfPermissions.readOnly())
 *       .build();
 *
 *   // Decrypt — remove all protection
 *   EncryptionOptions opts = new EncryptionOptions.Builder(Operation.DECRYPT)
 *       .inputPath("report-enc.pdf")
 *       .outputPath("report-plain.pdf")
 *       .ownerPassword("ownerSecret")
 *       .build();
 *
 *   // Change password — update credentials without changing permissions
 *   EncryptionOptions opts = new EncryptionOptions.Builder(Operation.CHANGE_PASSWORD)
 *       .inputPath("report-enc.pdf")
 *       .outputPath("report-newpwd.pdf")
 *       .ownerPassword("oldOwner")
 *       .newOwnerPassword("newOwner")
 *       .newUserPassword("newUser")
 *       .build();
 *
 *   // Update permissions — change flags, keep passwords
 *   EncryptionOptions opts = new EncryptionOptions.Builder(Operation.UPDATE_PERMISSIONS)
 *       .inputPath("report-enc.pdf")
 *       .outputPath("report-updated.pdf")
 *       .ownerPassword("ownerSecret")
 *       .permissions(PdfPermissions.readOnly())
 *       .build();
 *
 *   // Inspect — read and display current security status
 *   EncryptionOptions opts = new EncryptionOptions.Builder(Operation.INSPECT)
 *       .inputPath("report.pdf")
 *       .ownerPassword("ownerSecret")    // optional, omit for unprotected PDFs
 *       .build();
 */
public final class EncryptionOptions {

    // -----------------------------------------------------------------------
    // Operation enum
    // -----------------------------------------------------------------------

    /**
     * The security operation to perform.
     *
     *   ENCRYPT            — apply AES-256 encryption with passwords + permissions
     *   DECRYPT            — remove all encryption (requires owner password)
     *   CHANGE_PASSWORD    — update owner and/or user password, keep permissions
     *   UPDATE_PERMISSIONS — change permission flags, keep existing passwords
     *   INSPECT            — print current security status without modifying the file
     */
    public enum Operation {
        ENCRYPT,
        DECRYPT,
        CHANGE_PASSWORD,
        UPDATE_PERMISSIONS,
        INSPECT
    }

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final Operation     operation;
    private final String        inputPath;
    private final String        outputPath;         // null for INSPECT

    // Credentials for the current (existing) document
    private final String        ownerPassword;      // required for all ops except INSPECT on plain PDF
    private final String        userPassword;       // used when setting new encryption (ENCRYPT)

    // New credentials for CHANGE_PASSWORD
    private final String        newOwnerPassword;
    private final String        newUserPassword;

    // Permission flags (ENCRYPT and UPDATE_PERMISSIONS)
    private final PdfPermissions permissions;

    private EncryptionOptions(Builder b) {
        this.operation        = b.operation;
        this.inputPath        = b.inputPath;
        this.outputPath       = b.outputPath;
        this.ownerPassword    = b.ownerPassword;
        this.userPassword     = b.userPassword;
        this.newOwnerPassword = b.newOwnerPassword;
        this.newUserPassword  = b.newUserPassword;
        this.permissions      = b.permissions;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public Operation     getOperation()        { return operation; }
    public String        getInputPath()        { return inputPath; }
    public String        getOutputPath()       { return outputPath; }
    public String        getOwnerPassword()    { return ownerPassword; }
    public String        getUserPassword()     { return userPassword; }
    public String        getNewOwnerPassword() { return newOwnerPassword; }
    public String        getNewUserPassword()  { return newUserPassword; }
    public PdfPermissions getPermissions()     { return permissions; }

    @Override
    public String toString() {
        return String.format(
            "EncryptionOptions[op=%s, input=%s, output=%s, ownerPwd=%s, " +
            "userPwd=%s, newOwnerPwd=%s, newUserPwd=%s, perms=%s]",
            operation, inputPath, outputPath,
            ownerPassword    != null ? "***" : "null",
            userPassword     != null ? (userPassword.isEmpty() ? "\"\"" : "***") : "null",
            newOwnerPassword != null ? "***" : "null",
            newUserPassword  != null ? (newUserPassword.isEmpty() ? "\"\"" : "***") : "null",
            permissions);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {

        private final Operation operation;
        private String         inputPath        = null;
        private String         outputPath       = null;
        private String         ownerPassword    = null;
        private String         userPassword     = "";    // default: empty = open without prompting
        private String         newOwnerPassword = null;
        private String         newUserPassword  = null;
        private PdfPermissions permissions      = PdfPermissions.allAllowed();

        /**
         * @param operation the security operation to configure
         */
        public Builder(Operation operation) {
            this.operation = operation;
        }

        /** Source PDF path (required for all operations). */
        public Builder inputPath(String v)        { this.inputPath        = v; return this; }

        /**
         * Destination PDF path.
         * Required for ENCRYPT, DECRYPT, CHANGE_PASSWORD, UPDATE_PERMISSIONS.
         * Not used for INSPECT (no file is written).
         */
        public Builder outputPath(String v)       { this.outputPath       = v; return this; }

        /**
         * Current owner password of the document.
         *
         * For ENCRYPT: becomes the owner password of the new encrypted file.
         * For DECRYPT / CHANGE_PASSWORD / UPDATE_PERMISSIONS: used to open
         *   the existing encrypted document. Must be the owner password, not
         *   the user password, because these operations require owner-level access.
         * For INSPECT: optional. If provided and the document is encrypted, it
         *   is used to open the document for a full permission read. If omitted
         *   on a protected document, only basic encryption status is shown.
         */
        public Builder ownerPassword(String v)    { this.ownerPassword    = v; return this; }

        /**
         * User password to set when encrypting.
         *
         * Default: "" (empty string) — the PDF opens without prompting, but
         * permission flags are still enforced for copy/modify etc.
         *
         * Set to a non-empty value when you want the user to supply a password
         * just to open the document.
         *
         * Only used by ENCRYPT.
         */
        public Builder userPassword(String v)     { this.userPassword     = v; return this; }

        /**
         * New owner password for CHANGE_PASSWORD.
         * If null, the existing owner password (ownerPassword) is re-used.
         */
        public Builder newOwnerPassword(String v) { this.newOwnerPassword = v; return this; }

        /**
         * New user password for CHANGE_PASSWORD.
         * If null, the existing user password is re-used (PDFBox keeps it).
         */
        public Builder newUserPassword(String v)  { this.newUserPassword  = v; return this; }

        /**
         * Permission flags to apply.
         * Defaults to PdfPermissions.allAllowed() — all flags true.
         * Used by ENCRYPT and UPDATE_PERMISSIONS.
         */
        public Builder permissions(PdfPermissions v) { this.permissions   = v; return this; }

        // ── Convenience setters for individual permission flags ──────────
        // These let callers avoid constructing a PdfPermissions separately
        // when they only need to flip one or two flags.

        public Builder allowPrint(boolean v) {
            this.permissions = copyWith(permissions).allowPrint(v).build();
            return this;
        }
        public Builder allowCopy(boolean v) {
            this.permissions = copyWith(permissions).allowCopy(v).build();
            return this;
        }
        public Builder allowModify(boolean v) {
            this.permissions = copyWith(permissions).allowModify(v).build();
            return this;
        }
        public Builder allowFillForms(boolean v) {
            this.permissions = copyWith(permissions).allowFillForms(v).build();
            return this;
        }
        public Builder allowAccessibility(boolean v) {
            this.permissions = copyWith(permissions).allowAccessibility(v).build();
            return this;
        }
        public Builder allowAssemble(boolean v) {
            this.permissions = copyWith(permissions).allowAssemble(v).build();
            return this;
        }

        // ── Build ────────────────────────────────────────────────────────

        public EncryptionOptions build() {
            validateForOperation();
            return new EncryptionOptions(this);
        }

        // ── Validation ───────────────────────────────────────────────────

        private void validateForOperation() {
            if (inputPath == null || inputPath.isBlank())
                throw new IllegalStateException("EncryptionOptions: inputPath is required");

            switch (operation) {
                case ENCRYPT -> {
                    requireOutputPath();
                    requireOwnerPassword("ENCRYPT");
                    if (permissions == null)
                        throw new IllegalStateException(
                            "EncryptionOptions: permissions must not be null for ENCRYPT");
                }
                case DECRYPT -> {
                    requireOutputPath();
                    requireOwnerPassword("DECRYPT");
                }
                case CHANGE_PASSWORD -> {
                    requireOutputPath();
                    requireOwnerPassword("CHANGE_PASSWORD");
                    // At least one new password must be provided
                    if (newOwnerPassword == null && newUserPassword == null)
                        throw new IllegalStateException(
                            "EncryptionOptions: CHANGE_PASSWORD requires " +
                            "newOwnerPassword and/or newUserPassword");
                }
                case UPDATE_PERMISSIONS -> {
                    requireOutputPath();
                    requireOwnerPassword("UPDATE_PERMISSIONS");
                    if (permissions == null)
                        throw new IllegalStateException(
                            "EncryptionOptions: permissions must not be null " +
                            "for UPDATE_PERMISSIONS");
                }
                case INSPECT -> {
                    // outputPath and ownerPassword are both optional for INSPECT
                }
            }
        }

        private void requireOutputPath() {
            if (outputPath == null || outputPath.isBlank())
                throw new IllegalStateException(
                    "EncryptionOptions: outputPath is required for " + operation);
        }

        private void requireOwnerPassword(String op) {
            if (ownerPassword == null || ownerPassword.isBlank())
                throw new IllegalStateException(
                    "EncryptionOptions: ownerPassword is required for " + op +
                    " (must not be empty)");
        }

        /** Returns a new PdfPermissions.Builder pre-populated from an existing PdfPermissions. */
        private static PdfPermissions.Builder copyWith(PdfPermissions src) {
            return new PdfPermissions.Builder()
                .allowPrint(src.canPrint())
                .allowPrintFaithful(src.canPrintFaithful())
                .allowCopy(src.canCopy())
                .allowModify(src.canModify())
                .allowModifyAnnotations(src.canModifyAnnotations())
                .allowFillForms(src.canFillForms())
                .allowAccessibility(src.canAccessibility())
                .allowAssemble(src.canAssemble());
        }
    }
}
