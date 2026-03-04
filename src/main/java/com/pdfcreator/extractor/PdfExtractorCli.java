package com.pdfcreator.extractor;

import java.io.IOException;
import java.io.File;
import java.util.List;
import com.pdfcreator.extractor.PasswordRequiredException;
import java.util.Map;

/**
 * CLI handler for --extract mode.
 *
 * Called from PdfCreator.main() when the --extract flag is present.
 * Parses extraction-specific arguments, delegates to PdfTextExtractor,
 * and writes or prints the result.
 *
 * Supported flags:
 *
 *   --input  <path>         PDF file to extract from (required)
 *   --output <path>         Write extracted text to this file (default: print to stdout)
 *   --start-page <n>        First page to extract, 1-based (default: 1)
 *   --end-page   <n>        Last page to extract, 1-based (default: last page)
 *   --no-sort               Disable position-based text sorting
 *   --strip-whitespace      Collapse multiple whitespace into a single space
 *   --no-metadata           Suppress the metadata header block from output
 *   --no-per-page           Print flat full text; omit per-page separators
 *
 * CLI Examples:
 *
 *   # Print all text from a PDF to stdout
 *   java -jar pdf-creator.jar --extract --input report.pdf
 *
 *   # Extract pages 2-5, write to file
 *   java -jar pdf-creator.jar --extract \
 *       --input report.pdf --output report.txt \
 *       --start-page 2 --end-page 5
 *
 *   # Normalised whitespace, no metadata header
 *   java -jar pdf-creator.jar --extract \
 *       --input report.pdf --strip-whitespace --no-metadata
 *
 *   # Extract metadata only (no per-page text, no sorting overhead)
 *   java -jar pdf-creator.jar --extract \
 *       --input report.pdf --no-per-page --no-sort
 */
public class PdfExtractorCli {

    private final PdfTextExtractor  extractor      = new PdfTextExtractor();
    private final PdfImageExtractor  imageExtractor = new PdfImageExtractor();

    /**
     * Entry point called from PdfCreator.main() when --extract flag is detected.
     *
     * @param args full argument array passed to main()
     * @throws IOException if the PDF cannot be read or output file cannot be written
     */
    public void run(String[] args) throws IOException {
        if (hasFlag(args, "--extract-images")) {
            runImageExtraction(args);
        } else {
            runTextExtraction(args);
        }
    }

    /** Handles --extract-images mode — saves images to a directory. */
    private void runImageExtraction(String[] args) throws IOException {

        // ---- IMAGE EXTRACTION ----
        String inputPath = getArg(args, "--input", null);
        if (inputPath == null) {
            System.err.println("Error: --extract-images requires --input <pdf-path>");
            System.exit(1);
        }

        String outputDir = getArg(args, "--output-dir", "extracted-images/");
        String imgFmt    = getArg(args, "--img-format", "png");
        int minW         = intArg(args, "--min-width",  10);
        int minH         = intArg(args, "--min-height", 10);
        int startPage    = intArg(args, "--start-page", -1);
        int endPage      = intArg(args, "--end-page",   -1);

        String imgPassword = getArg(args, "--password", null);

        ImageExtractionOptions opts = new ImageExtractionOptions.Builder()
            .startPage(startPage)
            .endPage(endPage)
            .minWidth(minW)
            .minHeight(minH)
            .preferredFormat(imgFmt)
            .password(imgPassword)
            .build();

        System.out.printf("Mode       : extract-images%n");
        System.out.printf("Input      : %s%n", inputPath);
        System.out.printf("Output dir : %s%n", outputDir);
        System.out.printf("Options    : %s%n%n", opts);

        List<ExtractedImage> images;
        try {
            images = imageExtractor.extractToDirectory(inputPath, outputDir, opts);
        } catch (PasswordRequiredException e) {
            System.err.println();
            System.err.println("Error: " + e.getMessage());
            System.err.println(e.wasPasswordProvided()
                ? "  Hint: the supplied password is incorrect."
                : "  Hint: use --password <password> to supply the PDF password.");
            System.exit(1);
            return;
        }

        printImageSummary(images, outputDir);
    }

