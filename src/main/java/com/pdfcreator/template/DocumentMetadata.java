package com.pdfcreator.template;

/**
 * Immutable model for PDF document information metadata.
 *
 * Maps directly to fields on PDDocumentInformation (PDFBox):
 *
 *   title     → PDDocumentInformation.setTitle()
 *               Shown in the PDF viewer's title bar and tab.
 *               Supports {{placeholder}} substitution from DocumentData scalars,
 *               so a bank statement can set its title to
 *               "{{customer_name}} — Statement {{statement_date}}".
 *
 *   author    → PDDocumentInformation.setAuthor()
 *               The person or organisation that authored the document.
 *               For bank documents: the bank name (use {{bank_name}}).
 *
 *   subject   → PDDocumentInformation.setSubject()
 *               A short description of the document's content.
 *               Example: "Monthly Current Account Statement"
 *
 *   keywords  → PDDocumentInformation.setKeywords()
 *               Space- or comma-separated keywords for search indexing.
 *               Example: "bank statement current account {{account_no}}"
 *
 *   creator   → PDDocumentInformation.setCreator()
 *               The application that created the original content.
 *               Defaults to "PdfCreator" if not specified.
 *
 *   producer  → PDDocumentInformation.setProducer()
 *               The library or tool that converted or produced the PDF.
 *               Defaults to "PdfCreator / Apache PDFBox 3" if not specified.
 *
 * All fields are optional. If a field is null or blank, the corresponding
 * PDDocumentInformation setter is not called — leaving the field absent in
 * the PDF metadata rather than setting it to an empty string.
 *
 * All fields support {{placeholder}} substitution before being applied.
 * This is resolved by PlaceholderResolver inside RenderPipeline.
 *
 * JSON definition example (inside a template object):
 *
 *   "metadata": {
 *     "title":    "{{customer_name}} — Statement {{statement_date}}",
 *     "author":   "{{bank_name}}",
 *     "subject":  "Monthly Current Account Statement",
 *     "keywords": "bank statement current account {{account_no}} {{sort_code}}",
 *     "creator":  "National Bank Customer Document System",
 *     "producer":  "PdfCreator / Apache PDFBox 3"
 *   }
 */
public class DocumentMetadata {

    private final String title;
    private final String author;
    private final String subject;
    private final String keywords;
    private final String creator;
    private final String producer;

    private DocumentMetadata(Builder b) {
        this.title    = b.title;
        this.author   = b.author;
        this.subject  = b.subject;
        this.keywords = b.keywords;
        this.creator  = b.creator;
        this.producer = b.producer;
    }

    public String getTitle()    { return title; }
    public String getAuthor()   { return author; }
    public String getSubject()  { return subject; }
    public String getKeywords() { return keywords; }
    public String getCreator()  { return creator; }
    public String getProducer() { return producer; }

    /** Returns true if at least one field is non-null and non-blank. */
    public boolean hasAnyField() {
        return hasValue(title) || hasValue(author) || hasValue(subject)
            || hasValue(keywords) || hasValue(creator) || hasValue(producer);
    }

    private static boolean hasValue(String s) { return s != null && !s.isBlank(); }

    @Override
    public String toString() {
        return String.format(
            "DocumentMetadata[title=%s, author=%s, subject=%s, keywords=%s, creator=%s, producer=%s]",
            title, author, subject, keywords, creator, producer);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private String title    = null;
        private String author   = null;
        private String subject  = null;
        private String keywords = null;
        private String creator  = "PdfCreator";
        private String producer = "PdfCreator / Apache PDFBox 3";

        public Builder title(String v)    { this.title    = v; return this; }
        public Builder author(String v)   { this.author   = v; return this; }
        public Builder subject(String v)  { this.subject  = v; return this; }
        public Builder keywords(String v) { this.keywords = v; return this; }
        public Builder creator(String v)  { this.creator  = v; return this; }
        public Builder producer(String v) { this.producer = v; return this; }

        public DocumentMetadata build() { return new DocumentMetadata(this); }
    }
}
