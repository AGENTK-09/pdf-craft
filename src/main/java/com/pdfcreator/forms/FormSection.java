package com.pdfcreator.forms;

import java.util.List;

/**
 * Companion data for a TemplateSection of type FORM.
 *
 * Analogous to ColumnsSection for COLUMNS sections: it carries the
 * structured data that FormRenderer needs to build and place the AcroForm
 * widgets on the page. The parent TemplateSection holds the type; this
 * object holds everything form-specific.
 *
 * ── FORM OPTIONS ─────────────────────────────────────────────────────────
 *
 *   needAppearances  — Sets NeedAppearances=true on the AcroForm dictionary.
 *                      Instructs viewers to regenerate field appearances from
 *                      current values. Required when creating forms with
 *                      non-standard fonts or when appearances are not pre-built.
 *                      Default: true.
 *
 *   fieldSpacing     — Vertical gap between consecutive field widgets (points).
 *                      Default: 8pt.
 *
 *   labelFontSize    — Font size used for the label text printed above each
 *                      widget. Default: matches PdfConfig.getBodyFontSize().
 *                      0 = use config value.
 *
 * ── LAYOUT ────────────────────────────────────────────────────────────────
 *
 * Fields are rendered top-to-bottom in the order they appear in fieldDefs.
 * Each field occupies:
 *   labelFontSize + 2pt (label line)
 *   + fieldDef.effectiveHeight(lineHeight) (widget)
 *   + fieldSpacing (gap after widget)
 *
 * If the remaining page space cannot fit the next field, FormRenderer calls
 * ctx.advanceY() to trigger a page break before that field.
 */
public class FormSection {

    private final List<FormFieldDef> fieldDefs;
    private final boolean            needAppearances;
    private final float              fieldSpacing;
    private final int                labelFontSize;   // 0 = use config value

    private FormSection(Builder b) {
        this.fieldDefs       = b.fieldDefs != null ? List.copyOf(b.fieldDefs) : List.of();
        this.needAppearances = b.needAppearances;
        this.fieldSpacing    = b.fieldSpacing;
        this.labelFontSize   = b.labelFontSize;
    }

    public List<FormFieldDef> getFieldDefs()       { return fieldDefs; }
    public boolean            isNeedAppearances()  { return needAppearances; }
    public float              getFieldSpacing()    { return fieldSpacing; }
    public int                getLabelFontSize()   { return labelFontSize; }

    @Override
    public String toString() {
        return String.format("FormSection[fields=%d, needAppearances=%b]",
            fieldDefs.size(), needAppearances);
    }

    public static class Builder {
        private List<FormFieldDef> fieldDefs       = List.of();
        private boolean            needAppearances = true;
        private float              fieldSpacing    = 8f;
        private int                labelFontSize   = 0;

        public Builder fieldDefs(List<FormFieldDef> v)  { this.fieldDefs = v; return this; }
        public Builder needAppearances(boolean v)        { this.needAppearances = v; return this; }
        public Builder fieldSpacing(float v)             { this.fieldSpacing = v; return this; }
        public Builder labelFontSize(int v)              { this.labelFontSize = v; return this; }

        public FormSection build() {
            if (fieldDefs == null || fieldDefs.isEmpty())
                throw new IllegalStateException("FormSection must have at least one field definition");
            return new FormSection(this);
        }
    }
}
