package com.pdfcreator.validator;

/**
 * PDF/A conformance standards supported by PdfValidator.
 *
 * PDFBox Preflight only supports PDF/A-1 variants. For PDF/A-2 or PDF/A-3
 * validation, an external tool such as VeraPDF is required.
 */
public enum PdfAStandard {

    /**
     * PDF/A-1b — Basic conformance (ISO 19005-1 level B).
     *
     * Requires: font embedding, XMP metadata with pdfaid schema, output intent
     * colour profile, no encryption, no transparency, no JavaScript.
     * Does NOT require structural tagging or reading order.
     *
     * This is the standard to target for bank statements and customer documents.
     */
    PDF_A1B("PDF/A-1b (Basic)", "B"),

    /**
     * PDF/A-1a — Accessible conformance (ISO 19005-1 level A).
     *
     * Everything in PDF/A-1b plus: document structure tree, tagged content,
     * natural language specification, alt text for figures.
     *
     * Note: PDFBox Preflight does not fully enforce the accessibility checks
     * over and above level B. Use VeraPDF for strict PDF/A-1a validation.
     */
    PDF_A1A("PDF/A-1a (Accessible)", "A");

    private final String displayName;
    private final String conformanceLevel;

    PdfAStandard(String displayName, String conformanceLevel) {
        this.displayName      = displayName;
        this.conformanceLevel = conformanceLevel;
    }

    public String getDisplayName()      { return displayName; }
    public String getConformanceLevel() { return conformanceLevel; }

    /** Parse case-insensitive name. Throws IllegalArgumentException if unknown. */
    public static PdfAStandard parse(String name) {
        return switch (name.toUpperCase().trim().replace("-", "_").replace("/", "_")) {
            case "PDF_A1B", "PDF_A_1B", "1B", "A1B" -> PDF_A1B;
            case "PDF_A1A", "PDF_A_1A", "1A", "A1A" -> PDF_A1A;
            default -> throw new IllegalArgumentException(
                "Unknown PDF/A standard: '" + name + "'. Use pdf-a-1b or pdf-a-1a.");
        };
    }

    @Override public String toString() { return displayName; }
}
