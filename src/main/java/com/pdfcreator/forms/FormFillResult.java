package com.pdfcreator.forms;

import java.util.List;

/**
 * Immutable result of a form fill operation.
 *
 * Returned by {@link PdfFormFiller#fill(FormFillOptions)}.
 *
 * ── FIELD COUNTERS ─────────────────────────────────────────────────────────
 *
 *   fieldsAttempted  — total number of fieldName→value pairs in the request
 *   fieldsFilled     — fields that were successfully set
 *   skippedFields    — field names that were not found in the form
 *   errorFields      — field names where setValue() threw an exception
 *                      (e.g. invalid value for RADIO, read-only field)
 *
 * A fill is considered "clean" when skippedFields and errorFields are both
 * empty. The CLI prints a warning if either is non-empty.
 *
 * ── FLATTEN ───────────────────────────────────────────────────────────────
 *
 * If FormFillOptions.isFlatten() was true, the AcroForm widgets are
 * converted to static content and removed from the interactive form. The
 * resulting PDF is no longer editable. flattened reflects whether
 * acroForm.flatten() was called (regardless of whether it succeeded).
 *
 * ── IMPORTANT: FLATTEN + DIGITAL SIGNATURES ───────────────────────────────
 *
 * Flattening invalidates any existing digital signatures in the document.
 * PdfFormFiller logs a warning if both flatten=true and the document
 * contains signature fields.
 */
public class FormFillResult {

    private final String       outputPath;
    private final int          fieldsAttempted;
    private final int          fieldsFilled;
    private final List<String> skippedFields;
    private final List<String> errorFields;
    private final boolean      flattened;
    private final long         durationMs;

    private FormFillResult(Builder b) {
        this.outputPath      = b.outputPath;
        this.fieldsAttempted = b.fieldsAttempted;
        this.fieldsFilled    = b.fieldsFilled;
        this.skippedFields   = b.skippedFields != null ? List.copyOf(b.skippedFields) : List.of();
        this.errorFields     = b.errorFields   != null ? List.copyOf(b.errorFields)   : List.of();
        this.flattened       = b.flattened;
        this.durationMs      = b.durationMs;
    }

    public String       getOutputPath()      { return outputPath; }
    public int          getFieldsAttempted() { return fieldsAttempted; }
    public int          getFieldsFilled()    { return fieldsFilled; }
    public List<String> getSkippedFields()   { return skippedFields; }
    public List<String> getErrorFields()     { return errorFields; }
    public boolean      isFlattened()        { return flattened; }
    public long         getDurationMs()      { return durationMs; }
    public boolean      isClean()            { return skippedFields.isEmpty() && errorFields.isEmpty(); }

    @Override
    public String toString() {
        return String.format(
            "FormFillResult[output=%s, filled=%d/%d, skipped=%d, errors=%d, flattened=%b, %dms]",
            outputPath, fieldsFilled, fieldsAttempted,
            skippedFields.size(), errorFields.size(), flattened, durationMs);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private String       outputPath      = "";
        private int          fieldsAttempted = 0;
        private int          fieldsFilled    = 0;
        private List<String> skippedFields   = List.of();
        private List<String> errorFields     = List.of();
        private boolean      flattened       = false;
        private long         durationMs      = 0;

        public Builder outputPath(String v)        { this.outputPath      = v; return this; }
        public Builder fieldsAttempted(int v)      { this.fieldsAttempted = v; return this; }
        public Builder fieldsFilled(int v)         { this.fieldsFilled    = v; return this; }
        public Builder skippedFields(List<String> v){ this.skippedFields  = v; return this; }
        public Builder errorFields(List<String> v)  { this.errorFields    = v; return this; }
        public Builder flattened(boolean v)        { this.flattened       = v; return this; }
        public Builder durationMs(long v)          { this.durationMs      = v; return this; }
        public FormFillResult build()              { return new FormFillResult(this); }
    }
}
