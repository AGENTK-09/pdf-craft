package com.pdfcreator.manipulator;

import com.pdfcreator.extractor.PasswordRequiredException;

import java.io.IOException;
import java.util.List;

/**
 * CLI handler for --merge and --split modes.
 *
 * Called from PdfCreator.main() when --merge or --split is present.
 * Dispatches internally based on which flag is detected.
 *
 * ── MERGE FLAGS ───────────────────────────────────────────────────────────
 *
 *   --merge
 *   --inputs <p1,p2,...>    Comma-separated list of PDF paths to merge (required)
 *   --output <path>         Output PDF path (default: merged.pdf)
 *   --passwords <pw1,pw2>   Comma-separated passwords aligned with --inputs
 *                           Use empty string for unprotected files: ",,secret,,"
 *   --no-copy-meta          Do not copy metadata from the first input
 *
 * Examples:
 *
 *   # Merge three PDFs
 *   java -jar pdf-creator.jar --merge \
 *     --inputs jan.pdf,feb.pdf,mar.pdf --output q1.pdf
 *
 *   # Merge with mixed password-protected inputs
 *   java -jar pdf-creator.jar --merge \
 *     --inputs open.pdf,protected.pdf --passwords ,secret123 --output merged.pdf
 *
 * ── SPLIT FLAGS ───────────────────────────────────────────────────────────
 *
 *   --split
 *   --input <path>          PDF file to split (required)
 *   --output-dir <path>     Directory for output files (default: split-output/)
 *   --password <pwd>        Password if the source PDF is encrypted
 *   --prefix <name>         Filename prefix for output files (default: input stem)
 *
 *   Strategy flags (exactly one required):
 *   --every-n-pages <n>     Split into chunks of N pages each
 *   --into-parts <n>        Divide into N roughly equal parts
 *   --page-ranges <ranges>  Explicit ranges: "1-3,4-6,7-10"
 *
 * Examples:
 *
 *   # Split into chunks of 5 pages
 *   java -jar pdf-creator.jar --split \
 *     --input report.pdf --every-n-pages 5 --output-dir split/
 *
 *   # Split into 4 roughly equal parts
 *   java -jar pdf-creator.jar --split \
 *     --input report.pdf --into-parts 4 --output-dir split/
 *
 *   # Split at explicit page boundaries
 *   java -jar pdf-creator.jar --split \
 *     --input report.pdf --page-ranges "1-3,4-6,7-10" --output-dir split/
 *
 *   # Split a password-protected PDF
 *   java -jar pdf-creator.jar --split \
 *     --input protected.pdf --password secret --every-n-pages 3 --output-dir split/
 */
public class PdfManipulatorCli {

    private final PdfManipulator manipulator = new PdfManipulator();

    /** Entry point called from PdfCreator.main(). */
    public void run(String[] args) throws IOException {
        if (hasFlag(args, "--merge")) {
            runMerge(args);
        } else {
            runSplit(args);
        }
    }

    // -----------------------------------------------------------------------
    // Merge
    // -----------------------------------------------------------------------

    private void runMerge(String[] args) throws IOException {
        String inputsCsv   = getArg(args, "--inputs",    null);
        String outputPath  = getArg(args, "--output",    "merged.pdf");
        String passwordCsv = getArg(args, "--passwords", null);
        boolean copyMeta   = !hasFlag(args, "--no-copy-meta");

        if (inputsCsv == null) {
            System.err.println("Error: --merge requires --inputs <path1,path2,...>");
            System.exit(1);
        }

        String[] paths     = inputsCsv.split(",", -1);
        String[] passwords = passwordCsv != null
            ? passwordCsv.split(",", -1)
            : new String[paths.length];

        if (paths.length < 2) {
            System.err.println("Error: --merge requires at least 2 input files.");
            System.exit(1);
        }

        MergeOptions.Builder builder = new MergeOptions.Builder()
            .output(outputPath)
            .copyMetadataFromFirst(copyMeta);

        for (int i = 0; i < paths.length; i++) {
            String path = paths[i].trim();
            String pwd  = (i < passwords.length && passwords[i] != null
                            && !passwords[i].trim().isEmpty())
                          ? passwords[i].trim() : null;
            builder.addInput(path, pwd);
        }

        MergeOptions opts = builder.build();

        System.out.printf("Mode    : merge%n");
        System.out.printf("Inputs  : %d file(s)%n", paths.length);
        for (int i = 0; i < paths.length; i++)
            System.out.printf("  [%d] %s%s%n", i + 1, paths[i].trim(),
                (i < passwords.length && passwords[i] != null
                    && !passwords[i].trim().isEmpty()) ? " (password-protected)" : "");
        System.out.printf("Output  : %s%n", outputPath);
        System.out.printf("CopyMeta: %b%n%n", copyMeta);

        try {
            manipulator.merge(opts);
            System.out.printf("%nMerge complete → %s%n", outputPath);
        } catch (PasswordRequiredException e) {
            System.err.println("\nError: " + e.getMessage());
            System.err.println(e.wasPasswordProvided()
                ? "  Hint: the supplied password is incorrect."
                : "  Hint: use --passwords to supply passwords for protected inputs.");
            System.exit(1);
        }
    }

