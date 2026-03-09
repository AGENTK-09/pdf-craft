package com.pdfcreator.forms;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Stateless engine that extracts AcroForm field metadata and current values
 * from an existing PDF document.
 *
 * ── TRAVERSAL STRATEGY ────────────────────────────────────────────────────
 *
 * AcroForm fields may be nested in a parent–child hierarchy. The PDAcroForm
 * API exposes two ways to walk fields:
 *
 *   acroForm.getFields()        — top-level fields only (misses nested children)
 *   acroForm.getFieldIterator() — depth-first iterator over ALL fields
 *
 * PdfFormExtractor always uses getFieldIterator() so deeply nested fields
 * (e.g. "address.city") are discovered correctly.
 *
 * ── FIELD TYPE DETECTION ──────────────────────────────────────────────────
 *
 * Type is detected by instanceof dispatch in this order:
 *   PDPushButton   → PUSHBUTTON  (check before PDButton which is parent class)
 *   PDRadioButton  → RADIO       (check before PDButton)
 *   PDCheckBox     → CHECKBOX    (check before PDButton)
 *   PDComboBox     → COMBO       (check before PDChoice)
 *   PDListBox      → LISTBOX     (check before PDChoice)
 *   PDTextField    → TEXT or MULTILINE (based on isMultiline())
 *   PDSignatureField → SIGNATURE
 *   everything else → UNKNOWN
 *
 * ── PASSWORD PROTECTED PDFs ───────────────────────────────────────────────
 *
 * If password is non-null it is tried as the user password. If that fails
 * the owner password is tried. A descriptive IOException is thrown if both
 * fail, so the CLI can report a clear error.
 *
 * ── JSON SERIALISATION ────────────────────────────────────────────────────
 *
 * extractToJson() writes the result as a JSON file without external
 * dependencies — it uses manual string building to keep the implementation
 * self-contained (no Gson/Jackson dependency to add to pom.xml).
 */
public class PdfFormExtractor {

    private static final Logger logger = Logger.getLogger(PdfFormExtractor.class.getName());

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Extracts all AcroForm fields from the PDF at inputPath.
     *
     * @param inputPath path to the source PDF
     * @param password  user/owner password, or null for unencrypted PDFs
     * @return extraction result (hasAcroForm=false if the PDF has no form)
     */
    public FormExtractionResult extract(String inputPath, String password) throws IOException {
        long start = System.currentTimeMillis();

        try (PDDocument document = openDocument(inputPath, password)) {
            PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();

            if (acroForm == null) {
                logger.info("No AcroForm found in: " + inputPath);
                return new FormExtractionResult.Builder()
                    .sourcePath(inputPath)
                    .hasAcroForm(false)
                    .fields(List.of())
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
            }

            List<FormField> fields = extractFields(acroForm);

            FormExtractionResult result = new FormExtractionResult.Builder()
                .sourcePath(inputPath)
                .hasAcroForm(true)
                .fields(fields)
                .durationMs(System.currentTimeMillis() - start)
                .build();

            logger.info(String.format("Extracted %d field(s) from %s in %dms",
                fields.size(), inputPath, result.getDurationMs()));

            return result;
        }
    }

    /**
     * Extracts all AcroForm fields and writes the result as a JSON file.
     *
     * @param inputPath  source PDF path
     * @param password   user/owner password, or null
     * @param outputPath where to write the JSON file; if null, prints to stdout
     * @return the extraction result (also written to file)
     */
    public FormExtractionResult extractToJson(String inputPath,
                                               String password,
                                               String outputPath) throws IOException {
        FormExtractionResult result = extract(inputPath, password);

        String json = toJson(result);

        if (outputPath == null) {
            System.out.println(json);
        } else {
            java.nio.file.Files.writeString(
                java.nio.file.Path.of(outputPath), json, StandardCharsets.UTF_8);
            logger.info("Extraction result written to: " + outputPath);
        }

        return result;
    }