    /** Handles --extract (text) mode. */
    private void runTextExtraction(String[] args) throws IOException {
        String inputPath  = getArg(args, "--input",  null);
        String outputPath = getArg(args, "--output", null);

        if (inputPath == null) {
            System.err.println("Error: --extract requires --input <pdf-path>");
            System.err.println("       Example: java -jar pdf-creator.jar --extract --input report.pdf");
            System.exit(1);
        }

        String txtPassword = getArg(args, "--password", null);

        ExtractionOptions options = new ExtractionOptions.Builder()
            .startPage(intArg(args, "--start-page", -1))
            .endPage(intArg(args, "--end-page",   -1))
            .sortByPosition(!hasFlag(args, "--no-sort"))
            .stripExtraWhitespace(hasFlag(args, "--strip-whitespace"))
            .includeMetadata(!hasFlag(args, "--no-metadata"))
            .extractPerPage(!hasFlag(args, "--no-per-page"))
            .password(txtPassword)
            .build();

        System.out.printf("Mode    : extract%n");
        System.out.printf("Input   : %s%n", inputPath);
        System.out.printf("Output  : %s%n", outputPath != null ? outputPath : "stdout");
        System.out.printf("Options : %s%n%n", options);

        ExtractionResult result;

        try {
            if (outputPath != null) {
                result = extractor.extractToFile(inputPath, outputPath, options);
            } else {
                result = extractor.extract(inputPath, options);
                extractor.printResult(result);
            }
        } catch (PasswordRequiredException e) {
            System.err.println();
            System.err.println("Error: " + e.getMessage());
            System.err.println(e.wasPasswordProvided()
                ? "  Hint: the supplied password is incorrect."
                : "  Hint: use --password <password> to supply the PDF password.");
            System.exit(1);
            return;
        }

        printSummary(result, outputPath);
    }

    // -----------------------------------------------------------------------
    // Private
    // -----------------------------------------------------------------------

    private void printImageSummary(List<ExtractedImage> images, String outputDir) {
        System.out.println();
        System.out.println("=".repeat(55));
        System.out.println("  Image extraction complete");
        System.out.println("=".repeat(55));
        System.out.printf("  Images found : %d%n", images.size());
        if (!images.isEmpty()) {
            System.out.printf("  Output dir   : %s%n", outputDir);
            int totalBytes = images.stream().mapToInt(ExtractedImage::getSizeBytes).sum();
            System.out.printf("  Total size   : %,d bytes%n", totalBytes);
            System.out.println("  Files:");
            for (ExtractedImage img : images)
                System.out.printf("    %s  (%dx%d, %,d bytes)%n",
                    img.getSuggestedName(), img.getWidth(),
                    img.getHeight(), img.getSizeBytes());
        }
        System.out.println("=".repeat(55));
    }

    private void printSummary(ExtractionResult result, String outputPath) {
        System.out.println();
        System.out.println("=".repeat(55));
        System.out.println("  Extraction complete");
        System.out.println("=".repeat(55));
        System.out.printf("  Source      : %s%n", result.getSourcePath());
        System.out.printf("  Total pages : %d%n", result.getPageCount());
        System.out.printf("  Words       : ~%d%n", result.getWordCount());
        System.out.printf("  Characters  : %d%n",  result.getCharCount());

        if (!result.getMetadata().isEmpty()) {
            System.out.printf("  Metadata    : %d field(s)%n", result.getMetadata().size());
            for (Map.Entry<String, String> e : result.getMetadata().entrySet())
                System.out.printf("    %-20s %s%n", e.getKey() + ":", e.getValue());
        }

        if (outputPath != null)
            System.out.printf("  Written to  : %s%n", outputPath);

        System.out.println("=".repeat(55));
    }

    private static String getArg(String[] args, String flag, String def) {
        for (int i = 0; i < args.length - 1; i++)
            if (args[i].equals(flag)) return args[i + 1];
        return def;
    }

    private static int intArg(String[] args, String flag, int def) {
        String val = getArg(args, flag, null);
        if (val == null) return def;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            System.err.println("Warning: invalid integer for " + flag + " ('" + val + "') — using default " + def);
            return def;
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) if (a.equals(flag)) return true;
        return false;
    }
}