    // -----------------------------------------------------------------------
    // Split
    // -----------------------------------------------------------------------

    private void runSplit(String[] args) throws IOException {
        String inputPath  = getArg(args, "--input",      null);
        String outputDir  = getArg(args, "--output-dir", "split-output/");
        String password   = getArg(args, "--password",   null);
        String prefix     = getArg(args, "--prefix",     null);

        if (inputPath == null) {
            System.err.println("Error: --split requires --input <pdf-path>");
            System.exit(1);
        }

        // Exactly one strategy flag must be present
        String everyN      = getArg(args, "--every-n-pages", null);
        String intoParts   = getArg(args, "--into-parts",    null);
        String pageRanges  = getArg(args, "--page-ranges",   null);

        int strategyCount = (everyN != null ? 1 : 0)
                          + (intoParts != null ? 1 : 0)
                          + (pageRanges != null ? 1 : 0);
        if (strategyCount == 0) {
            System.err.println("Error: --split requires one of: " +
                "--every-n-pages, --into-parts, --page-ranges");
            System.exit(1);
        }
        if (strategyCount > 1) {
            System.err.println("Error: --split accepts only one strategy flag.");
            System.exit(1);
        }

        SplitOptions.Builder builder = new SplitOptions.Builder(inputPath, outputDir);
        if (password != null) builder.password(password);
        if (prefix   != null) builder.filenamePrefix(prefix);

        String strategyDesc;
        if (everyN != null) {
            int n = parsePositiveInt("--every-n-pages", everyN);
            builder.everyNPages(n);
            strategyDesc = "every " + n + " pages";
        } else if (intoParts != null) {
            int n = parsePositiveInt("--into-parts", intoParts);
            builder.intoParts(n);
            strategyDesc = "into " + n + " parts";
        } else {
            int[][] ranges = parsePageRanges(pageRanges);
            builder.byPageRanges(ranges);
            strategyDesc = ranges.length + " explicit range(s)";
        }

        SplitOptions opts = builder.build();

        System.out.printf("Mode      : split%n");
        System.out.printf("Input     : %s%s%n", inputPath,
            password != null ? " (password-protected)" : "");
        System.out.printf("Strategy  : %s%n", strategyDesc);
        System.out.printf("Output dir: %s%n%n", outputDir);

        try {
            SplitResult result = manipulator.split(opts);
            printSplitSummary(result);
        } catch (PasswordRequiredException e) {
            System.err.println("\nError: " + e.getMessage());
            System.err.println(e.wasPasswordProvided()
                ? "  Hint: the supplied password is incorrect."
                : "  Hint: use --password <pwd> to supply the PDF password.");
            System.exit(1);
        }
    }

    private void printSplitSummary(SplitResult result) {
        System.out.println();
        System.out.println("=".repeat(55));
        System.out.println("  Split complete");
        System.out.println("=".repeat(55));
        System.out.printf("  Source         : %s%n", result.getSourcePath());
        System.out.printf("  Total pages    : %d%n", result.getTotalPagesProcessed());
        System.out.printf("  Output files   : %d%n", result.getOutputCount());
        for (int i = 0; i < result.getOutputPaths().size(); i++) {
            System.out.printf("    [%d] %s  (%d page%s)%n",
                i + 1,
                result.getOutputPaths().get(i),
                result.getPageCounts().get(i),
                result.getPageCounts().get(i) == 1 ? "" : "s");
        }
        System.out.println("=".repeat(55));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Parses a page-ranges string like "1-3,4-6,7-10" into a 2D int array.
     * Each element is {startPage, endPage} (1-based, inclusive).
     */
    private static int[][] parsePageRanges(String rangesStr) {
        String[] parts = rangesStr.split(",");
        int[][] result = new int[parts.length][2];
        for (int i = 0; i < parts.length; i++) {
            String[] bounds = parts[i].trim().split("-");
            if (bounds.length != 2)
                throw new IllegalArgumentException(
                    "Invalid page range '" + parts[i].trim() +
                    "'. Expected format: start-end (e.g. 1-3)");
            try {
                result[i][0] = Integer.parseInt(bounds[0].trim());
                result[i][1] = Integer.parseInt(bounds[1].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "Non-integer page number in range: '" + parts[i].trim() + "'");
            }
            if (result[i][0] < 1 || result[i][0] > result[i][1])
                throw new IllegalArgumentException(
                    "Invalid range " + result[i][0] + "-" + result[i][1] +
                    ": start must be >= 1 and <= end");
        }
        return result;
    }

    private static int parsePositiveInt(String flag, String value) {
        try {
            int n = Integer.parseInt(value.trim());
            if (n < 1) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) {
            System.err.println("Error: " + flag + " must be a positive integer, got: " + value);
            System.exit(1);
            return -1; // unreachable
        }
    }

    private static String getArg(String[] args, String flag, String def) {
        for (int i = 0; i < args.length - 1; i++)
            if (args[i].equals(flag)) return args[i + 1];
        return def;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) if (a.equals(flag)) return true;
        return false;
    }
}