    // -----------------------------------------------------------------------
    // Field traversal
    // -----------------------------------------------------------------------

    private List<FormField> extractFields(PDAcroForm acroForm) {
        List<FormField> result = new ArrayList<>();

        // getFieldIterator() does a depth-first traversal of the full field tree,
        // including nested children. getFields() would only return top-level nodes.
        acroForm.getFieldIterator().forEachRemaining(field -> {
            try {
                result.add(buildFormField(field));
            } catch (Exception e) {
                logger.warning("Skipping field '" + field.getFullyQualifiedName() +
                    "': " + e.getMessage());
            }
        });

        return result;
    }

    private FormField buildFormField(PDField field) throws IOException {
        String partial = field.getPartialName();
        String fqn     = field.getFullyQualifiedName();

        FormField.FieldType type    = detectType(field);
        String              value   = safeGetValue(field, type);
        List<String>        options = extractOptions(field, type);
        boolean             req     = field.isRequired();
        boolean             ro      = field.isReadOnly();
        String              tooltip = extractTooltip(field);

        return new FormField.Builder(partial, fqn)
            .fieldType(type)
            .currentValue(value)
            .options(options)
            .required(req)
            .readOnly(ro)
            .tooltip(tooltip)
            .build();
    }

    // -----------------------------------------------------------------------
    // Type detection — order matters: most-specific first
    // -----------------------------------------------------------------------

    private FormField.FieldType detectType(PDField field) {
        // PDPushButton, PDRadioButton, PDCheckBox all extend PDButton —
        // check them in order before the parent class.
        if (field instanceof PDPushButton)    return FormField.FieldType.PUSHBUTTON;
        if (field instanceof PDRadioButton)   return FormField.FieldType.RADIO;
        if (field instanceof PDCheckBox)      return FormField.FieldType.CHECKBOX;
        // PDComboBox and PDListBox extend PDChoice — check before PDChoice.
        if (field instanceof PDComboBox)      return FormField.FieldType.COMBO;
        if (field instanceof PDListBox)       return FormField.FieldType.LISTBOX;
        if (field instanceof PDTextField tf)  return tf.isMultiline()
                                                     ? FormField.FieldType.MULTILINE
                                                     : FormField.FieldType.TEXT;
        if (field instanceof PDSignatureField) return FormField.FieldType.SIGNATURE;
        return FormField.FieldType.UNKNOWN;
    }

    // -----------------------------------------------------------------------
    // Value extraction — guarded because getValue() can throw on malformed PDFs
    // -----------------------------------------------------------------------

    private String safeGetValue(PDField field, FormField.FieldType type) {
        if (type == FormField.FieldType.PUSHBUTTON ||
            type == FormField.FieldType.SIGNATURE  ||
            type == FormField.FieldType.UNKNOWN) {
            return null;
        }
        try {
            if (field instanceof PDCheckBox cb) {
                // isChecked() is more reliable than getValue() for checkboxes
                return cb.isChecked() ? cb.getOnValues().stream().findFirst().orElse("Yes") : "Off";
            }
            String v = field.getValueAsString();
            return v != null ? v : "";
        } catch (Exception e) {
            logger.fine("Could not read value for field '" +
                field.getFullyQualifiedName() + "': " + e.getMessage());
            return "";
        }
    }

    // -----------------------------------------------------------------------
    // Options (RADIO export values, COMBO/LISTBOX display options)
    // -----------------------------------------------------------------------

    private List<String> extractOptions(PDField field, FormField.FieldType type) {
        try {
            if (field instanceof PDRadioButton rb) {
                List<String> exports = rb.getExportValues();
                return exports != null ? List.copyOf(exports) : List.of();
            }
            if (field instanceof PDChoice choice) {
                // getOptionsDisplayValues() returns the human-readable labels;
                // getOptionsExportValues() returns the stored values.
                // We return display values because they are what callers see.
                List<String> display = choice.getOptionsDisplayValues();
                if (display != null && !display.isEmpty()) return List.copyOf(display);
                List<String> exports = choice.getOptionsExportValues();
                return exports != null ? List.copyOf(exports) : List.of();
            }
        } catch (Exception e) {
            logger.fine("Could not read options for field '" +
                field.getFullyQualifiedName() + "': " + e.getMessage());
        }
        return List.of();
    }

