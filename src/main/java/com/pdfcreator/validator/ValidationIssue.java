package com.pdfcreator.validator;

/**
 * A single validation issue found during PDF/A validation.
 *
 * Each issue maps to one ValidationError produced by PDFBox Preflight.
 *
 * ── ERROR CODE CATEGORIES ─────────────────────────────────────────────────
 *
 *   1.x  Syntax        — malformed PDF structure, bad xref, illegal streams
 *   2.x  Graphics      — device-dependent colour spaces, missing output intent
 *   3.x  Fonts         — non-embedded fonts, missing FontDescriptor, no ToUnicode
 *   4.x  Transparency  — SMask, blend modes, alpha compositing (all forbidden)
 *   5.x  Annotations   — forbidden annotation types
 *   6.x  Actions       — JavaScript, GoToRemote, Launch, URI actions
 *   7.x  Metadata      — missing/invalid XMP, missing pdfaid schema
 *   8.x  Structure     — optional content layers, encryption present
 */
public final class ValidationIssue {

    private final String  errorCode;   // e.g. "3.1.3"
    private final String  detail;      // human-readable description
    private final Integer pageNumber;  // null if not page-specific
    private final String  category;    // derived from error code prefix

    public ValidationIssue(String errorCode, String detail, Integer pageNumber) {
        this.errorCode  = errorCode  != null ? errorCode  : "?";
        this.detail     = detail     != null ? detail     : "(no detail)";
        this.pageNumber = pageNumber;
        this.category   = deriveCategory(this.errorCode);
    }

    public String  getErrorCode()  { return errorCode; }
    public String  getDetail()     { return detail; }
    public Integer getPageNumber() { return pageNumber; }
    public String  getCategory()   { return category; }

    /** True if this issue has a page number associated with it. */
    public boolean isPageSpecific() { return pageNumber != null && pageNumber > 0; }

    /**
     * Derives a human-readable category name from the numeric error code prefix.
     * The first digit of the code maps to an ISO 19005-1 clause.
     */
    private static String deriveCategory(String code) {
        if (code == null || code.isBlank()) return "Unknown";
        char first = code.charAt(0);
        return switch (first) {
            case '1' -> "Syntax";
            case '2' -> "Graphics / Colour";
            case '3' -> "Fonts";
            case '4' -> "Transparency";
            case '5' -> "Annotations";
            case '6' -> "Actions";
            case '7' -> "Metadata / XMP";
            case '8' -> "Document Structure";
            default  -> "Other";
        };
    }

    @Override
    public String toString() {
        String page = pageNumber != null && pageNumber > 0
            ? " [page " + pageNumber + "]" : "";
        return String.format("[%s] %s%s — %s", errorCode, category, page, detail);
    }
}
