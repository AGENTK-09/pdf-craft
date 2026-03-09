package com.pdfcreator.forms;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable result of a form extraction operation.
 *
 * Returned by {@link PdfFormExtractor#extract(String, String)}.
 *
 * ── SUMMARY ───────────────────────────────────────────────────────────────
 *
 *   hasAcroForm()    — false if the PDF has no AcroForm dictionary at all.
 *                      All other fields are meaningless in this case.
 *   getFields()      — all discovered fields (fillable and non-fillable).
 *   getFillable()    — subset where FormField.FieldType.isFillable() is true.
 *   asNameValueMap() — convenience: fully-qualified-name → currentValue.
 *                      Useful for comparing before/after fill.
 *
 * ── JSON OUTPUT FORMAT ────────────────────────────────────────────────────
 *
 * PdfFormExtractor.extractToJson() serialises this object as:
 *
 * {
 *   "sourcePath": "forms/application.pdf",
 *   "hasAcroForm": true,
 *   "totalFields": 7,
 *   "fillableFields": 6,
 *   "durationMs": 42,
 *   "fields": [
 *     {
 *       "partialName": "fullName",
 *       "fullyQualifiedName": "fullName",
 *       "fieldType": "TEXT",
 *       "currentValue": "",
 *       "required": true,
 *       "readOnly": false,
 *       "tooltip": "Enter your full name",
 *       "options": []
 *     },
 *     {
 *       "partialName": "loanType",
 *       "fullyQualifiedName": "loanType",
 *       "fieldType": "COMBO",
 *       "currentValue": "Personal",
 *       "required": false,
 *       "readOnly": false,
 *       "tooltip": null,
 *       "options": ["Personal", "Home", "Auto"]
 *     }
 *   ]
 * }
 */
public class FormExtractionResult {

    private final String          sourcePath;
    private final boolean         hasAcroForm;
    private final List<FormField> fields;
    private final long            durationMs;

    private FormExtractionResult(Builder b) {
        this.sourcePath  = b.sourcePath;
        this.hasAcroForm = b.hasAcroForm;
        this.fields      = b.fields != null ? List.copyOf(b.fields) : List.of();
        this.durationMs  = b.durationMs;
    }

    public String          getSourcePath()    { return sourcePath; }
    public boolean         hasAcroForm()      { return hasAcroForm; }
    public List<FormField> getFields()        { return fields; }
    public long            getDurationMs()    { return durationMs; }
    public int             getTotalFields()   { return fields.size(); }

    public List<FormField> getFillable() {
        return fields.stream()
            .filter(f -> f.getFieldType().isFillable())
            .toList();
    }

    /**
     * Returns a map of fullyQualifiedName → currentValue for all fillable fields.
     * Useful for comparing the state of a form before and after filling.
     */
    public Map<String, String> asNameValueMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (FormField f : getFillable()) {
            map.put(f.getFullyQualifiedName(), f.getCurrentValue());
        }
        return map;
    }

    @Override
    public String toString() {
        return String.format(
            "FormExtractionResult[source=%s, hasAcroForm=%b, fields=%d, durationMs=%d]",
            sourcePath, hasAcroForm, fields.size(), durationMs);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private String          sourcePath  = "";
        private boolean         hasAcroForm = false;
        private List<FormField> fields      = List.of();
        private long            durationMs  = 0;

        public Builder sourcePath(String v)      { this.sourcePath  = v; return this; }
        public Builder hasAcroForm(boolean v)    { this.hasAcroForm = v; return this; }
        public Builder fields(List<FormField> v) { this.fields      = v; return this; }
        public Builder durationMs(long v)        { this.durationMs  = v; return this; }
        public FormExtractionResult build()      { return new FormExtractionResult(this); }
    }
}
