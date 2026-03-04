package com.pdfcreator.extractor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable result of a PDF text extraction operation.
 *
 * Always available:
 *   getText()       — full concatenated text for all extracted pages
 *   getPageCount()  — total number of pages in the PDF
 *   getWordCount()  — approximate word count (split on whitespace)
 *   getCharCount()  — total character count
 *
 * Available when ExtractionOptions.extractPerPage = true (default):
 *   getPageTexts()  — List<String> with one entry per extracted page (1-based index + 1)
 *
 * Available when ExtractionOptions.includeMetadata = true (default):
 *   getMetadata()   — Map<String, String> containing any non-null fields from the
 *                     PDF's document information dictionary:
 *                       "title", "author", "subject", "keywords",
 *                       "creator", "producer", "creationDate", "modificationDate"
 *
 * Usage:
 *   ExtractionResult result = extractor.extract("report.pdf");
 *
 *   System.out.println(result.getText());
 *   result.getPageTexts().forEach((i, text) -> System.out.println("Page " + i + ": " + text));
 *   System.out.println("Author: " + result.getMetadata().get("author"));
 */
public class ExtractionResult {

    private final String              text;
    private final List<String>        pageTexts;   // one entry per extracted page; empty if not requested
    private final Map<String, String> metadata;    // document information fields; empty if not requested
    private final int                 pageCount;   // total pages in the PDF
    private final int                 wordCount;
    private final int                 charCount;
    private final String              sourcePath;

    private ExtractionResult(Builder b) {
        this.text       = b.text;
        this.pageTexts  = Collections.unmodifiableList(b.pageTexts);
        this.metadata   = Collections.unmodifiableMap(b.metadata);
        this.pageCount  = b.pageCount;
        this.wordCount  = b.text == null || b.text.isBlank() ? 0
                          : b.text.trim().split("\\s+").length;
        this.charCount  = b.text == null ? 0 : b.text.length();
        this.sourcePath = b.sourcePath;
    }

    /** Full concatenated extracted text. Never null. */
    public String getText() { return text != null ? text : ""; }

    /**
     * Per-page text list. Index 0 = first extracted page (which may be page N of the PDF
     * if startPage > 1). Empty list if extractPerPage was false.
     */
    public List<String> getPageTexts() { return pageTexts; }

    /**
     * Document information metadata map. Keys: title, author, subject, keywords,
     * creator, producer, creationDate, modificationDate. Only non-null PDF fields
     * are included. Empty map if includeMetadata was false.
     */
    public Map<String, String> getMetadata() { return metadata; }

    /** Total page count of the source PDF (not the number of pages extracted). */
    public int getPageCount() { return pageCount; }

    /** Approximate word count of the extracted text (split on whitespace). */
    public int getWordCount() { return wordCount; }

    /** Character count of the extracted text. */
    public int getCharCount() { return charCount; }

    /** Path of the PDF file that was extracted. */
    public String getSourcePath() { return sourcePath; }

    /** Returns a compact summary suitable for logging. */
    @Override
    public String toString() {
        return String.format(
            "ExtractionResult[source=%s, pages=%d, extracted=%d, words=%d, chars=%d, metadataFields=%d]",
            sourcePath, pageCount, pageTexts.isEmpty() ? -1 : pageTexts.size(),
            wordCount, charCount, metadata.size());
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private String              text       = "";
        private List<String>        pageTexts  = List.of();
        private Map<String, String> metadata   = Map.of();
        private int                 pageCount  = 0;
        private String              sourcePath = "";

        public Builder text(String v)                    { this.text       = v;    return this; }
        public Builder pageTexts(List<String> v)         { this.pageTexts  = v;    return this; }
        public Builder metadata(Map<String, String> v)   { this.metadata   = v;    return this; }
        public Builder pageCount(int v)                  { this.pageCount  = v;    return this; }
        public Builder sourcePath(String v)              { this.sourcePath = v;    return this; }

        public ExtractionResult build() { return new ExtractionResult(this); }
    }
}
