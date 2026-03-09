package com.pdfcreator.forms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * CLI handler for all AcroForm operations.
 *
 * Dispatches to:
 *   --generate-form   → PdfFormGenerator  (generate a new PDF with AcroForm fields)
 *   --extract-form    → (stub, Phase 2)   (extract field names/values from existing PDF)
 *   --fill-form       → (stub, Phase 2)   (fill field values in an existing PDF)
 *
 * ── GENERATE FORM ─────────────────────────────────────────────────────────
 *
 * Minimum required arguments:
 *   --generate-form
 *   --output <path>        Output PDF file path
 *
 * Optional arguments:
 *   --title <text>         Form title printed at the top (default: "Form")
 *   --config-id <id>       Style config preset (default: "default")
 *   --config-file <path>   Path to pdf-configs.json (default: configs/pdf-configs.json)
 *   --field <spec>         Field specification (repeatable, see format below)
 *   --need-appearances     Set NeedAppearances=true (default: always true)
 *
 * Field specification format for --field:
 *
 *   name=<fieldName>,type=<fieldType>[,label=<text>][,required][,readonly]
 *   [,default=<value>][,options=<v1|v2|v3>][,tooltip=<text>]
 *   [,height=<pts>][,rows=<n>][,toggleSize=<pts>]
 *
 * Field type values: text, multiline, checkbox, radio, combo, listbox
 *
 * Examples:
 *
 *   --field "name=fullName,type=text,label=Full Name,required"
 *   --field "name=loanType,type=combo,label=Loan Type,options=Personal|Home|Auto,default=Personal"
 *   --field "name=agree,type=checkbox,label=I agree to the terms,default=Yes"
 *   --field "name=payFreq,type=radio,label=Payment Frequency,options=Monthly|Quarterly|Annual"
 *   --field "name=notes,type=multiline,label=Additional Notes,rows=4"
 *
 * ── EXTRACT FORM (Phase 2 stub) ───────────────────────────────────────────
 *
 *   --extract-form --input <path> [--output <json-path>]
 *
 * ── FILL FORM (Phase 2 stub) ──────────────────────────────────────────────
 *
 *   --fill-form --input <path> --output <path>
 *               --field "name=<fieldName>,value=<value>" (repeatable)
 *               [--flatten]
 */
public class PdfFormCli {

    private static final Logger logger = Logger.getLogger(PdfFormCli.class.getName());

    public void run(String[] args) throws IOException {

        if (hasFlag(args, "--generate-form")) {
            runGenerateForm(args);
        } else if (hasFlag(args, "--extract-form")) {
            runExtractForm(args);
        } else if (hasFlag(args, "--fill-form")) {
            runFillForm(args);
        } else {
            System.err.println("PdfFormCli: no form flag provided. " +
                "Use --generate-form, --extract-form, or --fill-form.");
        }
    }

    // -----------------------------------------------------------------------
    // Generate form
    // -----------------------------------------------------------------------

    private void runGenerateForm(String[] args) throws IOException {
        String outputPath  = getArg(args, "--output",      "form-output.pdf");
        String title       = getArg(args, "--title",       "Form");
        String configId    = getArg(args, "--config-id",   "default");
        String configFile  = getArg(args, "--config-file", "configs/pdf-configs.json");

        List<FormFieldDef> fieldDefs = parseFieldArgs(args);
        if (fieldDefs.isEmpty()) {
            System.err.println("--generate-form: no --field arguments provided. " +
                "Use --field \"name=<name>,type=<type>[,label=<label>]...\" to add fields.");
            System.exit(1);
        }

        FormGenerationOptions opts = new FormGenerationOptions.Builder()
            .outputPath(outputPath)
            .title(title)
            .configId(configId)
            .configFile(configFile)
            .fieldDefs(fieldDefs)
            .needAppearances(true)
            .build();

        String out = new PdfFormGenerator(configFile).generate(opts);
        System.out.println("Form generated: " + out);
    }

    // -----------------------------------------------------------------------
    // Extract form fields
    // -----------------------------------------------------------------------

    private void runExtractForm(String[] args) throws IOException {
        String inputPath  = getArg(args, "--input",    null);
        String outputPath = getArg(args, "--output",   null);  // null → print to stdout
        String password   = getArg(args, "--password", null);

        if (inputPath == null) {
            System.err.println("--extract-form requires --input <pdf-path>");
            System.exit(1);
        }

        PdfFormExtractor extractor = new PdfFormExtractor();
        FormExtractionResult result = extractor.extractToJson(inputPath, password, outputPath);

        // Summary to stderr so stdout stays clean for JSON piping
        System.err.printf("Extraction complete: %d field(s) found%s%n",
            result.getTotalFields(),
            result.hasAcroForm() ? "" : " (no AcroForm)");

        if (outputPath != null) {
            System.out.println("Extraction result written to: " + outputPath);
        }
    }

    // -----------------------------------------------------------------------
    // Fill form fields
    // -----------------------------------------------------------------------

