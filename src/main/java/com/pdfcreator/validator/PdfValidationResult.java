package com.pdfcreator.validator;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable result of a PDF/A validation run.
 *
 * Returned by PdfValidator.validate(). Contains the full list of issues,
 * a per-category breakdown, and a formatted summary for console output.
 */
public final class PdfValidationResult {

    private final String              inputPath;
    private final PdfAStandard        standard;
    private final boolean             valid;
    private final List<ValidationIssue> issues;
    private final boolean             truncated;   // true if maxErrors was hit
    private final long                durationMs;
    private final int                 totalPages;

    private PdfValidationResult(Builder b) {
        this.inputPath  = b.inputPath;
        this.standard   = b.standard;
        this.valid      = b.valid;
        this.issues     = Collections.unmodifiableList(b.issues);
        this.truncated  = b.truncated;
        this.durationMs = b.durationMs;
        this.totalPages = b.totalPages;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public String              getInputPath()  { return inputPath; }
    public PdfAStandard        getStandard()   { return standard; }
    public boolean             isValid()       { return valid; }
    public List<ValidationIssue> getIssues()   { return issues; }
    public boolean             isTruncated()   { return truncated; }
    public long                getDurationMs() { return durationMs; }
    public int                 getTotalPages() { return totalPages; }
    public int                 getIssueCount() { return issues.size(); }

    /**
     * Returns issue counts grouped by category name.
     * Keys are category strings (e.g. "Fonts", "Metadata / XMP").
     */
    public Map<String, Long> getCountByCategory() {
        return issues.stream()
            .collect(Collectors.groupingBy(ValidationIssue::getCategory, Collectors.counting()));
    }

    // -----------------------------------------------------------------------
    // Formatted summary
    // -----------------------------------------------------------------------

    public String getSummary() {
        String bar = "=".repeat(60);
        StringBuilder sb = new StringBuilder();
        sb.append(bar).append("\n");
        sb.append(String.format("  PDF/A Validation: %s%n",
            valid ? "✓ PASSED" : "✗ FAILED"));
        sb.append(bar).append("\n");
        sb.append(String.format("  File     : %s%n", inputPath));
        sb.append(String.format("  Standard : %s%n", standard));
        sb.append(String.format("  Pages    : %d%n", totalPages));
        sb.append(String.format("  Duration : %.2f s%n", durationMs / 1000.0));

        if (valid) {
            sb.append("\n  No conformance issues found.\n");
        } else {
            sb.append(String.format("%n  Issues found: %d", issues.size()));
            if (truncated) sb.append(" (truncated — more errors may exist)");
            sb.append("\n");

            // Category summary
            Map<String, Long> byCat = getCountByCategory();
            if (!byCat.isEmpty()) {
                sb.append("\n  By category:\n");
                byCat.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(e -> sb.append(String.format("    %-25s %d%n",
                        e.getKey() + ":", e.getValue())));
            }

            // Individual issues
            sb.append("\n  Detail:\n");
            for (ValidationIssue issue : issues) {
                sb.append("    ").append(issue).append("\n");
            }
        }

        sb.append(bar).append("\n");
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("PdfValidationResult[input=%s, standard=%s, valid=%b, issues=%d]",
            inputPath, standard, valid, issues.size());
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private String              inputPath  = "";
        private PdfAStandard        standard   = PdfAStandard.PDF_A1B;
        private boolean             valid      = false;
        private List<ValidationIssue> issues   = new ArrayList<>();
        private boolean             truncated  = false;
        private long                durationMs = 0;
        private int                 totalPages = 0;

        public Builder inputPath(String v)               { this.inputPath  = v; return this; }
        public Builder standard(PdfAStandard v)          { this.standard   = v; return this; }
        public Builder valid(boolean v)                  { this.valid      = v; return this; }
        public Builder issues(List<ValidationIssue> v)   { this.issues     = v; return this; }
        public Builder truncated(boolean v)              { this.truncated  = v; return this; }
        public Builder durationMs(long v)                { this.durationMs = v; return this; }
        public Builder totalPages(int v)                 { this.totalPages = v; return this; }

        public PdfValidationResult build() { return new PdfValidationResult(this); }
    }
}
