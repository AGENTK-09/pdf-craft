package com.pdfcreator.htmlconverter;

/**
 * Immutable configuration for an HTML-to-PDF conversion job.
 *
 * All fields are set via the Builder. Required fields are validated in build().
 *
 * ── RENDERER ──────────────────────────────────────────────────────────────
 *
 *   WKHTMLTOPDF (default and only renderer in round 1)
 *   Uses wkhtmltopdf 0.12.x via ProcessBuilder. The binary must be on PATH
 *   or its absolute path set via the WKHTMLTOPDF_PATH environment variable.
 *   Supports CSS2.1 + most of CSS3 (flexbox: partial, grid: no).
 *   JavaScript execution is disabled by default.
 *
 * ── BASE URL ──────────────────────────────────────────────────────────────
 *
 *   wkhtmltopdf resolves relative paths (CSS, images, fonts) relative to the
 *   input HTML file's directory by default — this is the correct behaviour.
 *   Override with baseUrl() only when the HTML uses paths relative to a
 *   different root (e.g. a compiled dist/ directory served from project root).
 *
 * ── PAGE SIZE ─────────────────────────────────────────────────────────────
 *
 *   Passed directly to wkhtmltopdf --page-size. Accepted values match Qt
 *   paper size names: A4, A3, A5, Letter, Legal, Tabloid, etc.
 *   Default: A4.
 *
 * ── MARGINS ───────────────────────────────────────────────────────────────
 *
 *   Specified in millimetres (wkhtmltopdf's native unit for margins).
 *   Passed as: -T <n>mm  -B <n>mm  -L <n>mm  -R <n>mm
 *   Default: 10mm on all sides.
 *
 * ── JAVASCRIPT ────────────────────────────────────────────────────────────
 *
 *   Disabled by default. Enable with enableJavascript(true).
 *   When enabled, javascriptDelayMs controls how long wkhtmltopdf waits
 *   after the page's load event fires before capturing the PDF — allowing
 *   JS-rendered content to finish rendering.
 *
 *   Use only for trusted HTML input. Do not enable for untrusted sources.
 *
 * ── USAGE ─────────────────────────────────────────────────────────────────
 *
 *   HtmlConversionOptions opts = new HtmlConversionOptions.Builder()
 *       .inputPath("invoice.html")
 *       .outputPath("invoice.pdf")
 *       .pageSize("A4")
 *       .marginTop(15)
 *       .marginBottom(15)
 *       .build();
 *
 *   // With print-media CSS and a JS delay
 *   HtmlConversionOptions opts = new HtmlConversionOptions.Builder()
 *       .inputPath("report.html")
 *       .outputPath("report.pdf")
 *       .printMediaType(true)
 *       .enableJavascript(true)
 *       .javascriptDelayMs(1500)
 *       .build();
 */
public final class HtmlConversionOptions {

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final String  inputPath;          // required — source HTML file
    private final String  outputPath;         // required — output PDF file
    private final String  baseUrl;            // nullable — overrides wkhtmltopdf default
    private final String  pageSize;           // e.g. "A4", "Letter"
    private final String  orientation;        // "Portrait" or "Landscape"
    private final int     marginTopMm;        // top margin in mm
    private final int     marginBottomMm;     // bottom margin in mm
    private final int     marginLeftMm;       // left margin in mm
    private final int     marginRightMm;      // right margin in mm
    private final float   zoom;               // zoom factor (1.0 = 100%)
    private final boolean enableJavascript;   // JS execution
    private final int     javascriptDelayMs;  // wait after load event (ms)
    private final boolean printMediaType;     // use @media print CSS
    private final String  userStyleSheet;     // nullable — extra CSS file to inject
    private final boolean loadImages;         // load and embed images (default: true)
    private final String  title;             // nullable — override <title> in PDF info
    private final String  author;            // nullable — PDF author metadata
    private final String  subject;           // nullable — PDF subject metadata

