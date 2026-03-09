package com.pdfcreator.forms;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Options for filling an existing PDF's AcroForm fields via PdfFormFiller.
 *
 * ── FIELD VALUES ──────────────────────────────────────────────────────────
 *
 * fieldValues is a map of fully-qualified-name (or partial name) → value.
 *
 * Partial name lookup: if a key does not match any fully-qualified name,
 * PdfFormFiller falls back to matching by partial name. This means
 * "city" will match "address.city" when no top-level field named "city" exists.
 * When both a partial and fully-qualified name exist, the fully-qualified
 * name always takes priority.
 *
 * Value format per field type:
 *   TEXT / MULTILINE  — any string
 *   CHECKBOX          — "Yes" / "true" / "on"  → checked; anything else → unchecked
 *   RADIO             — the export value of the desired option (case-sensitive)
 *   COMBO             — the display value of the desired option
 *   LISTBOX           — the display value of the desired option
 *
 * ── FLATTEN ───────────────────────────────────────────────────────────────
 *
 * When flatten=true, PdfFormFiller calls acroForm.flatten() after filling.
 * This converts all interactive widgets into static appearances and removes
 * the AcroForm dictionary, making the document non-editable. Use this when
 * producing a final, printable version of a filled form.
 *
 * WARNING: Flattening invalidates existing digital signatures.
 *
 * ── PASSWORD ──────────────────────────────────────────────────────────────
 *
 * If the source PDF is encrypted, supply the user or owner password.
 * The filled output is always saved unencrypted unless you encrypt it
 * separately using PdfSecurityManager after filling.
 */
public class FormFillOptions {

    private final String              inputPath;
    private final String              outputPath;
    private final Map<String, String> fieldValues;
    private final String              password;
    private final boolean             flatten;
    private final boolean             needAppearances;

    private FormFillOptions(Builder b) {
        this.inputPath      = b.inputPath;
        this.outputPath     = b.outputPath;
        this.fieldValues    = Map.copyOf(b.fieldValues);
        this.password       = b.password;
        this.flatten        = b.flatten;
        this.needAppearances = b.needAppearances;
    }

    public String              getInputPath()      { return inputPath; }
    public String              getOutputPath()     { return outputPath; }
    public Map<String, String> getFieldValues()    { return fieldValues; }
    public String              getPassword()       { return password; }
    public boolean             isFlatten()         { return flatten; }
    public boolean             isNeedAppearances() { return needAppearances; }

    public static class Builder {
        private String              inputPath      = null;
        private String              outputPath     = null;
        private Map<String, String> fieldValues    = new LinkedHashMap<>();
        private String              password       = null;
        private boolean             flatten        = false;
        private boolean             needAppearances = true;

        public Builder inputPath(String v)           { this.inputPath  = v; return this; }
        public Builder outputPath(String v)          { this.outputPath = v; return this; }
        public Builder fieldValues(Map<String,String> v) { this.fieldValues = new LinkedHashMap<>(v); return this; }
        public Builder addFieldValue(String name, String value) { this.fieldValues.put(name, value); return this; }
        public Builder password(String v)            { this.password   = v; return this; }
        public Builder flatten(boolean v)            { this.flatten    = v; return this; }
        public Builder needAppearances(boolean v)    { this.needAppearances = v; return this; }

        public FormFillOptions build() {
            if (inputPath  == null || inputPath.isBlank())
                throw new IllegalStateException("inputPath is required");
            if (outputPath == null || outputPath.isBlank())
                throw new IllegalStateException("outputPath is required");
            if (fieldValues.isEmpty())
                throw new IllegalStateException(
                    "At least one field value is required — use addFieldValue() or fieldValues()");
            return new FormFillOptions(this);
        }
    }
}
