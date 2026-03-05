package com.pdfcreator.printer;

import com.pdfcreator.extractor.PasswordRequiredException;

import java.awt.print.PrinterException;
import java.io.IOException;
import java.util.List;

/**
 * CLI handler for --print mode.
 *
 * Called from PdfCreator.main() when --print is present.
 * Also handles --list-printers which has no PDF input.
 *
 * Flags:
 *
 *   --print
 *   --input <path>          PDF file to print (required unless --list-printers)
 *   --printer <name>        Printer name (default: system default printer)
 *   --start-page <n>        First page to print, 1-based (default: 1)
 *   --end-page <n>          Last page to print, 1-based (default: last)
 *   --copies <n>            Number of copies (default: 1)
 *   --sides <mode>          simplex | duplex-long | duplex-short (default: simplex)
 *   --scaling <mode>        fit | shrink | actual (default: fit)
 *   --silent                Print without showing the OS print dialog
 *   --password <pwd>        Password for encrypted PDFs
 *   --job-name <name>       Job name shown in OS print queue
 *
 *   --list-printers         List all available printers and exit (no --input needed)
 *
 * Examples:
 *
 *   # List available printers
 *   java -jar pdf-creator.jar --list-printers
 *
 *   # Print to default printer (shows dialog)
 *   java -jar pdf-creator.jar --print --input report.pdf
 *
 *   # Print silently to named printer
 *   java -jar pdf-creator.jar --print --input report.pdf \
 *     --printer "HP LaserJet 400" --silent
 *
 *   # Print pages 2-5, 2 copies, duplex
 *   java -jar pdf-creator.jar --print --input report.pdf \
 *     --start-page 2 --end-page 5 --copies 2 \
 *     --sides duplex-long --silent
 *
 *   # Print a password-protected PDF
 *   java -jar pdf-creator.jar --print --input protected.pdf \
 *     --password secret --silent
 */
public class PdfPrinterCli {

    private final PdfPrinter printer = new PdfPrinter();

    /** Entry point called from PdfCreator.main(). */
    public void run(String[] args) throws IOException {

        // --list-printers is a standalone flag — no input needed
        if (hasFlag(args, "--list-printers")) {
            runListPrinters();
            return;
        }

        runPrint(args);
    }

    // -----------------------------------------------------------------------
    // List printers
    // -----------------------------------------------------------------------

    private void runListPrinters() {
        List<String> printers = printer.listPrinters();
        String defaultName    = printer.getDefaultPrinterName();

        System.out.println("=".repeat(55));
        System.out.println("  Available Printers");
        System.out.println("=".repeat(55));

        if (printers.isEmpty()) {
            System.out.println("  No printers found.");
        } else {
            for (int i = 0; i < printers.size(); i++) {
                String name = printers.get(i);
                boolean isDefault = name.equals(defaultName);
                System.out.printf("  [%d] %s%s%n", i + 1, name,
                    isDefault ? "  <-- default" : "");
            }
        }

        System.out.println("=".repeat(55));
        System.out.printf("  Total: %d printer(s)%n", printers.size());
        if (defaultName != null)
            System.out.printf("  Default: %s%n", defaultName);
        System.out.println("=".repeat(55));
    }

    // -----------------------------------------------------------------------
    // Print
    // -----------------------------------------------------------------------

    private void runPrint(String[] args) throws IOException {

        String inputPath   = getArg(args, "--input",      null);
        String printerName = getArg(args, "--printer",    null);
        String password    = getArg(args, "--password",   null);
        String jobName     = getArg(args, "--job-name",   null);
        String sidesArg    = getArg(args, "--sides",      "simplex");
        String scalingArg  = getArg(args, "--scaling",    "fit");
        int    startPage   = intArg(args, "--start-page", -1);
        int    endPage     = intArg(args, "--end-page",   -1);
        int    copies      = intArg(args, "--copies",     1);
        boolean silent     = hasFlag(args, "--silent");

        if (inputPath == null) {
            System.err.println("Error: --print requires --input <pdf-path>");
            System.err.println("       Use --list-printers to see available printers.");
            System.exit(1);
        }

        // Parse sides
        PrintOptions.Sides sides;
        try {
            sides = parseSides(sidesArg);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: invalid --sides value '" + sidesArg +
                "'. Valid: simplex, duplex-long, duplex-short");
            System.exit(1);
            return;
        }

        // Parse scaling
        PrintOptions.ScalingMode scaling;
        try {
            scaling = parseScaling(scalingArg);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: invalid --scaling value '" + scalingArg +
                "'. Valid: fit, shrink, actual");
            System.exit(1);
            return;
        }

        PrintOptions.Builder builder = new PrintOptions.Builder(inputPath)
            .printerName(printerName)
            .startPage(startPage)
            .endPage(endPage)
            .copies(copies)
            .sides(sides)
            .scaling(scaling)
            .silent(silent)
            .password(password);

        if (jobName != null) builder.jobName(jobName);

        PrintOptions opts = builder.build();

        // Print summary
        System.out.println("Mode    : print");
        System.out.printf("Input   : %s%s%n", inputPath,
            password != null ? " (password-protected)" : "");
        System.out.printf("Printer : %s%n",
            printerName != null ? printerName : "system default");
        System.out.printf("Pages   : %s-%s%n",
            startPage < 0 ? "first" : startPage,
            endPage   < 0 ? "last"  : endPage);
        System.out.printf("Copies  : %d%n", copies);
        System.out.printf("Sides   : %s%n", sidesArg);
        System.out.printf("Scaling : %s%n", scalingArg);
        System.out.printf("Silent  : %b%n%n", silent);

        try {
            printer.print(opts);
            System.out.println("Print job submitted successfully.");
        } catch (PasswordRequiredException e) {
            System.err.println("\nError: " + e.getMessage());
            System.err.println(e.wasPasswordProvided()
                ? "  Hint: the supplied password is incorrect."
                : "  Hint: use --password <pwd> to supply the PDF password.");
            System.exit(1);
        } catch (PrinterException e) {
            System.err.println("\nPrinter error: " + e.getMessage());
            System.err.println("  Use --list-printers to verify the printer name.");
            System.exit(1);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static PrintOptions.Sides parseSides(String value) {
        return switch (value.toLowerCase().trim()) {
            case "simplex"      -> PrintOptions.Sides.SIMPLEX;
            case "duplex-long"  -> PrintOptions.Sides.DUPLEX_LONG_EDGE;
            case "duplex-short" -> PrintOptions.Sides.DUPLEX_SHORT_EDGE;
            default             -> throw new IllegalArgumentException("Unknown sides: " + value);
        };
    }

    private static PrintOptions.ScalingMode parseScaling(String value) {
        return switch (value.toLowerCase().trim()) {
            case "fit"    -> PrintOptions.ScalingMode.FIT;
            case "shrink" -> PrintOptions.ScalingMode.SHRINK;
            case "actual" -> PrintOptions.ScalingMode.ACTUAL_SIZE;
            default       -> throw new IllegalArgumentException("Unknown scaling: " + value);
        };
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
            int n = Integer.parseInt(val.trim());
            return n;
        } catch (NumberFormatException e) {
            System.err.println("Warning: invalid integer for " + flag +
                " ('" + val + "') — using default " + def);
            return def;
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) if (a.equals(flag)) return true;
        return false;
    }
}
