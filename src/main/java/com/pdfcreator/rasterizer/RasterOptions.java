package com.pdfcreator.rasterizer;

/**
 * Configuration for rasterizing PDF pages into images.
 *
 * Immutable once built via the Builder.
 *
 * ── OUTPUT FORMAT ─────────────────────────────────────────────────────────
 *
 *   PNG  — lossless, supports transparency. Best for documents with text,
 *          diagrams, or screenshots. Larger files than JPEG.
 *
 *   JPEG — lossy, no transparency. Best for photo-heavy pages where file
 *          size matters. Quality is configurable (0–100).
 *
 *   Both produce BufferedImage internally via PDFRenderer; the format only
 *   affects how the image is written to disk.
 *
 * ── DPI / RESOLUTION ──────────────────────────────────────────────────────
 *
 *   DPI controls rendering sharpness. Common values:
 *     72  dpi — screen resolution (smallest files, lowest quality)
 *    150  dpi — good for on-screen reading / email attachments
 *    300  dpi — print quality (recommended for customer-facing documents)
 *    600  dpi — archival / OCR pre-processing
 *
 *   PDFBox's PDFRenderer.renderImageWithDPI(pageIndex, dpi) uses this value.
 *
 * ── PAGE RANGE ─────────────────────────────────────────────────────────────
 *
 *   startPage / endPage are 1-based and inclusive.
 *   -1 means "first page" / "last page" respectively.
 *   A single page can be selected with startPage(n).endPage(n).
 *
 * ── OUTPUT NAMING ──────────────────────────────────────────────────────────
 *
 *   outputDir    — directory where image files are written (required).
 *   filePrefix   — prefix for output filenames (default: "page").
 *                  Files are named: <prefix>-<pageNumber>.<ext>
 *                  e.g. page-001.png, page-002.png
 *
 * ── USAGE ──────────────────────────────────────────────────────────────────
 *
 *   // All pages to PNG at 150 dpi
 *   RasterOptions opts = new RasterOptions.Builder()
 *       .inputPath("report.pdf")
 *       .outputDir("report-pages/")
 *       .format(RasterOptions.Format.PNG)
 *       .dpi(150)
 *       .build();
 *
 *   // Page 2 only, JPEG at 300 dpi, quality 90
 *   RasterOptions opts = new RasterOptions.Builder()
 *       .inputPath("report.pdf")
 *       .outputDir("out/")
 *       .format(RasterOptions.Format.JPEG)
 *       .dpi(300)
 *       .jpegQuality(0.90f)
 *       .startPage(2)
 *       .endPage(2)
 *       .build();
 */
public final class RasterOptions {

    // -----------------------------------------------------------------------
    // Format enum
    // -----------------------------------------------------------------------

    public enum Format {
        PNG("png"),
        JPEG("jpg");

        private final String extension;
        Format(String ext) { this.extension = ext; }
        public String getExtension() { return extension; }

        /** Parse case-insensitive format name. Throws IllegalArgumentException if unknown. */
        public static Format parse(String name) {
            return switch (name.toUpperCase().trim()) {
                case "PNG"              -> PNG;
                case "JPEG", "JPG"      -> JPEG;
                default -> throw new IllegalArgumentException(
                    "Unknown image format: '" + name + "'. Use PNG or JPEG.");
            };
        }
    }

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final String  inputPath;
    private final String  outputDir;
    private final String  filePrefix;
    private final Format  format;
    private final float   dpi;
    private final float   jpegQuality;   // 0.0 – 1.0; only used for JPEG
    private final int     startPage;     // 1-based; -1 = first page
    private final int     endPage;       // 1-based; -1 = last page
    private final String  password;      // null = no password

    private RasterOptions(Builder b) {
        this.inputPath   = b.inputPath;
        this.outputDir   = b.outputDir;
        this.filePrefix  = b.filePrefix;
        this.format      = b.format;
        this.dpi         = b.dpi;
        this.jpegQuality = b.jpegQuality;
        this.startPage   = b.startPage;
        this.endPage     = b.endPage;
        this.password    = b.password;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public String  getInputPath()   { return inputPath; }
    public String  getOutputDir()   { return outputDir; }
    public String  getFilePrefix()  { return filePrefix; }
    public Format  getFormat()      { return format; }
    public float   getDpi()         { return dpi; }
    public float   getJpegQuality() { return jpegQuality; }
    public int     getStartPage()   { return startPage; }
    public int     getEndPage()     { return endPage; }
    public String  getPassword()    { return password; }

    @Override
    public String toString() {
        return String.format(
            "RasterOptions[input=%s, outputDir=%s, format=%s, dpi=%.0f, " +
            "pages=%s-%s, prefix=%s]",
            inputPath, outputDir, format, dpi,
            startPage < 0 ? "first" : startPage,
            endPage   < 0 ? "last"  : endPage,
            filePrefix);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {

        private String inputPath   = null;
        private String outputDir   = null;
        private String filePrefix  = "page";
        private Format format      = Format.PNG;
        private float  dpi         = 150f;
        private float  jpegQuality = 0.85f;
        private int    startPage   = -1;
        private int    endPage     = -1;
        private String password    = null;

        /** Path to the source PDF (required). */
        public Builder inputPath(String v)   { this.inputPath   = v; return this; }

        /** Directory where rendered image files are written (required). Created if absent. */
        public Builder outputDir(String v)   { this.outputDir   = v; return this; }

        /**
         * Filename prefix for output images.
         * Files are named: <prefix>-<pageNumber>.<ext>  e.g. page-001.png
         * Default: "page"
         */
        public Builder filePrefix(String v)  { this.filePrefix  = v; return this; }

        /** Output image format: PNG (default) or JPEG. */
        public Builder format(Format v)      { this.format      = v; return this; }

        /**
         * Rendering resolution in dots per inch.
         * Default: 150. Recommended: 300 for print-quality output.
         */
        public Builder dpi(float v)          { this.dpi         = v; return this; }

        /**
         * JPEG compression quality: 0.0 (worst) to 1.0 (best).
         * Default: 0.85. Only applies when format is JPEG.
         */
        public Builder jpegQuality(float v)  { this.jpegQuality = Math.max(0f, Math.min(1f, v)); return this; }

        /** First page to render, 1-based inclusive. Default: -1 (first page). */
        public Builder startPage(int v)      { this.startPage   = v; return this; }

        /** Last page to render, 1-based inclusive. Default: -1 (last page). */
        public Builder endPage(int v)        { this.endPage     = v; return this; }

        /** Password for encrypted PDFs. Null for unprotected documents. */
        public Builder password(String v)    { this.password    = v; return this; }

        public RasterOptions build() {
            if (inputPath == null || inputPath.isBlank())
                throw new IllegalStateException("RasterOptions: inputPath is required");
            if (outputDir == null || outputDir.isBlank())
                throw new IllegalStateException("RasterOptions: outputDir is required");
            if (dpi <= 0)
                throw new IllegalStateException("RasterOptions: dpi must be > 0");
            return new RasterOptions(this);
        }
    }
}