    // -----------------------------------------------------------------------
    // Tooltip — stored in the first widget's Contents entry
    // -----------------------------------------------------------------------

    private String extractTooltip(PDField field) {
        try {
            List<PDAnnotationWidget> widgets = field.getWidgets();
            if (widgets != null && !widgets.isEmpty()) {
                String contents = widgets.get(0).getContents();
                if (contents != null && !contents.isBlank()) return contents;
            }
        } catch (Exception e) {
            // Not critical — tooltips are optional
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Document opening with password support
    // -----------------------------------------------------------------------

    private PDDocument openDocument(String path, String password) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            throw new IOException("Input file not found: " + path);
        }
        try {
            // PDFBox 3.x: PDDocument.load() removed — use Loader.loadPDF() with
            // RandomAccessReadBufferedFile for file-based loading (migration guide).
            if (password != null && !password.isBlank()) {
                PDDocument doc = Loader.loadPDF(
                    new RandomAccessReadBufferedFile(file), password);
                AccessPermission ap = doc.getCurrentAccessPermission();
                if (!ap.canExtractContent()) {
                    doc.close();
                    throw new IOException(
                        "Password accepted but content extraction not permitted: " + path);
                }
                return doc;
            }
            return Loader.loadPDF(new RandomAccessReadBufferedFile(file));
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            throw new IOException("Incorrect password for PDF: " + path, e);
        }
    }

    // -----------------------------------------------------------------------
    // JSON serialisation — no external library, keeps pom.xml clean
    // -----------------------------------------------------------------------

    private String toJson(FormExtractionResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"sourcePath\": ").append(jsonString(result.getSourcePath())).append(",\n");
        sb.append("  \"hasAcroForm\": ").append(result.hasAcroForm()).append(",\n");
        sb.append("  \"totalFields\": ").append(result.getTotalFields()).append(",\n");
        sb.append("  \"fillableFields\": ").append(result.getFillable().size()).append(",\n");
        sb.append("  \"durationMs\": ").append(result.getDurationMs()).append(",\n");
        sb.append("  \"fields\": [\n");

        List<FormField> fields = result.getFields();
        for (int i = 0; i < fields.size(); i++) {
            FormField f = fields.get(i);
            sb.append("    {\n");
            sb.append("      \"partialName\": ")
              .append(jsonString(f.getPartialName())).append(",\n");
            sb.append("      \"fullyQualifiedName\": ")
              .append(jsonString(f.getFullyQualifiedName())).append(",\n");
            sb.append("      \"fieldType\": ")
              .append(jsonString(f.getFieldType().name())).append(",\n");
            sb.append("      \"currentValue\": ")
              .append(jsonString(f.getCurrentValue())).append(",\n");
            sb.append("      \"required\": ").append(f.isRequired()).append(",\n");
            sb.append("      \"readOnly\": ").append(f.isReadOnly()).append(",\n");
            sb.append("      \"tooltip\": ").append(jsonString(f.getTooltip())).append(",\n");
            sb.append("      \"options\": [");
            List<String> opts = f.getOptions();
            for (int j = 0; j < opts.size(); j++) {
                sb.append(jsonString(opts.get(j)));
                if (j < opts.size() - 1) sb.append(", ");
            }
            sb.append("]\n");
            sb.append("    }");
            if (i < fields.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ]\n}");
        return sb.toString();
    }

    /** Wraps a value as a JSON string literal, or null if the value is null. */
    private String jsonString(String value) {
        if (value == null) return "null";
        // Escape backslash, double-quote, and control characters
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\"";
    }
}
