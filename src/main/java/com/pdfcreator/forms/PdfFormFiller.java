package com.pdfcreator.forms;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.interactive.form.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Stateless engine that fills AcroForm field values in an existing PDF.
 *
 * ── FIELD LOOKUP ──────────────────────────────────────────────────────────
 *
 * Fields are resolved by building two lookup maps from the AcroForm field tree:
 *   1. fullyQualifiedName → PDField   (exact match, always tried first)
 *   2. partialName        → PDField   (fallback, resolves simple names like "city"
 *                                     to "address.city" when unambiguous)
 *
 * If a key in FormFillOptions.fieldValues matches neither map, the field name
 * is added to FormFillResult.skippedFields and processing continues.
 *
 * ── TYPE-SAFE VALUE SETTING ───────────────────────────────────────────────
 *
 * Values are set using type-specific logic:
 *
 *   TEXT / MULTILINE   → PDTextField.setValue(String)
 *
 *   CHECKBOX           → "Yes" / "true" / "on" / "1" → PDCheckBox.check()
 *                         everything else             → PDCheckBox.unCheck()
 *                        (case-insensitive)
 *
 *   RADIO              → PDRadioButton.setValue(exportValue)
 *                        The value must match one of PDRadioButton.getExportValues().
 *                        If it does not match, an error is recorded and the field
 *                        is left unchanged (we never set an invalid radio value).
 *
 *   COMBO / LISTBOX    → PDChoice.setValue(List.of(value))
 *                        If the value is not in the option list, a warning is
 *                        logged but setValue is still called — some PDFs allow
 *                        free-text entries in combo boxes (PDComboBox.isEdit()).
 *
 *   PUSHBUTTON / SIGNATURE / UNKNOWN
 *                      → always skipped with a warning; added to errorFields.
 *
 * ── NEED APPEARANCES ──────────────────────────────────────────────────────
 *
 * After all values are set, acroForm.setNeedAppearances(true) is called
 * unless the caller explicitly disabled it. This instructs viewers to
 * regenerate the visual appearance of each field from its value and Default
 * Appearance string, which is essential when no pre-built appearance streams
 * exist in the form.
 *
 * ── FLATTEN ───────────────────────────────────────────────────────────────
 *
 * If FormFillOptions.isFlatten() is true, acroForm.flatten() is called after
 * all values are set. This converts all interactive widgets to static content.
 * A warning is logged if the document also contains signature fields, since
 * flattening invalidates digital signatures.
 *
 * ── INCREMENTAL SAVE ──────────────────────────────────────────────────────
 *
 * The filled document is always saved using document.save(outputPath), not
 * incremental save, so the output is a clean, self-contained PDF regardless
 * of the source format.
 */
public class PdfFormFiller {

    private static final Logger logger = Logger.getLogger(PdfFormFiller.class.getName());

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Fills AcroForm fields in the source PDF and saves the result.
     *
     * @param opts specifies source path, output path, field values, and fill options
     * @return fill result with counters and any skipped/error field names
     * @throws IOException if the source PDF cannot be read or the output cannot be written
     */
    public FormFillResult fill(FormFillOptions opts) throws IOException {
        long start = System.currentTimeMillis();

        try (PDDocument document = openDocument(opts.getInputPath(), opts.getPassword())) {

            PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
            if (acroForm == null) {
                throw new IllegalStateException(
                    "PDF has no AcroForm: " + opts.getInputPath() +
                    ". Use --generate-form to create a form, or --extract-form to verify.");
            }

            // Build name → field lookup maps
            Map<String, PDField> byFqn     = new HashMap<>();
            Map<String, PDField> byPartial = new HashMap<>();
            buildLookupMaps(acroForm, byFqn, byPartial);

            // Check for signature fields before flatten (warn only)
            if (opts.isFlatten()) {
                boolean hasSignatures = byFqn.values().stream()
                    .anyMatch(f -> f instanceof PDSignatureField);
                if (hasSignatures) {
                    logger.warning("flatten=true but document contains signature fields. " +
                        "Flattening will invalidate existing digital signatures.");
                    System.out.println("WARNING: Flattening will invalidate digital signatures.");
                }
            }

            // Set NeedAppearances so viewers regenerate field appearances
            acroForm.setNeedAppearances(opts.isNeedAppearances());

            // Fill each requested field
            int          fieldsAttempted = opts.getFieldValues().size();
            int          fieldsFilled   = 0;
            List<String> skipped        = new ArrayList<>();
            List<String> errors         = new ArrayList<>();

            for (Map.Entry<String, String> entry : opts.getFieldValues().entrySet()) {
                String name  = entry.getKey();
                String value = entry.getValue();

                // Resolve field — FQN first, then partial name fallback
                PDField field = byFqn.get(name);
                if (field == null) field = byPartial.get(name);

                if (field == null) {
                    logger.warning("Field not found in AcroForm: '" + name + "' — skipping");
                    skipped.add(name);
                    continue;
                }

                try {
                    setFieldValue(field, value);
                    fieldsFilled++;
                    logger.fine("Set field '" + name + "' = '" + value + "'");
                } catch (Exception e) {
                    logger.warning("Error setting field '" + name + "': " + e.getMessage());
                    errors.add(name + " (" + e.getMessage() + ")");
                }
            }

            // Flatten if requested
            boolean flattened = false;
            if (opts.isFlatten()) {
                acroForm.flatten();
                flattened = true;
                logger.info("AcroForm flattened — fields are now static content");
            }

            // Save output
            document.save(opts.getOutputPath());

            FormFillResult result = new FormFillResult.Builder()
                .outputPath(opts.getOutputPath())
                .fieldsAttempted(fieldsAttempted)
                .fieldsFilled(fieldsFilled)
                .skippedFields(skipped)
                .errorFields(errors)
                .flattened(flattened)
                .durationMs(System.currentTimeMillis() - start)
                .build();

            printSummary(result);
            return result;
        }
    }

