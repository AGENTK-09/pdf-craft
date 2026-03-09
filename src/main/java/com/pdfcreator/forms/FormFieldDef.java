package com.pdfcreator.forms;

import java.util.List;

/**
 * Immutable definition of a single AcroForm field to be generated.
 *
 * Used both as template data (when declaring a FORM section in a JSON template)
 * and programmatically (when building forms via FormGenerationOptions).
 *
 * ── FIELD LAYOUT ─────────────────────────────────────────────────────────
 *
 * When rendered inside a FORM section, FormRenderer lays out each field as:
 *
 *   [label text]       ← rendered as body text above or beside the widget
 *   [  widget box  ]   ← interactive AcroForm widget annotation
 *
 * The widget's rectangle (PDF annotation) is what Acrobat and PDF viewers
 * render as the interactive control. It is placed at a position tracked by
 * the rendering cursor, not at a hard-coded coordinate.
 *
 * ── FIELD HEIGHT DEFAULTS ─────────────────────────────────────────────────
 *
 *   TEXT / MULTILINE / COMBO / LISTBOX  → fieldHeight (default 20pt single,
 *                                         or rows * lineHeight for MULTILINE)
 *   CHECKBOX / RADIO                    → toggleSize × toggleSize square
 *
 * ── RADIO GROUP ───────────────────────────────────────────────────────────
 *
 * For RADIO fields, each option in `options` becomes a separate radio button
 * widget in the same radio button group (shared fieldName). They are laid out
 * horizontally with a label beside each button.
 *
 * ── VALUE DEFAULTS ────────────────────────────────────────────────────────
 *
 * defaultValue is set as the field's initial value. For CHECKBOX, use "Yes"
 * (checked) or "" (unchecked). For RADIO/COMBO/LISTBOX, use the option value.
 */
public class FormFieldDef {

    private final String        fieldName;       // AcroForm partial field name (unique)
    private final FormFieldType fieldType;
    private final String        label;           // Human-readable label printed above the widget
    private final String        tooltip;         // Optional tooltip (PDAnnotationWidget.setContents)
    private final String        defaultValue;    // Initial field value; null = empty
    private final List<String>  options;         // RADIO / COMBO / LISTBOX: selectable values
    private final boolean       required;        // Sets the Required flag on the field
    private final boolean       readOnly;        // Sets the ReadOnly flag on the field
    private final float         fieldHeight;     // Widget height in points (0 = auto)
    private final int           multilineRows;   // MULTILINE: number of visible rows (default 3)
    private final float         toggleSize;      // CHECKBOX / RADIO: widget square size (default 12)

    private FormFieldDef(Builder b) {
        this.fieldName     = b.fieldName;
        this.fieldType     = b.fieldType;
        this.label         = b.label;
        this.tooltip       = b.tooltip;
        this.defaultValue  = b.defaultValue;
        this.options       = b.options != null ? List.copyOf(b.options) : List.of();
        this.required      = b.required;
        this.readOnly      = b.readOnly;
        this.fieldHeight   = b.fieldHeight;
        this.multilineRows = b.multilineRows;
        this.toggleSize    = b.toggleSize;
    }

    public String        getFieldName()     { return fieldName; }
    public FormFieldType getFieldType()     { return fieldType; }
    public String        getLabel()         { return label; }
    public String        getTooltip()       { return tooltip; }
    public String        getDefaultValue()  { return defaultValue; }
    public List<String>  getOptions()       { return options; }
    public boolean       isRequired()       { return required; }
    public boolean       isReadOnly()       { return readOnly; }
    public float         getFieldHeight()   { return fieldHeight; }
    public int           getMultilineRows() { return multilineRows; }
    public float         getToggleSize()    { return toggleSize; }

    /** Effective widget height — uses explicit fieldHeight, auto-calculates for multiline. */
    public float effectiveHeight(float lineHeight) {
        if (fieldHeight > 0) return fieldHeight;
        return switch (fieldType) {
            case MULTILINE -> multilineRows * lineHeight + 6f;
            case LISTBOX   -> multilineRows * lineHeight + 6f;
            case CHECKBOX,
                 RADIO     -> toggleSize;
            default        -> lineHeight + 4f;
        };
    }

    @Override
    public String toString() {
        return String.format("FormFieldDef[name=%s, type=%s, label=%s]",
            fieldName, fieldType, label);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private String        fieldName;
        private FormFieldType fieldType    = FormFieldType.TEXT;
        private String        label        = "";
        private String        tooltip      = null;
        private String        defaultValue = null;
        private List<String>  options      = null;
        private boolean       required     = false;
        private boolean       readOnly     = false;
        private float         fieldHeight  = 0f;   // 0 = auto
        private int           multilineRows = 3;
        private float         toggleSize   = 12f;

        public Builder(String fieldName)         { this.fieldName = fieldName; }
        public Builder type(FormFieldType v)     { this.fieldType = v; return this; }
        public Builder label(String v)           { this.label = v; return this; }
        public Builder tooltip(String v)         { this.tooltip = v; return this; }
        public Builder defaultValue(String v)    { this.defaultValue = v; return this; }
        public Builder options(List<String> v)   { this.options = v; return this; }
        public Builder required(boolean v)       { this.required = v; return this; }
        public Builder readOnly(boolean v)       { this.readOnly = v; return this; }
        public Builder fieldHeight(float v)      { this.fieldHeight = v; return this; }
        public Builder multilineRows(int v)      { this.multilineRows = v; return this; }
        public Builder toggleSize(float v)       { this.toggleSize = v; return this; }

        public FormFieldDef build() {
            if (fieldName == null || fieldName.isBlank())
                throw new IllegalStateException("FormFieldDef must have a fieldName");
            if (fieldType == null)
                throw new IllegalStateException("FormFieldDef must have a fieldType");
            if (fieldType.hasOptions() && (options == null || options.isEmpty()))
                throw new IllegalStateException(
                    "FormFieldDef '" + fieldName + "' of type " + fieldType +
                    " must have at least one option");
            return new FormFieldDef(this);
        }
    }
}