    private HtmlConversionOptions(Builder b) {
        this.inputPath         = b.inputPath;
        this.outputPath        = b.outputPath;
        this.baseUrl           = b.baseUrl;
        this.pageSize          = b.pageSize;
        this.orientation       = b.orientation;
        this.marginTopMm       = b.marginTopMm;
        this.marginBottomMm    = b.marginBottomMm;
        this.marginLeftMm      = b.marginLeftMm;
        this.marginRightMm     = b.marginRightMm;
        this.zoom              = b.zoom;
        this.enableJavascript  = b.enableJavascript;
        this.javascriptDelayMs = b.javascriptDelayMs;
        this.printMediaType    = b.printMediaType;
        this.userStyleSheet    = b.userStyleSheet;
        this.loadImages        = b.loadImages;
        this.title             = b.title;
        this.author            = b.author;
        this.subject           = b.subject;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public String  getInputPath()          { return inputPath; }
    public String  getOutputPath()         { return outputPath; }
    public String  getBaseUrl()            { return baseUrl; }
    public String  getPageSize()           { return pageSize; }
    public String  getOrientation()        { return orientation; }
    public int     getMarginTopMm()        { return marginTopMm; }
    public int     getMarginBottomMm()     { return marginBottomMm; }
    public int     getMarginLeftMm()       { return marginLeftMm; }
    public int     getMarginRightMm()      { return marginRightMm; }
    public float   getZoom()               { return zoom; }
    public boolean isEnableJavascript()    { return enableJavascript; }
    public int     getJavascriptDelayMs()  { return javascriptDelayMs; }
    public boolean isPrintMediaType()      { return printMediaType; }
    public String  getUserStyleSheet()     { return userStyleSheet; }
    public boolean isLoadImages()          { return loadImages; }
    public String  getTitle()              { return title; }
    public String  getAuthor()             { return author; }
    public String  getSubject()            { return subject; }

    @Override
    public String toString() {
        return String.format(
            "HtmlConversionOptions[input=%s, output=%s, pageSize=%s, orientation=%s, " +
            "margins=%d/%d/%d/%d mm, js=%s, printMedia=%s]",
            inputPath, outputPath, pageSize, orientation,
            marginTopMm, marginBottomMm, marginLeftMm, marginRightMm,
            enableJavascript, printMediaType);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {

        private String  inputPath         = null;
        private String  outputPath        = null;
        private String  baseUrl           = null;
        private String  pageSize          = "A4";
        private String  orientation       = "Portrait";
        private int     marginTopMm       = 10;
        private int     marginBottomMm    = 10;
        private int     marginLeftMm      = 10;
        private int     marginRightMm     = 10;
        private float   zoom              = 1.0f;
        private boolean enableJavascript  = false;
        private int     javascriptDelayMs = 0;
        private boolean printMediaType    = false;
        private String  userStyleSheet    = null;
        private boolean loadImages        = true;
        private String  title             = null;
        private String  author            = null;
        private String  subject           = null;

        /** Path to the source HTML file (required). */
        public Builder inputPath(String v)          { this.inputPath         = v; return this; }

        /** Output PDF file path (required). */
        public Builder outputPath(String v)         { this.outputPath        = v; return this; }

        /**
         * Base URL for resolving relative CSS/image paths.
         * Default: null — wkhtmltopdf uses the input file's parent directory,
         * which is the correct default for most use cases.
         */
        public Builder baseUrl(String v)            { this.baseUrl           = v; return this; }

        /**
         * Paper size name. Passed directly to wkhtmltopdf --page-size.
         * Valid values: A4 (default), A3, A5, Letter, Legal, Tabloid, etc.
         */
        public Builder pageSize(String v)           { this.pageSize          = v; return this; }

        /** Page orientation: "Portrait" (default) or "Landscape". */
        public Builder orientation(String v)        { this.orientation       = v; return this; }

        /** Top margin in millimetres. Default: 10. */
        public Builder marginTop(int mm)            { this.marginTopMm       = mm; return this; }

        /** Bottom margin in millimetres. Default: 10. */
        public Builder marginBottom(int mm)         { this.marginBottomMm    = mm; return this; }

        /** Left margin in millimetres. Default: 10. */
        public Builder marginLeft(int mm)           { this.marginLeftMm      = mm; return this; }

        /** Right margin in millimetres. Default: 10. */
        public Builder marginRight(int mm)          { this.marginRightMm     = mm; return this; }

        /**
         * Zoom factor applied to the page before rendering.
         * 1.0 = 100% (default). Use < 1.0 to shrink, > 1.0 to enlarge.
         * Useful for HTML pages designed for a specific screen width.
         */
        public Builder zoom(float v)                { this.zoom              = v; return this; }

        /**
         * Enable JavaScript execution. Default: false.
         * Use only for trusted HTML. Combine with javascriptDelayMs() to
         * wait for JS-rendered content to appear before capture.
         */
        public Builder enableJavascript(boolean v)  { this.enableJavascript  = v; return this; }

        /**
         * Milliseconds to wait after the page load event fires before
         * capturing the PDF. Only effective when enableJavascript is true.
         * Default: 0 (no delay).
         */
        public Builder javascriptDelayMs(int ms)    { this.javascriptDelayMs = Math.max(0, ms); return this; }

        /**
         * When true, wkhtmltopdf applies @media print CSS rules instead
         * of @media screen. Use this when the HTML has print-specific styles.
         * Default: false.
         */
        public Builder printMediaType(boolean v)    { this.printMediaType    = v; return this; }

        /**
         * Path to an additional CSS file injected after the page's own styles.
         * Useful for injecting global overrides (e.g. hiding navigation bars,
         * forcing fonts) without modifying the HTML source.
         * Default: null (no extra stylesheet).
         */
        public Builder userStyleSheet(String v)     { this.userStyleSheet    = v; return this; }

        /**
         * Whether to load and embed images. Default: true.
         * Set to false for text-only PDFs where images should be suppressed.
         */
        public Builder loadImages(boolean v)        { this.loadImages        = v; return this; }

        /**
         * Title for the PDF document information dictionary.
         * Overrides the HTML &lt;title&gt; tag value when set.
         * Default: null — title is extracted from the &lt;title&gt; tag.
         */
        public Builder title(String v)              { this.title             = v; return this; }

        /** Author for the PDF document information dictionary. */
        public Builder author(String v)             { this.author            = v; return this; }

        /** Subject for the PDF document information dictionary. */
        public Builder subject(String v)            { this.subject           = v; return this; }

        public HtmlConversionOptions build() {
            if (inputPath == null || inputPath.isBlank())
                throw new IllegalStateException("HtmlConversionOptions: inputPath is required");
            if (outputPath == null || outputPath.isBlank())
                throw new IllegalStateException("HtmlConversionOptions: outputPath is required");
            if (pageSize == null || pageSize.isBlank())
                throw new IllegalStateException("HtmlConversionOptions: pageSize must not be blank");
            if (!orientation.equalsIgnoreCase("Portrait") && !orientation.equalsIgnoreCase("Landscape"))
                throw new IllegalStateException(
                    "HtmlConversionOptions: orientation must be 'Portrait' or 'Landscape', got: " + orientation);
            if (zoom <= 0)
                throw new IllegalStateException("HtmlConversionOptions: zoom must be > 0");
            return new HtmlConversionOptions(this);
        }
    }
}
