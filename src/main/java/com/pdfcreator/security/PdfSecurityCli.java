package com.pdfcreator.security;

import com.pdfcreator.extractor.PasswordRequiredException;

import java.io.IOException;

/**
 * CLI handler for PDF security operations.
 *
 * Called from PdfCreator.main() when any of the following flags are present:
 *   --encrypt, --decrypt, --change-password, --update-permissions, --inspect-security
 *
 * Each sub-command reads its own set of flags. All operations are
 * non-destructive — they always read from --input and write to --output.
 *
 * ── ENCRYPT ───────────────────────────────────────────────────────────────
 *
 *   --encrypt
 *   --input <path>             Source PDF (required)
 *   --output <path>            Encrypted output PDF (required)
 *   --owner-password <pwd>     Owner password — grants full access (required)
 *   --user-password <pwd>      User password — required to open the file
 *                              (default: "" — opens without prompting, but
 *                              permission flags are still enforced)
 *
 *   Permission flags (all default to allowed):
 *   --deny-print               Deny printing at full resolution
 *   --deny-print-faithful      Deny degraded (draft) printing
 *   --deny-copy                Deny copying text / graphics to clipboard
 *   --deny-modify              Deny modifying document content
 *   --deny-annotations         Deny adding/editing annotations and form fields
 *   --deny-fill-forms          Deny filling in existing form fields
 *   --deny-accessibility       Deny text extraction for accessibility tools
 *   --deny-assemble            Deny inserting/deleting/rotating pages
 *
 *   Alternatively, use a named preset instead of individual flags:
 *   --preset <name>            all-allowed | read-only | print-only | no-copy
 *
 *   Examples:
 *
 *   # Encrypt — password-gate, all permissions (just gated by password)
 *   java -jar pdf-creator.jar --encrypt \
 *     --input report.pdf --output report-enc.pdf \
 *     --owner-password ownerSecret --user-password userSecret
 *
 *   # Encrypt — open freely, read-only preset (no copy/modify)
 *   java -jar pdf-creator.jar --encrypt \
 *     --input report.pdf --output report-readonly.pdf \
 *     --owner-password ownerSecret --preset read-only
 *
 *   # Encrypt — custom: allow print, deny everything else
 *   java -jar pdf-creator.jar --encrypt \
 *     --input report.pdf --output report-print-only.pdf \
 *     --owner-password ownerSecret \
 *     --deny-copy --deny-modify --deny-annotations \
 *     --deny-fill-forms --deny-accessibility --deny-assemble
 *
 * ── DECRYPT ───────────────────────────────────────────────────────────────
 *
 *   --decrypt
 *   --input <path>             Encrypted source PDF (required)
 *   --output <path>            Plain output PDF (required)
 *   --owner-password <pwd>     Owner password (required — user password not sufficient)
 *
 *   Example:
 *   java -jar pdf-creator.jar --decrypt \
 *     --input report-enc.pdf --output report-plain.pdf \
 *     --owner-password ownerSecret
 *
 * ── CHANGE PASSWORD ───────────────────────────────────────────────────────
 *
 *   --change-password
 *   --input <path>             Encrypted source PDF (required)
 *   --output <path>            Re-encrypted output with new password(s) (required)
 *   --owner-password <pwd>     Current owner password (required)
 *   --new-owner-password <pwd> New owner password (required unless using --new-user-password only)
 *   --new-user-password <pwd>  New user password (optional; defaults to "" if omitted)
 *
 *   Example:
 *   java -jar pdf-creator.jar --change-password \
 *     --input report-enc.pdf --output report-newpwd.pdf \
 *     --owner-password oldOwner \
 *     --new-owner-password newOwner --new-user-password newUser
 *
 * ── UPDATE PERMISSIONS ────────────────────────────────────────────────────
 *
 *   --update-permissions
 *   --input <path>             Encrypted source PDF (required)
 *   --output <path>            Re-encrypted output with updated flags (required)
 *   --owner-password <pwd>     Owner password (required)
 *
 *   Permission flags (same as --encrypt: use --deny-* or --preset):
 *   --deny-print, --deny-copy, --deny-modify, ... (see ENCRYPT section above)
 *   --preset <name>            all-allowed | read-only | print-only | no-copy
 *
 *   Example:
 *   java -jar pdf-creator.jar --update-permissions \
 *     --input report-enc.pdf --output report-updated.pdf \
 *     --owner-password ownerSecret --preset read-only
 *
 * ── INSPECT SECURITY ──────────────────────────────────────────────────────
 *
 *   --inspect-security
 *   --input <path>             PDF to inspect (required)
 *   --owner-password <pwd>     Password if encrypted (optional for plain PDFs)
 *
 *   Example:
 *   java -jar pdf-creator.jar --inspect-security --input report-enc.pdf \
 *     --owner-password ownerSecret
 */
public class PdfSecurityCli {

    private final PdfSecurityManager manager = new PdfSecurityManager();

