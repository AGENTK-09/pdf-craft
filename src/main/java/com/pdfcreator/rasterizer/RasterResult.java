package com.pdfcreator.rasterizer;

import java.util.Collections;
import java.util.List;

/**
 * Immutable result of a PDF rasterization run.
 *
 * Returned by PdfRasterizer.rasterize(). Contains the list of output image
 * files written, the settings used, and timing information.
 *
 * Usage:
 *
 *   RasterResult result = rasterizer.rasterize(opts);
 *   System.out.println(result.getSummary());
 *
 *   result.getOutputFiles().forEach(f ->
 *       System.out.println("  " + f));
 */
public final class RasterResult {

    private final String       inputPath;
    private final String       outputDir;
    private final RasterOptions.Format format;
    private final float        dpi;
    private final int          totalPages;       // total pages in the source PDF
    private final int          pagesRendered;    // number of pages actually rendered
    private final List<String> outputFiles;      // absolute paths of all written image files
    private final long         durationMs;
    private final long         totalSizeBytes;   // sum of all output file sizes

    private RasterResult(Builder b) {
        this.inputPath      = b.inputPath;
        this.outputDir      = b.outputDir;
        this.format         = b.format;
        this.dpi            = b.dpi;
        this.totalPages     = b.totalPages;
        this.pagesRendered  = b.pagesRendered;
        this.outputFiles    = Collections.unmodifiableList(b.outputFiles);
        this.durationMs     = b.durationMs;
        this.totalSizeBytes = b.totalSizeBytes;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public String             getInputPath()      { return inputPath; }
    public String             getOutputDir()      { return outputDir; }
    public RasterOptions.Format getFormat()       { return format; }
    public float              getDpi()            { return dpi; }
    public int                getTotalPages()     { return totalPages; }
    public int                getPagesRendered()  { return pagesRendered; }
    public List<String>       getOutputFiles()    { return outputFiles; }
    public long               getDurationMs()     { return durationMs; }
    public long               getTotalSizeBytes() { return totalSizeBytes; }

    // -----------------------------------------------------------------------
    // Formatted summary
    // -----------------------------------------------------------------------

    public String getSummary() {
        String bar = "=".repeat(55);
        StringBuilder sb = new StringBuilder();
        sb.append(bar).append("\n");
        sb.append("  Rasterization Complete\n");
        sb.append(bar).append("\n");
        sb.append(String.format("  Input          : %s%n", inputPath));
        sb.append(String.format("  Output dir     : %s%n", outputDir));
        sb.append(String.format("  Format         : %s @ %.0f DPI%n", format, dpi));
        sb.append(String.format("  Pages total    : %d%n", totalPages));
        sb.append(String.format("  Pages rendered : %d%n", pagesRendered));
        sb.append(String.format("  Total size     : %s%n", humanSize(totalSizeBytes)));
        sb.append(String.format("  Duration       : %.2f s%n", durationMs / 1000.0));
        sb.append(bar).append("\n");
        sb.append("  Output files:\n");
        outputFiles.forEach(f -> sb.append("    ").append(f).append("\n"));
        sb.append(bar).append("\n");
        return sb.toString();
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024)          return bytes + " B";
        if (bytes < 1024 * 1024)   return String.format("%.1f KB", bytes / 1024.0);
        return                            String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    @Override
    public String toString() {
        return String.format("RasterResult[input=%s, format=%s, dpi=%.0f, pages=%d, files=%d]",
            inputPath, format, dpi, pagesRendered, outputFiles.size());
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private String             inputPath      = "";
        private String             outputDir      = "";
        private RasterOptions.Format format       = RasterOptions.Format.PNG;
        private float              dpi            = 150f;
        private int                totalPages     = 0;
        private int                pagesRendered  = 0;
        private List<String>       outputFiles    = List.of();
        private long               durationMs     = 0;
        private long               totalSizeBytes = 0;

        public Builder inputPath(String v)            { this.inputPath      = v; return this; }
        public Builder outputDir(String v)            { this.outputDir      = v; return this; }
        public Builder format(RasterOptions.Format v) { this.format         = v; return this; }
        public Builder dpi(float v)                   { this.dpi            = v; return this; }
        public Builder totalPages(int v)              { this.totalPages     = v; return this; }
        public Builder pagesRendered(int v)           { this.pagesRendered  = v; return this; }
        public Builder outputFiles(List<String> v)    { this.outputFiles    = v; return this; }
        public Builder durationMs(long v)             { this.durationMs     = v; return this; }
        public Builder totalSizeBytes(long v)         { this.totalSizeBytes = v; return this; }

        public RasterResult build() { return new RasterResult(this); }
    }
}
