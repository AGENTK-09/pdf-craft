package com.pdfcreator.validator;

import java.io.IOException;

/**
 * CLI handler for the --validate mode.
 *
 * Called from PdfCreator.main() when the --validate flag is present.
 *
 * ── FLAGS ─────────────────────────────────────────────────────────────────
 *
 *   --validate               (required) activates this mode
 *   --input   <path>         PDF file to validate (required)
 *   --standard <name>        pdf-a-1b (default) or pdf-a-1a
 *   --max-errors <n>         stop after N errors (default: 0 = unlimited)
 *   --password <pwd>         password for encrypted PDFs
 *
 * ── EXAMPLES ──────────────────────────────────────────────────────────────
 *
 *   # Validate against PDF/A-1b (default)
 *   java -jar pdf-creator.jar --validate --input statement.pdf
 *
 *   # Validate against PDF/A-1a, show at most 20 errors
 *   java -jar pdf-creator.jar --validate \
 *       --input statement.pdf \
 *       --standard pdf-a-1a \
 *       --max-errors 20
 *
 * ── EXIT CODES ────────────────────────────────────────────────────────────
 *
 *   0  — document is valid
 *   1  — document is invalid (validation errors found)
 *   2  — input file not found or unreadable
 */
public class PdfValidatorCli {

    private final PdfValidator validator = new PdfValidator();

    public void run(String[] args) {

        String input     = requireArg(args, "--input",     "--validate");
        String stdStr    = getArg(args, "--standard",      "pdf-a-1b");
        int    maxErrors = intArg(args, "--max-errors",    0);
        String password  = getArg(args, "--password",      null);

        PdfAStandard standard;
        try {
            standard = PdfAStandard.parse(stdStr);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
            return;
        }

        ValidationOptions opts = new ValidationOptions.Builder()
            .inputPath(input)
            .standard(standard)
            .maxErrors(maxErrors)
            .password(password)
            .build();

        System.out.println("Mode     : validate");
        System.out.printf("Input    : %s%n", input);
        System.out.printf("Standard : %s%n", standard);
        System.out.printf("Max errs : %s%n",
            maxErrors == 0 ? "unlimited" : String.valueOf(maxErrors));
        System.out.println();

        try {
            PdfValidationResult result = validator.validate(opts);
            System.out.println(result.getSummary());
            System.exit(result.isValid() ? 0 : 1);

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String requireArg(String[] args, String flag, String op) {
        String v = getArg(args, flag, null);
        if (v == null) {
            System.err.println("Error: " + op + " requires " + flag);
            System.exit(2);
        }
        return v;
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
}