    /** Entry point called from PdfCreator.main(). */
    public void run(String[] args) throws IOException {
        if (hasFlag(args, "--encrypt")) {
            runEncrypt(args);
        } else if (hasFlag(args, "--decrypt")) {
            runDecrypt(args);
        } else if (hasFlag(args, "--change-password")) {
            runChangePassword(args);
        } else if (hasFlag(args, "--update-permissions")) {
            runUpdatePermissions(args);
        } else {
            runInspect(args);
        }
    }

    // -----------------------------------------------------------------------
    // ENCRYPT
    // -----------------------------------------------------------------------

    private void runEncrypt(String[] args) throws IOException {
        String input         = requireArg(args, "--input",          "ENCRYPT");
        String output        = requireArg(args, "--output",         "ENCRYPT");
        String ownerPassword = requireArg(args, "--owner-password", "ENCRYPT");
        String userPassword  = getArg(args, "--user-password", "");
        String preset        = getArg(args, "--preset", null);

        PdfPermissions permissions = resolvePermissions(args, preset);

        EncryptionOptions opts = new EncryptionOptions.Builder(EncryptionOptions.Operation.ENCRYPT)
            .inputPath(input)
            .outputPath(output)
            .ownerPassword(ownerPassword)
            .userPassword(userPassword)
            .permissions(permissions)
            .build();

        printHeader("ENCRYPT", input, output, null);
        System.out.printf("User password  : %s%n",
            userPassword.isEmpty() ? "(none — opens without prompting)" : "***");
        printPermissionTable(permissions);
        System.out.println();

        execute(opts);
    }

    // -----------------------------------------------------------------------
    // DECRYPT
    // -----------------------------------------------------------------------

    private void runDecrypt(String[] args) throws IOException {
        String input         = requireArg(args, "--input",          "DECRYPT");
        String output        = requireArg(args, "--output",         "DECRYPT");
        String ownerPassword = requireArg(args, "--owner-password", "DECRYPT");

        EncryptionOptions opts = new EncryptionOptions.Builder(EncryptionOptions.Operation.DECRYPT)
            .inputPath(input)
            .outputPath(output)
            .ownerPassword(ownerPassword)
            .build();

        printHeader("DECRYPT", input, output, null);
        System.out.println();

        execute(opts);
    }

    // -----------------------------------------------------------------------
    // CHANGE PASSWORD
    // -----------------------------------------------------------------------

    private void runChangePassword(String[] args) throws IOException {
        String input            = requireArg(args, "--input",          "CHANGE_PASSWORD");
        String output           = requireArg(args, "--output",         "CHANGE_PASSWORD");
        String ownerPassword    = requireArg(args, "--owner-password", "CHANGE_PASSWORD");
        String newOwnerPassword = getArg(args, "--new-owner-password", null);
        String newUserPassword  = getArg(args, "--new-user-password",  null);

        if (newOwnerPassword == null && newUserPassword == null) {
            System.err.println(
                "Error: --change-password requires at least one of: " +
                "--new-owner-password, --new-user-password");
            System.exit(1);
        }

        EncryptionOptions opts = new EncryptionOptions.Builder(EncryptionOptions.Operation.CHANGE_PASSWORD)
            .inputPath(input)
            .outputPath(output)
            .ownerPassword(ownerPassword)
            .newOwnerPassword(newOwnerPassword)
            .newUserPassword(newUserPassword)
            .build();

        printHeader("CHANGE PASSWORD", input, output, null);
        System.out.printf("New owner pwd  : %s%n",
            newOwnerPassword != null ? "***" : "(unchanged)");
        System.out.printf("New user pwd   : %s%n",
            newUserPassword  != null ? "***" : "(unchanged)");
        System.out.println();

        execute(opts);
    }

    // -----------------------------------------------------------------------
    // UPDATE PERMISSIONS
    // -----------------------------------------------------------------------

    private void runUpdatePermissions(String[] args) throws IOException {
        String input         = requireArg(args, "--input",          "UPDATE_PERMISSIONS");
        String output        = requireArg(args, "--output",         "UPDATE_PERMISSIONS");
        String ownerPassword = requireArg(args, "--owner-password", "UPDATE_PERMISSIONS");
        String preset        = getArg(args, "--preset", null);

        PdfPermissions permissions = resolvePermissions(args, preset);

        EncryptionOptions opts = new EncryptionOptions.Builder(EncryptionOptions.Operation.UPDATE_PERMISSIONS)
            .inputPath(input)
            .outputPath(output)
            .ownerPassword(ownerPassword)
            .permissions(permissions)
            .build();

        printHeader("UPDATE PERMISSIONS", input, output, null);
        printPermissionTable(permissions);
        System.out.println();

        execute(opts);
    }

    // -----------------------------------------------------------------------
    // INSPECT
    // -----------------------------------------------------------------------

