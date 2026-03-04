package com.pdfcreator.manipulator;

/**
 * Configuration for a PDF split operation.
 *
 * Three split strategies are supported. Choose one by calling the corresponding
 * factory method on the Builder.
 *
 * ── Strategy 1: BY_PAGE_RANGE ────────────────────────────────────────────
 * Split at explicit page boundaries. You define exactly which pages go into
 * each output file.
 *
 *   SplitOptions opts = new SplitOptions.Builder("report.pdf", "output/")
 *       .byPageRanges(new int[][]{ {1,3}, {4,6}, {7,10} })
 *       .build();
 *   // produces: output/report-part1.pdf (pages 1-3)
 *   //           output/report-part2.pdf (pages 4-6)
 *   //           output/report-part3.pdf (pages 7-10)
 *
 * ── Strategy 2: BY_EVERY_N_PAGES ─────────────────────────────────────────
 * Split into chunks of N pages each. The last chunk may be smaller.
 *
 *   SplitOptions opts = new SplitOptions.Builder("report.pdf", "output/")
 *       .everyNPages(3)
 *       .build();
 *   // a 10-page PDF produces:
 *   //   report-part1.pdf (pages 1-3)
 *   //   report-part2.pdf (pages 4-6)
 *   //   report-part3.pdf (pages 7-9)
 *   //   report-part4.pdf (page 10)
 *
 * ── Strategy 3: INTO_N_PARTS ──────────────────────────────────────────────
 * Divide into exactly N roughly equal parts. Pages are distributed as evenly
 * as possible; earlier parts get one extra page when the total doesn't divide
 * evenly.
 *
 *   SplitOptions opts = new SplitOptions.Builder("report.pdf", "output/")
 *       .intoParts(4)
 *       .build();
 *   // a 10-page PDF split into 4 parts: 3, 3, 2, 2 pages each
 */
public class SplitOptions {

    public enum Strategy {
        /** Explicit page ranges supplied by the caller. */
        BY_PAGE_RANGE,
        /** Fixed chunk size: one output file every N pages. */
        BY_EVERY_N_PAGES,
        /** Divide into exactly N roughly equal parts. */
        INTO_N_PARTS
    }

    private final String   inputPath;
    private final String   password;      // null = no password
    private final String   outputDir;
    private final String   filenamePrefix; // default = stem of inputPath
    private final Strategy strategy;

    // Strategy parameters — only the one matching strategy is used
    private final int[][]  pageRanges;    // used by BY_PAGE_RANGE
    private final int      chunkSize;     // used by BY_EVERY_N_PAGES
    private final int      partCount;     // used by INTO_N_PARTS

    private SplitOptions(Builder b) {
        this.inputPath      = b.inputPath;
        this.password       = b.password;
        this.outputDir      = b.outputDir;
        this.filenamePrefix = b.filenamePrefix != null
            ? b.filenamePrefix
            : stemOf(b.inputPath);
        this.strategy       = b.strategy;
        this.pageRanges     = b.pageRanges;
        this.chunkSize      = b.chunkSize;
        this.partCount      = b.partCount;
    }

    public String   getInputPath()      { return inputPath; }
    public String   getPassword()       { return password; }
    public String   getOutputDir()      { return outputDir; }
    public String   getFilenamePrefix() { return filenamePrefix; }
    public Strategy getStrategy()       { return strategy; }
    public int[][]  getPageRanges()     { return pageRanges; }
    public int      getChunkSize()      { return chunkSize; }
    public int      getPartCount()      { return partCount; }
    public boolean  hasPassword()       { return password != null && !password.isEmpty(); }

    /** Extracts the filename without extension: "path/to/report.pdf" → "report" */
    private static String stemOf(String path) {
        String name = path.replaceAll(".*[\\\\/]", "");
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Override
    public String toString() {
        String stratStr = switch (strategy) {
            case BY_PAGE_RANGE   -> "BY_PAGE_RANGE(" + (pageRanges != null ? pageRanges.length : 0) + " ranges)";
            case BY_EVERY_N_PAGES -> "BY_EVERY_N_PAGES(n=" + chunkSize + ")";
            case INTO_N_PARTS    -> "INTO_N_PARTS(n=" + partCount + ")";
        };
        return String.format("SplitOptions[input=%s, strategy=%s, outputDir=%s, prefix=%s%s]",
            inputPath, stratStr, outputDir, filenamePrefix,
            password != null ? ", password=***" : "");
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private final String inputPath;
        private final String outputDir;
        private String   password       = null;
        private String   filenamePrefix = null;   // null = auto from inputPath stem
        private Strategy strategy;
        private int[][]  pageRanges;
        private int      chunkSize;
        private int      partCount;

        /**
         * @param inputPath path to the PDF to split
         * @param outputDir directory where output files will be written
         */
        public Builder(String inputPath, String outputDir) {
            this.inputPath = inputPath;
            this.outputDir = outputDir;
        }

        /** Password for an encrypted source PDF. */
        public Builder password(String v) { this.password = v; return this; }

        /**
         * Custom filename prefix for output files.
         * Default: stem of the input filename ("report.pdf" → "report").
         * Output files will be named: "{prefix}-part1.pdf", "{prefix}-part2.pdf", …
         */
        public Builder filenamePrefix(String v) { this.filenamePrefix = v; return this; }

        /**
         * Strategy 1: split at explicit page boundaries.
         *
         * @param ranges 2D array where each element is {startPage, endPage} (1-based, inclusive).
         *               Example: {{1,3},{4,6},{7,10}}
         */
        public Builder byPageRanges(int[][] ranges) {
            this.strategy   = Strategy.BY_PAGE_RANGE;
            this.pageRanges = ranges;
            return this;
        }

        /**
         * Strategy 2: split into chunks of N pages.
         *
         * @param n pages per output file (must be >= 1)
         */
        public Builder everyNPages(int n) {
            this.strategy  = Strategy.BY_EVERY_N_PAGES;
            this.chunkSize = n;
            return this;
        }

        /**
         * Strategy 3: divide into exactly N roughly equal parts.
         *
         * @param n number of output files (must be >= 2)
         */
        public Builder intoParts(int n) {
            this.strategy  = Strategy.INTO_N_PARTS;
            this.partCount = n;
            return this;
        }

        public SplitOptions build() {
            if (inputPath == null || inputPath.isBlank())
                throw new IllegalStateException("SplitOptions: inputPath must not be blank");
            if (outputDir == null || outputDir.isBlank())
                throw new IllegalStateException("SplitOptions: outputDir must not be blank");
            if (strategy == null)
                throw new IllegalStateException(
                    "SplitOptions: call byPageRanges(), everyNPages(), or intoParts() to set a strategy");
            if (strategy == Strategy.BY_PAGE_RANGE && (pageRanges == null || pageRanges.length == 0))
                throw new IllegalStateException("SplitOptions: byPageRanges() requires at least one range");
            if (strategy == Strategy.BY_EVERY_N_PAGES && chunkSize < 1)
                throw new IllegalStateException("SplitOptions: everyNPages() chunk size must be >= 1");
            if (strategy == Strategy.INTO_N_PARTS && partCount < 2)
                throw new IllegalStateException("SplitOptions: intoParts() part count must be >= 2");
            return new SplitOptions(this);
        }
    }
}