    private void runFillForm(String[] args) throws IOException {
        String inputPath  = getArg(args, "--input",    null);
        String outputPath = getArg(args, "--output",   null);
        String password   = getArg(args, "--password", null);
        boolean flatten   = hasFlag(args, "--flatten");

        if (inputPath == null) {
            System.err.println("--fill-form requires --input <pdf-path>");
            System.exit(1);
        }
        if (outputPath == null) {
            System.err.println("--fill-form requires --output <pdf-path>");
            System.exit(1);
        }

        // Parse --field "name=<fieldName>,value=<value>" entries
        // Re-use parseFieldArgs but only care about name= and value= keys
        Map<String, String> fieldValues = parseFillFieldArgs(args);
        if (fieldValues.isEmpty()) {
            System.err.println(
                "--fill-form: no --field arguments provided. " +
                "Use --field \"name=<fieldName>,value=<value>\" (repeatable).");
            System.exit(1);
        }

        FormFillOptions opts = new FormFillOptions.Builder()
            .inputPath(inputPath)
            .outputPath(outputPath)
            .fieldValues(fieldValues)
            .password(password)
            .flatten(flatten)
            .needAppearances(true)
            .build();

        FormFillResult result = new PdfFormFiller().fill(opts);

        if (!result.isClean()) {
            System.err.println("WARNING: fill completed with issues — " +
                "check skipped/error fields above.");
        }
    }

    // -----------------------------------------------------------------------
    // Field spec parser
    // -----------------------------------------------------------------------

    /**
     * Parses all --field arguments from the CLI args array into FormFieldDef objects.
     *
     * Each --field value is a comma-separated key=value spec:
     *   name=fullName,type=text,label=Full Name,required,default=Alice,
     *   options=A|B|C,tooltip=Enter your name,height=20,rows=3,toggleSize=12
     */
    private List<FormFieldDef> parseFieldArgs(String[] args) {
        List<FormFieldDef> fields = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if ("--field".equals(args[i]) && i + 1 < args.length) {
                String spec = args[++i];
                try {
                    fields.add(parseFieldSpec(spec));
                } catch (Exception e) {
                    System.err.println("Invalid --field spec: " + spec + " — " + e.getMessage());
                    System.exit(1);
                }
            }
        }
        return fields;
    }

    private FormFieldDef parseFieldSpec(String spec) {
        // Split on commas, but allow commas inside label values:
        // We do a simple split on ',' and handle multi-word labels via 'label=' key
        String[] parts = spec.split(",");
        String fieldName    = null;
        FormFieldType type  = FormFieldType.TEXT;
        String label        = "";
        String tooltip      = null;
        String defaultValue = null;
        List<String> options = new ArrayList<>();
        boolean required    = false;
        boolean readOnly    = false;
        float height        = 0f;
        int rows            = 3;
        float toggleSize    = 12f;

        for (String part : parts) {
            part = part.trim();
            if (part.equalsIgnoreCase("required")) { required  = true; continue; }
            if (part.equalsIgnoreCase("readonly"))  { readOnly  = true; continue; }

            int eq = part.indexOf('=');
            if (eq < 0) continue;

            String key = part.substring(0, eq).trim().toLowerCase();
            String val = part.substring(eq + 1).trim();

            switch (key) {
                case "name"        -> fieldName    = val;
                case "type"        -> type         = FormFieldType.fromString(val);
                case "label"       -> label        = val;
                case "tooltip"     -> tooltip      = val;
                case "default"     -> defaultValue = val;
                case "options"     -> { for (String o : val.split("\\|")) options.add(o.trim()); }
                case "height"      -> height       = Float.parseFloat(val);
                case "rows"        -> rows         = Integer.parseInt(val);
                case "togglesize"  -> toggleSize   = Float.parseFloat(val);
                default            -> logger.fine("Unknown field spec key: " + key);
            }
        }

        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("Field spec missing 'name=...'");
        }

        FormFieldDef.Builder b = new FormFieldDef.Builder(fieldName)
            .type(type)
            .label(label)
            .tooltip(tooltip)
            .defaultValue(defaultValue)
            .required(required)
            .readOnly(readOnly)
            .fieldHeight(height)
            .multilineRows(rows)
            .toggleSize(toggleSize);

        if (!options.isEmpty()) b.options(options);

        return b.build();
    }

    /**
     * Parses --field "name=<fieldName>,value=<value>" arguments for --fill-form.
     * Ignores --field arguments that have no value= key (those are for --generate-form).
     */
    private Map<String, String> parseFillFieldArgs(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if ("--field".equals(args[i]) && i + 1 < args.length) {
                String spec = args[++i];
                String[] parts = spec.split(",");
                String name  = null;
                String value = null;
                for (String part : parts) {
                    part = part.trim();
                    int eq = part.indexOf('=');
                    if (eq < 0) continue;
                    String key = part.substring(0, eq).trim().toLowerCase();
                    String val = part.substring(eq + 1).trim();
                    if ("name".equals(key))  name  = val;
                    if ("value".equals(key)) value = val;
                }
                if (name != null && value != null) {
                    result.put(name, value);
                } else if (name != null) {
                    // --field "name=x" with no value= key → empty string (clear the field)
                    result.put(name, "");
                }
            }
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Arg helpers
    // -----------------------------------------------------------------------

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) if (arg.equals(flag)) return true;
        return false;
    }

    private static String getArg(String[] args, String flag, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++)
            if (args[i].equals(flag)) return args[i + 1];
        return defaultValue;
    }
}