    private void runInspect(String[] args) throws IOException {
        String input         = requireArg(args, "--input", "INSPECT_SECURITY");
        String ownerPassword = getArg(args, "--owner-password", null);

        EncryptionOptions opts = new EncryptionOptions.Builder(EncryptionOptions.Operation.INSPECT)
            .inputPath(input)
            .ownerPassword(ownerPassword)
            .build();

        printHeader("INSPECT SECURITY", input, null, ownerPassword);

        execute(opts);
    }

    // -----------------------------------------------------------------------
    // Shared execute + error handling
    // -----------------------------------------------------------------------

    private void execute(EncryptionOptions opts) throws IOException {
        try {
            SecurityResult result = manager.execute(opts);
            System.out.println(result.getSummary());
        } catch (PasswordRequiredException e) {
            System.err.println("\nError: " + e.getMessage());
            System.err.println(e.wasPasswordProvided()
                ? "  Hint: the supplied --owner-password is incorrect."
                : "  Hint: this PDF is encrypted. Supply --owner-password <pwd>.");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("\nError: " + e.getMessage());
            System.exit(1);
        }
    }

    // -----------------------------------------------------------------------
    // Permission resolution helpers
    // -----------------------------------------------------------------------

    /**
     * Resolves permission flags from either a --preset name or individual --deny-* flags.
     *
     * Precedence: if --preset is provided, it is used as the base and any
     * --deny-* flags are applied on top. This allows fine-tuning a preset.
     */
    private static PdfPermissions resolvePermissions(String[] args, String preset) {
        // Start from preset (or all-allowed if no preset)
        PdfPermissions base = preset != null ? parsePreset(preset) : PdfPermissions.allAllowed();

        // Apply any individual --deny-* overrides on top
        PdfPermissions.Builder b = new PdfPermissions.Builder()
            .allowPrint(!hasFlag(args, "--deny-print")         && base.canPrint())
            .allowPrintFaithful(!hasFlag(args, "--deny-print-faithful") && base.canPrintFaithful())
            .allowCopy(!hasFlag(args, "--deny-copy")           && base.canCopy())
            .allowModify(!hasFlag(args, "--deny-modify")       && base.canModify())
            .allowModifyAnnotations(!hasFlag(args, "--deny-annotations") && base.canModifyAnnotations())
            .allowFillForms(!hasFlag(args, "--deny-fill-forms") && base.canFillForms())
            .allowAccessibility(!hasFlag(args, "--deny-accessibility") && base.canAccessibility())
            .allowAssemble(!hasFlag(args, "--deny-assemble")   && base.canAssemble());

        return b.build();
    }

    private static PdfPermissions parsePreset(String preset) {
        return switch (preset.toLowerCase().trim()) {
            case "all-allowed"  -> PdfPermissions.allAllowed();
            case "read-only"    -> PdfPermissions.readOnly();
            case "print-only"   -> PdfPermissions.printOnly();
            case "no-copy"      -> PdfPermissions.noCopy();
            default -> {
                System.err.println("Error: unknown --preset '" + preset +
                    "'. Valid: all-allowed, read-only, print-only, no-copy");
                System.exit(1);
                yield PdfPermissions.allAllowed(); // unreachable
            }
        };
    }

    // -----------------------------------------------------------------------
    // Print helpers
    // -----------------------------------------------------------------------

    private static void printHeader(String mode, String input, String output, String password) {
        System.out.println("Mode           : " + mode);
        System.out.printf("Input          : %s%n", input);
        if (output != null)
            System.out.printf("Output         : %s%n", output);
        System.out.printf("Owner password : %s%n",
            password != null ? "***" : "(supplied via --owner-password)");
    }

    private static void printPermissionTable(PdfPermissions p) {
        System.out.println("Permissions    :");
        System.out.printf("  %-28s %s%n", "Print:",              yn(p.canPrint()));
        System.out.printf("  %-28s %s%n", "Print (faithful):",   yn(p.canPrintFaithful()));
        System.out.printf("  %-28s %s%n", "Copy content:",       yn(p.canCopy()));
        System.out.printf("  %-28s %s%n", "Modify document:",    yn(p.canModify()));
        System.out.printf("  %-28s %s%n", "Modify annotations:", yn(p.canModifyAnnotations()));
        System.out.printf("  %-28s %s%n", "Fill forms:",         yn(p.canFillForms()));
        System.out.printf("  %-28s %s%n", "Accessibility:",      yn(p.canAccessibility()));
        System.out.printf("  %-28s %s%n", "Assemble pages:",     yn(p.canAssemble()));
    }

    private static String yn(boolean v) { return v ? "allowed" : "DENIED"; }

    // -----------------------------------------------------------------------
    // CLI argument helpers
    // -----------------------------------------------------------------------

    private static String requireArg(String[] args, String flag, String op) {
        String val = getArg(args, flag, null);
        if (val == null) {
            System.err.println("Error: --" + op.toLowerCase().replace('_', '-')
                + " requires " + flag);
            System.exit(1);
        }
        return val;
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
