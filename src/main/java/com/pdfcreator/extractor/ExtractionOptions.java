package com.pdfcreator.extractor;

/**
 * Configuration for a PDF text extraction run.
 *
 * All fields are optional — defaults produce the most useful output
 * for the majority of use cases.
 *
 * Usage (fluent builder):
 *
 *   ExtractionOptions opts = new ExtractionOptions.Builder()
 *       .startPage(2)
 *       .endPage(4)
 *       .sortByPosition(true)
 *       .stripExtraWhitespace(true)
 *       .includeMetadata(true)
 *       .extractPerPage(true)
 *       .build();
 *
 *   // Or use the default preset (all pages, sort on, metadata on, per-page on):
 *   ExtractionOptions opts = ExtractionOptions.defaults();
 */
public class ExtractionOptions {

    /** First page to extract (1-based, inclusive). -1 = start from page 1. */
    private final int startPage;

    /** Last page to extract (1-based, inclusive). -1 = extract to last page. */
    private final int endPage;

    /**
     * When true, PDFTextStripper sorts text fragments by their Y/X position on the
     * page before concatenating. Produces correct reading order for most layouts.
     * When false, text is emitted in raw PDF content-stream order.
     * Default: true.
     */
    private final boolean sortByPosition;

    /**
     * When true, collapses multiple whitespace characters into a single space and
     * trims leading/trailing whitespace from each line.
     * Default: false — preserves original spacing as laid out in the PDF.
     */
    private final boolean stripExtraWhitespace;

    /**
     * When true, the ExtractionResult includes the document information dictionary
     * (title, author, subject, keywords, creator, producer, creation date).
     * Default: true.
     */
    private final boolean includeMetadata;

    /**
     * When true, ExtractionResult.getPageTexts() is populated with one entry per page.
     * The full concatenated text is always available regardless of this flag.
     * Default: true.
     */
    private final boolean extractPerPage;

    private ExtractionOptions(Builder b) {
        this.startPage            = b.startPage;
        this.endPage              = b.endPage;
        this.sortByPosition       = b.sortByPosition;
        this.stripExtraWhitespace = b.stripExtraWhitespace;
        this.includeMetadata      = b.includeMetadata;
        this.extractPerPage       = b.extractPerPage;
    }

    public int     getStartPage()            { return startPage; }
    public int     getEndPage()              { return endPage; }
    public boolean isSortByPosition()        { return sortByPosition; }
    public boolean isStripExtraWhitespace()  { return stripExtraWhitespace; }
    public boolean isIncludeMetadata()       { return includeMetadata; }
    public boolean isExtractPerPage()        { return extractPerPage; }

    /** Default preset — extracts all pages, sorting on, metadata on, per-page on. */
    public static ExtractionOptions defaults() { return new Builder().build(); }

    @Override
    public String toString() {
        return String.format(
            "ExtractionOptions[pages=%s-%s, sort=%b, stripWS=%b, metadata=%b, perPage=%b]",
            startPage < 0 ? "first" : startPage,
            endPage   < 0 ? "last"  : endPage,
            sortByPosition, stripExtraWhitespace, includeMetadata, extractPerPage);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private int     startPage            = -1;
        private int     endPage              = -1;
        private boolean sortByPosition       = true;
        private boolean stripExtraWhitespace = false;
        private boolean includeMetadata      = true;
        private boolean extractPerPage       = true;

        public Builder startPage(int v)                { this.startPage = v;            return this; }
        public Builder endPage(int v)                  { this.endPage = v;              return this; }
        public Builder sortByPosition(boolean v)       { this.sortByPosition = v;       return this; }
        public Builder stripExtraWhitespace(boolean v) { this.stripExtraWhitespace = v; return this; }
        public Builder includeMetadata(boolean v)      { this.includeMetadata = v;      return this; }
        public Builder extractPerPage(boolean v)       { this.extractPerPage = v;       return this; }

        public ExtractionOptions build() { return new ExtractionOptions(this); }
    }
}