    // -----------------------------------------------------------------------
    // Field lookup map construction
    // -----------------------------------------------------------------------

    private void buildLookupMaps(PDAcroForm acroForm,
                                  Map<String, PDField> byFqn,
                                  Map<String, PDField> byPartial) {
        acroForm.getFieldIterator().forEachRemaining(field -> {
            String fqn     = field.getFullyQualifiedName();
            String partial = field.getPartialName();
            if (fqn     != null) byFqn.put(fqn, field);
            if (partial != null) {
                // Only add to partial map if not already present (first occurrence wins
                // for ambiguous partial names — fully-qualified is always preferred).
                byPartial.putIfAbsent(partial, field);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Type-safe value setting
    // -----------------------------------------------------------------------

    private void setFieldValue(PDField field, String value) throws IOException {

        if (field instanceof PDTextField tf) {
            tf.setValue(value != null ? value : "");
            return;
        }

        if (field instanceof PDCheckBox cb) {
            boolean check = value != null &&
                (value.equalsIgnoreCase("yes")  ||
                 value.equalsIgnoreCase("true") ||
                 value.equalsIgnoreCase("on")   ||
                 value.equals("1"));
            if (check) {
                cb.check();
            } else {
                cb.unCheck();
            }
            return;
        }

        if (field instanceof PDRadioButton rb) {
            List<String> exportValues = rb.getExportValues();
            if (exportValues != null && !exportValues.isEmpty() &&
                !exportValues.contains(value)) {
                throw new IllegalArgumentException(
                    "Invalid radio value '" + value + "'. " +
                    "Valid export values: " + exportValues);
            }
            rb.setValue(value);
            return;
        }

        if (field instanceof PDComboBox combo) {
            // ComboBox with isEdit()=true allows free-text; without edit, value should
            // be in the option list. We always call setValue and let PDFBox validate.
            combo.setValue(value != null ? value : "");
            return;
        }

        if (field instanceof PDListBox lb) {
            lb.setValue(value != null ? value : "");
            return;
        }

        if (field instanceof PDPushButton) {
            throw new IllegalArgumentException(
                "PUSHBUTTON fields cannot be filled — they are action triggers only.");
        }

        if (field instanceof PDSignatureField) {
            throw new IllegalArgumentException(
                "SIGNATURE fields cannot be filled via --fill-form. " +
                "Use --sign to apply a digital signature.");
        }

        throw new IllegalArgumentException(
            "Unsupported field type for field: " + field.getFullyQualifiedName());
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
                if (!ap.canFillInForm()) {
                    doc.close();
                    throw new IOException(
                        "PDF permissions do not allow form filling: " + path);
                }
                return doc;
            }
            return Loader.loadPDF(new RandomAccessReadBufferedFile(file));
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            throw new IOException("Incorrect password for PDF: " + path, e);
        }
    }

    // -----------------------------------------------------------------------
    // Console summary
    // -----------------------------------------------------------------------

    private void printSummary(FormFillResult r) {
        System.out.printf("Form filled → %s%n", r.getOutputPath());
        System.out.printf("Fields: %d filled / %d attempted%n",
            r.getFieldsFilled(), r.getFieldsAttempted());
        if (!r.getSkippedFields().isEmpty()) {
            System.out.println("Skipped (not found): " + r.getSkippedFields());
        }
        if (!r.getErrorFields().isEmpty()) {
            System.out.println("Errors: " + r.getErrorFields());
        }
        if (r.isFlattened()) {
            System.out.println("Form flattened — fields are now static content.");
        }
        System.out.printf("Duration: %dms%n", r.getDurationMs());
    }
}
