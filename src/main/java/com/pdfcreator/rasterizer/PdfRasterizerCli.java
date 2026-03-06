package com.pdfcreator.rasterizer;

import java.io.IOException;

/**
 * CLI handler for the --rasterize mode.
 *
 * Called from PdfCreator.main() when the --rasterize flag is present.
 *
 * ── FLAGS ─────────────────────────────────────────────────────────────────
 *
 *   --rasterize                    (required) activates this mode
 *   --input   <path>               source PDF file (required)
 *   --output-dir <path>            directory for image files (required)
 *   --format  <png|jpeg>           output image format (default: png)
 *   --dpi     <number>             rendering resolution (default: 150)
 *   --jpeg-quality <0.0-1.0>       JPEG compression quality (default: 0.85)
 *   --start-page <n>               first page to render, 1-based (default: 1)
 *   --end-page   <n>               last page to render, 1-based (default: last)
 *   --file-prefix <name>           prefix for output filenames (default: page)
 *   --password <pwd>               password for encrypted PDFs
 *
 * ── EXAMPLES ──────────────────────────────────────────────────────────────
 *
 *   # Render all pages to PNG at 150 DPI
 *   java -jar pdf-creator.jar --rasterize \
 *       --input report.pdf \
 *       --output-dir report-pages/
 *
 *   # Render all pages to PNG at 300 DPI (print quality)
 *   java -jar pdf-creator.jar --rasterize \
 *       --input report.pdf \
 *       --output-dir report-pages/ \
 *       --dpi 300
 *
 *   # Render pages 2-5 to JPEG at 200 DPI, quality 90%
 *   java -jar pdf-creator.jar --rasterize \
 *       --input report.pdf \
 *       --output-dir report-pages/ \
 *       --format jpeg \
 *       --dpi 200 \
 *       --jpeg-quality 0.90 \
 *       --start-page 2 \
 *       --end-page 5
 *
 *   # Single page (page 1) as PNG thumbnail, custom prefix
 *   java -jar pdf-creator.jar --rasterize \
 *       --input report.pdf \
 *       --output-dir thumbnails/ \
 *       --start-page 1 --end-page 1 \
 *       --dpi 72 \
 *       --file-prefix thumb
 *
 *   # Encrypted PDF
 *   java -jar pdf-creator.jar --rasterize \
 *       --input protected.pdf \
 *       --output-dir pages/ \
 *       --password secret
 *
 * ── OUTPUT FILE NAMING ────────────────────────────────────────────────────
 *
 *   Files are named: <file-prefix>-<pageNumber>.<ext>
 *   Page numbers are zero-padded to at least 3 digits.
 *
 *   Examples:
 *     page-001.png
 *     page-002.png
 *     thumb-001.jpg
 */
public class PdfRasterizerCli {

    private final PdfRasterizer rasterizer = new PdfRasterizer();

    /** Entry point called from PdfCreator.main(). */
    public void run(String[] args) throws IOException {

        String input      = requireArg(args, "--input",      "--rasterize");
        String outputDir  = requireArg(args, "--output-dir", "--rasterize");
        String formatStr  = getArg(args, "--format",       "png");
        float  dpi        = floatArg(args, "--dpi",         150f);
        float  quality    = floatArg(args, "--jpeg-quality", 0.85f);
        int    startPage  = intArg(args,   "--start-page",  -1);
        int    endPage    = intArg(args,   "--end-page",    -1);
        String filePrefix = getArg(args, "--file-prefix",  "page");
        String password   = getArg(args, "--password",     null);

        RasterOptions.Format format;
        try {
            format = RasterOptions.Format.parse(formatStr);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
            return;
        }

        RasterOptions opts = new RasterOptions.Builder()
            .inputPath(input)
            .outputDir(outputDir)
            .format(format)
            .dpi(dpi)
            .jpegQuality(quality)
            .startPage(startPage)
            .endPage(endPage)
            .filePrefix(filePrefix)
            .password(password)
            .build();

        // Print a header
        System.out.println("Mode           : rasterize");
        System.out.printf("Input          : %s%n", input);
        System.out.printf("Output dir     : %s%n", outputDir);
        System.out.printf("Format         : %s%n", format);
        System.out.printf("DPI            : %.0f%n", dpi);
        if (format == RasterOptions.Format.JPEG)
            System.out.printf("JPEG quality   : %.0f%%%n", quality * 100);
        System.out.printf("Pages          : %s – %s%n",
            startPage < 0 ? "first" : String.valueOf(startPage),
            endPage   < 0 ? "last"  : String.valueOf(endPage));
        System.out.printf("File prefix    : %s%n", filePrefix);
        System.out.println();

        try {
            RasterResult result = rasterizer.rasterize(opts);
            System.out.println(result.getSummary());
        } catch (com.pdfcreator.extractor.PasswordRequiredException e) {
            System.err.println("\nError: " + e.getMessage());
            System.err.println("  Hint: supply --password <pwd> for encrypted PDFs.");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("\nError: " + e.getMessage());
            System.exit(1);
        }
    }

    // -----------------------------------------------------------------------
    // CLI argument helpers
    // -----------------------------------------------------------------------

    private static String requireArg(String[] args, String flag, String op) {
        String val = getArg(args, flag, null);
        if (val == null) {
            System.err.println("Error: " + op + " requires " + flag);
            System.exit(1);
        }
        return val;
    }

    private static String getArg(String[] args, String flag, String def) {
        for (int i = 0; i < args.length - 1; i++)
            if (args[i].equals(flag)) return args[i + 1];
        return def;
    }

    private static int intArg(String[] args, String flag, int def) {
        String v = getArg(args, flag, null);
        if (v == null) return def;
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private static float floatArg(String[] args, String flag, float def) {
        String v = getArg(args, flag, null);
        if (v == null) return def;
        try { return Float.parseFloat(v.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) if (a.equals(flag)) return true;
        return false;
    }
}
