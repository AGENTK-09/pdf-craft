package com.pdfcreator.htmlconverter;

import java.io.IOException;

/**
 * CLI handler for --html-to-pdf mode.
 *
 * Called from PdfCreator.main() when the --html-to-pdf flag is present.
 * Parses all relevant flags from args and delegates to HtmlToPdfConverter.
 *
 * ── FLAGS ─────────────────────────────────────────────────────────────────
 *
 *   --html-to-pdf               (required) activates this mode
 *   --input <path>              source HTML file (required)
 *   --output <path>             output PDF file path (required)
 *   --tool-config <path>        properties file with wkhtmltopdf.path
 *                               (default: pdf-creator.properties next to JAR or in cwd)
 *   --base-url <url>            base URL for relative CSS/image paths
 *                               (default: input file's parent directory — handled by wkhtmltopdf)
 *   --page-size <size>          A4 | Letter | Legal | A3 | A5 etc. (default: A4)
 *   --orientation <o>           Portrait | Landscape (default: Portrait)
 *   --margin-top <mm>           top margin in mm (default: 10)
 *   --margin-bottom <mm>        bottom margin in mm (default: 10)
 *   --margin-left <mm>          left margin in mm (default: 10)
 *   --margin-right <mm>         right margin in mm (default: 10)
 *   --zoom <factor>             zoom factor, e.g. 0.8 or 1.25 (default: 1.0)
 *   --javascript                enable JavaScript execution (default: off)
 *   --javascript-delay <ms>     wait N ms after load before capture (default: 0)
 *   --print-media-type          use @media print CSS (default: @media screen)
 *   --user-style-sheet <path>   inject extra CSS file on top of page styles
 *   --no-images                 suppress image loading and embedding
 *   --title <text>              override PDF title metadata (default: from <title> tag)
 *   --author <text>             PDF author metadata
 *   --subject <text>            PDF subject metadata
 *
 * ── EXAMPLES ──────────────────────────────────────────────────────────────
 *
 *   # Basic conversion
 *   java -jar pdf-creator.jar --html-to-pdf \
 *       --input  invoice.html \
 *       --output invoice.pdf
 *
 *   # Specify wkhtmltopdf location via properties file (Windows)
 *   java -jar pdf-creator.jar --html-to-pdf \
 *       --input       invoice.html \
 *       --output      invoice.pdf \
 *       --tool-config C:/myapp/pdf-creator.properties
 *
 *   # Letter size, landscape, wider margins
 *   java -jar pdf-creator.jar --html-to-pdf \
 *       --input       report.html \
 *       --output      report.pdf \
 *       --page-size   Letter \
 *       --orientation Landscape \
 *       --margin-top  20 --margin-bottom 20 \
 *       --margin-left 25 --margin-right 25
 *
 *   # Use @media print CSS, set PDF metadata
 *   java -jar pdf-creator.jar --html-to-pdf \
 *       --input           statement.html \
 *       --output          statement.pdf \
 *       --print-media-type \
 *       --title           "March 2026 Statement" \
 *       --author          "First National Bank" \
 *       --subject         "Monthly Account Statement"
 *
 *   # JS-rendered content (e.g. chart.js graphs)
 *   java -jar pdf-creator.jar --html-to-pdf \
 *       --input            dashboard.html \
 *       --output           dashboard.pdf \
 *       --javascript \
 *       --javascript-delay 2000
 *
 *   # Inject print override CSS (e.g. hide nav, reset backgrounds)
 *   java -jar pdf-creator.jar --html-to-pdf \
 *       --input            page.html \
 *       --output           page.pdf \
 *       --user-style-sheet print-overrides.css
 */
public class HtmlConverterCli {

    private final HtmlToPdfConverter converter = new HtmlToPdfConverter();

