package com.pdfcreator.htmlconverter;

import java.util.Collections;
import java.util.List;

/**
 * Immutable result of an HTML-to-PDF conversion.
 *
 * Returned by HtmlToPdfConverter.convert(). Contains the output path,
 * renderer used, warnings captured from wkhtmltopdf stderr, file size,
 * and timing.
 *
 * Usage:
 *
 *   HtmlConversionResult result = converter.convert(opts);
 *   System.out.println(result.getSummary());
 *
 *   if (!result.getWarnings().isEmpty()) {
 *       result.getWarnings().forEach(w -> System.out.println("WARN: " + w));
 *   }
 */
public final class HtmlConversionResult {

    private final String       inputPath;
    private final String       outputPath;
    private final String       renderer;       // "wkhtmltopdf"
    private final long         outputSizeBytes;
    private final long         durationMs;
    private final List<String> warnings;       // non-fatal messages from wkhtmltopdf stderr

    private HtmlConversionResult(Builder b) {
        this.inputPath       = b.inputPath;
        this.outputPath      = b.outputPath;
        this.renderer        = b.renderer;
        this.outputSizeBytes = b.outputSizeBytes;
        this.durationMs      = b.durationMs;
        this.warnings        = Collections.unmodifiableList(b.warnings);
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public String       getInputPath()       { return inputPath; }
    public String       getOutputPath()      { return outputPath; }
    public String       getRenderer()        { return renderer; }
    public long         getOutputSizeBytes() { return outputSizeBytes; }
    public long         getDurationMs()      { return durationMs; }
    public List<String> getWarnings()        { return warnings; }

    // -----------------------------------------------------------------------
    // Formatted summary
    // -----------------------------------------------------------------------

    public String getSummary() {
        String bar = "=".repeat(55);
        StringBuilder sb = new StringBuilder();
        sb.append(bar).append("\n");
        sb.append("  HTML-to-PDF Conversion Complete\n");
        sb.append(bar).append("\n");
        sb.append(String.format("  Input          : %s%n", inputPath));
        sb.append(String.format("  Output         : %s%n", outputPath));
        sb.append(String.format("  Renderer       : %s%n", renderer));
        sb.append(String.format("  Output size    : %s%n", humanSize(outputSizeBytes)));
        sb.append(String.format("  Duration       : %.2f s%n", durationMs / 1000.0));
        if (!warnings.isEmpty()) {
            sb.append(String.format("  Warnings       : %d (see below)%n", warnings.size()));
        }
        sb.append(bar).append("\n");
        if (!warnings.isEmpty()) {
            sb.append("  Warnings from renderer:\n");
            warnings.forEach(w -> sb.append("    ").append(w).append("\n"));
            sb.append(bar).append("\n");
        }
        return sb.toString();
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024)        return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return                          String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    @Override
    public String toString() {
        return String.format("HtmlConversionResult[input=%s, output=%s, renderer=%s, " +
            "size=%s, duration=%.2fs, warnings=%d]",
            inputPath, outputPath, renderer,
            humanSize(outputSizeBytes), durationMs / 1000.0, warnings.size());
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private String       inputPath       = "";
        private String       outputPath      = "";
        private String       renderer        = "wkhtmltopdf";
        private long         outputSizeBytes = 0;
        private long         durationMs      = 0;
        private List<String> warnings        = List.of();

        public Builder inputPath(String v)       { this.inputPath       = v; return this; }
        public Builder outputPath(String v)      { this.outputPath      = v; return this; }
        public Builder renderer(String v)        { this.renderer        = v; return this; }
        public Builder outputSizeBytes(long v)   { this.outputSizeBytes = v; return this; }
        public Builder durationMs(long v)        { this.durationMs      = v; return this; }
        public Builder warnings(List<String> v)  { this.warnings        = v; return this; }

        public HtmlConversionResult build() { return new HtmlConversionResult(this); }
    }
}
