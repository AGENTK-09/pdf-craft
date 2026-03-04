package com.pdfcreator.extractor;

/**
 * Configuration for a PDF image extraction run.
 *
 * Controls which pages to scan, which images to keep (by minimum size),
 * and what format to encode the output in.
 *
 * Usage:
 *
 *   ImageExtractionOptions opts = new ImageExtractionOptions.Builder()
 *       .startPage(1)
 *       .endPage(5)
 *       .minWidth(50)            // skip images narrower than 50px
 *       .minHeight(50)           // skip images shorter than 50px
 *       .preferredFormat("png")  // encode output as PNG (default)
 *       .build();
 *
 *   // Or use defaults (all pages, 10px minimum, PNG output):
 *   ImageExtractionOptions opts = ImageExtractionOptions.defaults();
 */
public class ImageExtractionOptions {

    /** First page to scan (1-based, inclusive). -1 = start from page 1. */
    private final int startPage;

    /** Last page to scan (1-based, inclusive). -1 = scan to last page. */
    private final int endPage;

    /**
     * Minimum image width in pixels. Images narrower than this are skipped.
     *
     * PDFs often contain tiny 1x1 or small decorative images (rule lines rendered
     * as images, bullet point glyphs, watermark fragments). Setting a minimum size
     * filters these out and keeps only meaningful content images.
     *
     * Default: 10 pixels. Set to 1 to extract everything.
     */
    private final int minWidth;

    /**
     * Minimum image height in pixels. Images shorter than this are skipped.
     * Default: 10 pixels.
     */
    private final int minHeight;

    /**
     * Output format for all extracted images.
     *
     * "png"  — lossless, preserves transparency. Best for diagrams, screenshots,
     *          documents with text-in-images. Larger file size.
     * "jpg"  — lossy, smaller files. Best for photographic content.
     *          Transparency is not preserved (replaced with white background).
     *
     * Default: "png"
     */
    private final String preferredFormat;

    /**
     * When true, soft-mask images (alpha channel / transparency masks embedded
     * alongside their parent image) are skipped. These are internal PDFBox
     * artefacts and almost never what the user wants to extract.
     * Default: true — soft-masks are skipped.
     */
    private final boolean skipSoftMasks;

    /**
     * Password to open the PDF. Null means no password (unprotected document).
     * If the PDF is encrypted and this is null or wrong, extraction throws
     * PasswordRequiredException.
     */
    private final String password;

    private ImageExtractionOptions(Builder b) {
        this.startPage       = b.startPage;
        this.endPage         = b.endPage;
        this.minWidth        = b.minWidth;
        this.minHeight       = b.minHeight;
        this.preferredFormat = b.preferredFormat;
        this.skipSoftMasks   = b.skipSoftMasks;
        this.password        = b.password;
    }

    public int     getStartPage()       { return startPage; }
    public int     getEndPage()         { return endPage; }
    public int     getMinWidth()        { return minWidth; }
    public int     getMinHeight()       { return minHeight; }
    public String  getPreferredFormat() { return preferredFormat; }
    public boolean isSkipSoftMasks()    { return skipSoftMasks; }

    /**
     * Password for encrypted PDFs. Null for unprotected documents.
     * PDFBox tries this as both user password and owner password.
     */
    public String getPassword() { return password; }

    /** Default preset — all pages, 10px minimum, PNG, soft-masks skipped. */
    public static ImageExtractionOptions defaults() {
        return new Builder().build();
    }

    @Override
    public String toString() {
        return String.format(
            "ImageExtractionOptions[pages=%s-%s, minSize=%dx%d, format=%s, skipSoftMasks=%b, password=%s]",
            startPage < 0 ? "first" : startPage,
            endPage   < 0 ? "last"  : endPage,
            minWidth, minHeight, preferredFormat, skipSoftMasks,
            password != null ? "***" : "none");
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private int    startPage       = -1;
        private int    endPage         = -1;
        private int    minWidth        = 10;
        private int    minHeight       = 10;
        private String preferredFormat = "png";
        private boolean skipSoftMasks  = true;
        private String  password        = null;

        /** First page to scan, 1-based (default: 1). */
        public Builder startPage(int v)         { this.startPage       = v; return this; }

        /** Last page to scan, 1-based (default: last page). */
        public Builder endPage(int v)           { this.endPage         = v; return this; }

        /** Skip images narrower than this many pixels (default: 10). */
        public Builder minWidth(int v)          { this.minWidth        = v; return this; }

        /** Skip images shorter than this many pixels (default: 10). */
        public Builder minHeight(int v)         { this.minHeight       = v; return this; }

        /** Output format: "png" (default, lossless) or "jpg" (lossy, smaller). */
        public Builder preferredFormat(String v){ this.preferredFormat = v; return this; }

        /** Skip PDFBox soft-mask artefact images (default: true). */
        public Builder skipSoftMasks(boolean v) { this.skipSoftMasks   = v; return this; }

        /** Password for encrypted PDFs (user or owner password). Null = no password. */
        public Builder password(String v) { this.password = v; return this; }

        public ImageExtractionOptions build() { return new ImageExtractionOptions(this); }
    }
}
