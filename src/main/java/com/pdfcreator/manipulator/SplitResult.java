package com.pdfcreator.manipulator;

import java.util.Collections;
import java.util.List;

/**
 * Immutable result of a PDF split operation.
 *
 * Contains the list of output file paths written and the page count of each,
 * in the same order as the split produced them.
 *
 * Usage:
 *
 *   SplitResult result = manipulator.split(opts);
 *   System.out.println("Produced " + result.getOutputCount() + " files:");
 *   result.getOutputPaths().forEach(System.out::println);
 */
public class SplitResult {

    private final List<String> outputPaths;    // absolute or relative paths of written files
    private final List<Integer> pageCounts;    // pages in each output file (parallel with outputPaths)
    private final int totalPagesProcessed;     // total pages from the source document that were split
    private final String sourcePath;

    private SplitResult(Builder b) {
        this.outputPaths          = Collections.unmodifiableList(b.outputPaths);
        this.pageCounts           = Collections.unmodifiableList(b.pageCounts);
        this.totalPagesProcessed  = b.totalPagesProcessed;
        this.sourcePath           = b.sourcePath;
    }

    /** Paths of all output PDF files written, in split order. */
    public List<String>  getOutputPaths()         { return outputPaths; }

    /** Page count of each output file, in the same order as getOutputPaths(). */
    public List<Integer> getPageCounts()          { return pageCounts; }

    /** Number of output files produced. */
    public int           getOutputCount()         { return outputPaths.size(); }

    /** Total number of source pages that were distributed across the output files. */
    public int           getTotalPagesProcessed() { return totalPagesProcessed; }

    /** Path of the source PDF that was split. */
    public String        getSourcePath()          { return sourcePath; }

    @Override
    public String toString() {
        return String.format(
            "SplitResult[source=%s, outputs=%d, totalPages=%d]",
            sourcePath, outputPaths.size(), totalPagesProcessed);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private List<String>  outputPaths         = List.of();
        private List<Integer> pageCounts          = List.of();
        private int           totalPagesProcessed = 0;
        private String        sourcePath          = "";

        public Builder outputPaths(List<String> v)  { this.outputPaths         = v; return this; }
        public Builder pageCounts(List<Integer> v)   { this.pageCounts          = v; return this; }
        public Builder totalPagesProcessed(int v)    { this.totalPagesProcessed = v; return this; }
        public Builder sourcePath(String v)          { this.sourcePath          = v; return this; }

        public SplitResult build() { return new SplitResult(this); }
    }
}