    /** Entry point called from PdfCreator.main(). */
    public void run(String[] args) throws IOException {

        // ── Parse flags ──────────────────────────────────────────────────────
        String  toolConfigPath   = getArg(args,     "--tool-config",      null);
        String  input            = requireArg(args, "--input",            "--html-to-pdf");
        String  output           = requireArg(args, "--output",           "--html-to-pdf");
        String  baseUrl          = getArg(args,     "--base-url",         null);
        String  pageSize         = getArg(args,     "--page-size",        "A4");
        String  orientation      = getArg(args,     "--orientation",      "Portrait");
        int     marginTop        = intArg(args,      "--margin-top",       10);
        int     marginBottom     = intArg(args,      "--margin-bottom",    10);
        int     marginLeft       = intArg(args,      "--margin-left",      10);
        int     marginRight      = intArg(args,      "--margin-right",     10);
        float   zoom             = floatArg(args,    "--zoom",             1.0f);
        boolean javascript       = hasFlag(args,     "--javascript");
        int     javascriptDelay  = intArg(args,      "--javascript-delay", 0);
        boolean printMedia       = hasFlag(args,     "--print-media-type");
        String  userStyleSheet   = getArg(args,      "--user-style-sheet", null);
        boolean noImages         = hasFlag(args,      "--no-images");
        String  title            = getArg(args,      "--title",            null);
        String  author           = getArg(args,      "--author",           null);
        String  subject          = getArg(args,      "--subject",          null);

        // ── Build options ────────────────────────────────────────────────────
        HtmlConversionOptions opts;
        try {
            opts = new HtmlConversionOptions.Builder()
                .inputPath(input)
                .outputPath(output)
                .baseUrl(baseUrl)
                .pageSize(pageSize)
                .orientation(orientation)
                .marginTop(marginTop)
                .marginBottom(marginBottom)
                .marginLeft(marginLeft)
                .marginRight(marginRight)
                .zoom(zoom)
                .enableJavascript(javascript)
                .javascriptDelayMs(javascriptDelay)
                .printMediaType(printMedia)
                .userStyleSheet(userStyleSheet)
                .loadImages(!noImages)
                .title(title)
                .author(author)
                .subject(subject)
                .build();
        } catch (IllegalStateException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
            return;
        }

        // ── Load tool config (wkhtmltopdf binary path) ───────────────────────
        ToolConfig toolConfig;
        try {
            toolConfig = (toolConfigPath != null)
                ? ToolConfig.load(toolConfigPath)
                : ToolConfig.loadDefault();
        } catch (java.io.FileNotFoundException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
            return;
        } catch (java.io.IOException e) {
            System.err.println("Error reading tool config: " + e.getMessage());
            System.exit(1);
            return;
        }

        // ── Print header ────────────────────────────────────────────────────
        System.out.println("Mode           : html-to-pdf");
        System.out.printf ("Renderer       : wkhtmltopdf%n");
        System.out.printf ("Tool config    : %s%n", toolConfig.getSourceDescription());
        System.out.printf ("Input          : %s%n", input);
        System.out.printf ("Output         : %s%n", output);
        System.out.printf ("Page size      : %s  %s%n", pageSize, orientation);
        System.out.printf ("Margins        : T=%dmm  B=%dmm  L=%dmm  R=%dmm%n",
            marginTop, marginBottom, marginLeft, marginRight);
        if (Math.abs(zoom - 1.0f) > 0.001f)
            System.out.printf("Zoom           : %.2f%n", zoom);
        if (javascript)
            System.out.printf("JavaScript     : enabled  (delay: %dms)%n", javascriptDelay);
        if (printMedia)
            System.out.println("CSS media      : print");
        if (userStyleSheet != null)
            System.out.printf("Style override : %s%n", userStyleSheet);
        if (noImages)
            System.out.println("Images         : disabled");
        System.out.println();

        // ── Convert ──────────────────────────────────────────────────────────
        try {
            HtmlConversionResult result = converter.convert(opts, toolConfig);
            System.out.println(result.getSummary());
            if (!result.getWarnings().isEmpty()) {
                System.out.println("Warnings from wkhtmltopdf:");
                result.getWarnings().forEach(w -> System.out.println("  " + w));
            }
        } catch (IllegalStateException e) {
            // wkhtmltopdf binary not found
            System.err.println("\nError: " + e.getMessage());
            System.exit(1);
        } catch (HtmlConversionException e) {
            System.err.println("\nError: wkhtmltopdf conversion failed.");
            System.err.println("  Exit code : " + e.getExitCode());
            if (!e.getStderrLines().isEmpty()) {
                System.err.println("  Output:");
                e.getStderrLines().stream().limit(30)
                    .forEach(l -> System.err.println("    " + l));
            }
            System.exit(1);
        } catch (IOException e) {
            System.err.println("\nError: " + e.getMessage());
            System.exit(1);
        }
    }

    // -----------------------------------------------------------------------
    // CLI argument helpers (same pattern as PdfRasterizerCli)
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
        catch (NumberFormatException e) {
            System.err.println("Warning: invalid integer for " + flag + ": '" + v +
                "' — using default " + def);
            return def;
        }
    }

    private static float floatArg(String[] args, String flag, float def) {
        String v = getArg(args, flag, null);
        if (v == null) return def;
        try { return Float.parseFloat(v.trim()); }
        catch (NumberFormatException e) {
            System.err.println("Warning: invalid number for " + flag + ": '" + v +
                "' — using default " + def);
            return def;
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) if (a.equals(flag)) return true;
        return false;
    }
}
