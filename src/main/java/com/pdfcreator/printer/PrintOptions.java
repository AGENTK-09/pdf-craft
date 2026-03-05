package com.pdfcreator.printer;

/**
 * Configuration for a PDF print job.
 *
 * All fields have sensible defaults — the simplest usage is:
 *
 *   PrintOptions opts = new PrintOptions.Builder("report.pdf").build();
 *   // prints to the system default printer, all pages, 1 copy, fit-to-page
 *
 * Full example:
 *
 *   PrintOptions opts = new PrintOptions.Builder("report.pdf")
 *       .printerName("HP LaserJet 400")
 *       .startPage(2)
 *       .endPage(5)
 *       .copies(2)
 *       .sides(Sides.DUPLEX_LONG_EDGE)
 *       .scaling(ScalingMode.FIT)
 *       .silent(true)
 *       .password("secret")
 *       .build();
 */
public class PrintOptions {

    // -----------------------------------------------------------------------
    // Enums
    // -----------------------------------------------------------------------

    /**
     * Controls how each PDF page is scaled to fit the physical paper.
     *
     *   FIT        — scale up or down so the page fills the printable area
     *                (may crop if aspect ratios differ significantly)
     *   SHRINK     — scale down only if the page is larger than the paper;
     *                never enlarge. Good for preserving exact dimensions.
     *   ACTUAL_SIZE — print at 72dpi (1 PDF point = 1/72 inch), no scaling.
     *                 A4-sized PDF pages will print at actual A4 size.
     */
    public enum ScalingMode {
        FIT,
        SHRINK,
        ACTUAL_SIZE
    }

    /**
     * Duplex (double-sided) printing mode.
     *
     *   SIMPLEX           — single-sided (default)
     *   DUPLEX_LONG_EDGE  — double-sided, flip on long edge (portrait binding)
     *   DUPLEX_SHORT_EDGE — double-sided, flip on short edge (landscape binding)
     */
    public enum Sides {
        SIMPLEX,
        DUPLEX_LONG_EDGE,
        DUPLEX_SHORT_EDGE
    }

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final String      pdfPath;
    private final String      password;      // null = no password
    private final String      printerName;   // null = system default printer
    private final int         startPage;     // 1-based; -1 = first page
    private final int         endPage;       // 1-based; -1 = last page
    private final int         copies;
    private final Sides       sides;
    private final ScalingMode scaling;

    /**
     * When true, print without showing a print dialog.
     * When false (default), the system print dialog is shown, allowing the
     * user to confirm settings before printing.
     */
    private final boolean     silent;

    /**
     * Job name shown in the OS print queue and on the printer's front panel.
     * Defaults to the PDF filename stem.
     */
    private final String      jobName;

    private PrintOptions(Builder b) {
        this.pdfPath     = b.pdfPath;
        this.password    = b.password;
        this.printerName = b.printerName;
        this.startPage   = b.startPage;
        this.endPage     = b.endPage;
        this.copies      = b.copies;
        this.sides       = b.sides;
        this.scaling     = b.scaling;
        this.silent      = b.silent;
        this.jobName     = b.jobName != null ? b.jobName : stemOf(b.pdfPath);
    }

    public String      getPdfPath()     { return pdfPath; }
    public String      getPassword()    { return password; }
    public String      getPrinterName() { return printerName; }
    public int         getStartPage()   { return startPage; }
    public int         getEndPage()     { return endPage; }
    public int         getCopies()      { return copies; }
    public Sides       getSides()       { return sides; }
    public ScalingMode getScaling()     { return scaling; }
    public boolean     isSilent()       { return silent; }
    public String      getJobName()     { return jobName; }

    private static String stemOf(String path) {
        String name = path.replaceAll(".*[\\\\/]", "");
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Override
    public String toString() {
        return String.format(
            "PrintOptions[pdf=%s, printer=%s, pages=%s-%s, copies=%d, " +
            "sides=%s, scaling=%s, silent=%b%s]",
            pdfPath,
            printerName != null ? printerName : "default",
            startPage < 0 ? "first" : startPage,
            endPage   < 0 ? "last"  : endPage,
            copies, sides, scaling, silent,
            password != null ? ", password=***" : "");
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private final String pdfPath;
        private String      password    = null;
        private String      printerName = null;    // null = default printer
        private int         startPage   = -1;
        private int         endPage     = -1;
        private int         copies      = 1;
        private Sides       sides       = Sides.SIMPLEX;
        private ScalingMode scaling     = ScalingMode.FIT;
        private boolean     silent      = false;
        private String      jobName     = null;    // null = auto from filename

        /** @param pdfPath path to the PDF file to print (required) */
        public Builder(String pdfPath) {
            this.pdfPath = pdfPath;
        }

        /** Password for an encrypted PDF. Null for unprotected files. */
        public Builder password(String v)    { this.password    = v; return this; }

        /**
         * Exact printer name as reported by the OS.
         * Use PdfPrinter.listPrinters() to see available names.
         * Null (default) uses the system default printer.
         */
        public Builder printerName(String v) { this.printerName = v; return this; }

        /** First page to print, 1-based (default: 1). */
        public Builder startPage(int v)      { this.startPage   = v; return this; }

        /** Last page to print, 1-based (default: last page). */
        public Builder endPage(int v)        { this.endPage     = v; return this; }

        /** Number of copies to print (default: 1). */
        public Builder copies(int v)         { this.copies      = v; return this; }

        /** Simplex or duplex mode (default: SIMPLEX). */
        public Builder sides(Sides v)        { this.sides       = v; return this; }

        /** How PDF pages are scaled to fit paper (default: FIT). */
        public Builder scaling(ScalingMode v){ this.scaling     = v; return this; }

        /**
         * When true, send job silently without showing a print dialog (default: false).
         * Set to true for server-side / batch printing.
         */
        public Builder silent(boolean v)     { this.silent      = v; return this; }

        /** Job name shown in the OS print queue (default: PDF filename stem). */
        public Builder jobName(String v)     { this.jobName     = v; return this; }

        public PrintOptions build() {
            if (pdfPath == null || pdfPath.isBlank())
                throw new IllegalStateException("PrintOptions: pdfPath must not be blank");
            if (copies < 1)
                throw new IllegalStateException("PrintOptions: copies must be >= 1");
            return new PrintOptions(this);
        }
    }
}
