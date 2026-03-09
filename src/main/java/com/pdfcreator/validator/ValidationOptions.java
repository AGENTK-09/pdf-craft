package com.pdfcreator.validator;

/**
 * Configuration for a PDF/A validation run.
 *
 * Usage:
 *
 *   ValidationOptions opts = new ValidationOptions.Builder()
 *       .inputPath("statement.pdf")
 *       .standard(PdfAStandard.PDF_A1B)
 *       .maxErrors(50)
 *       .build();
 *
 *   PdfValidationResult result = new PdfValidator().validate(opts);
 */
public final class ValidationOptions {

    private final String       inputPath;
    private final PdfAStandard standard;
    private final int          maxErrors;   // 0 = unlimited
    private final String       password;    // null = no password

    private ValidationOptions(Builder b) {
        this.inputPath = b.inputPath;
        this.standard  = b.standard;
        this.maxErrors = b.maxErrors;
        this.password  = b.password;
    }

    public String       getInputPath() { return inputPath; }
    public PdfAStandard getStandard()  { return standard; }
    public int          getMaxErrors() { return maxErrors; }
    public String       getPassword()  { return password; }

    @Override
    public String toString() {
        return String.format("ValidationOptions[input=%s, standard=%s, maxErrors=%s]",
            inputPath, standard, maxErrors == 0 ? "unlimited" : maxErrors);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private String       inputPath = null;
        private PdfAStandard standard  = PdfAStandard.PDF_A1B;
        private int          maxErrors = 0;
        private String       password  = null;

        /** Path to the PDF file to validate (required). */
        public Builder inputPath(String v)   { this.inputPath = v; return this; }

        /** PDF/A standard to validate against. Default: PDF_A1B. */
        public Builder standard(PdfAStandard v) { this.standard = v; return this; }

        /**
         * Stop collecting errors after this many. 0 = collect all (default).
         * Useful for large documents with many errors — avoids flooding output.
         */
        public Builder maxErrors(int v)      { this.maxErrors = v; return this; }

        /** Password for encrypted PDFs. Note: encrypted PDFs always fail PDF/A validation. */
        public Builder password(String v)    { this.password  = v; return this; }

        public ValidationOptions build() {
            if (inputPath == null || inputPath.isBlank())
                throw new IllegalStateException("ValidationOptions: inputPath is required");
            return new ValidationOptions(this);
        }
    }
}
