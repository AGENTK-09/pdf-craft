package com.pdfcreator.forms;

import java.util.List;

/**
 * Immutable result DTO representing one AcroForm field discovered during extraction.
 *
 * Returned by PdfFormExtractor as elements of FormExtractionResult.getFields().
 *
 * ── FIELD TYPE MAPPING ────────────────────────────────────────────────────
 *
 * PDFBox interactive form class → FormFieldType:
 *   PDTextField  (multiline=false) → TEXT
 *   PDTextField  (multiline=true)  → MULTILINE
 *   PDCheckBox                     → CHECKBOX
 *   PDRadioButton                  → RADIO
 *   PDComboBox                     → COMBO
 *   PDListBox                      → LISTBOX
 *   PDPushButton                   → PUSHBUTTON (display only, not fillable)
 *   PDSignatureField               → SIGNATURE  (display only)
 *
 * PUSHBUTTON and SIGNATURE are included in extraction output with null
 * currentValue so callers can see the full form structure. PdfFormFiller
 * will reject attempts to set values on these types.
 *
 * ── CURRENT VALUE ─────────────────────────────────────────────────────────
 *
 * currentValue reflects the field's value at time of extraction:
 *   - TEXT / MULTILINE : the text string, or "" if empty
 *   - CHECKBOX         : "Yes" if checked, "Off" if unchecked
 *   - RADIO            : the selected export value, or "Off" if none selected
 *   - COMBO / LISTBOX  : the selected option value, or "" if none selected
 *   - PUSHBUTTON       : null (no settable value)
 *   - SIGNATURE        : null (signed/unsigned status not represented here)
 *
 * ── OPTIONS ───────────────────────────────────────────────────────────────
 *
 * options is non-empty only for RADIO, COMBO, and LISTBOX fields.
 * For RADIO, each element is an export value (what gets stored in the field
 * when that button is selected). For COMBO/LISTBOX, each element is a
 * display option that can also be used as a fill value.
 *
 * ── FULLY QUALIFIED NAME ──────────────────────────────────────────────────
 *
 * PDF AcroForm fields may be nested in a hierarchy (parent→child).
 * fullyQualifiedName is the dot-separated path (e.g. "address.city").
 * partialName is just the leaf node name (e.g. "city").
 * PdfFormFiller accepts either name for field lookup.
 */
public class FormField {

    /** Extended type enum that includes non-fillable field types found during extraction. */
    public enum FieldType {
        TEXT, MULTILINE, CHECKBOX, RADIO, COMBO, LISTBOX, PUSHBUTTON, SIGNATURE, UNKNOWN;

        public boolean isFillable() {
            return this == TEXT || this == MULTILINE || this == CHECKBOX
                || this == RADIO || this == COMBO || this == LISTBOX;
        }
    }

    private final String       partialName;
    private final String       fullyQualifiedName;
    private final FieldType    fieldType;
    private final String       currentValue;    // null for PUSHBUTTON / SIGNATURE
    private final List<String> options;         // non-empty for RADIO, COMBO, LISTBOX
    private final boolean      required;
    private final boolean      readOnly;
    private final String       tooltip;         // from widget Contents entry, may be null

    private FormField(Builder b) {
        this.partialName         = b.partialName;
        this.fullyQualifiedName  = b.fullyQualifiedName;
        this.fieldType           = b.fieldType;
        this.currentValue        = b.currentValue;
        this.options             = b.options != null ? List.copyOf(b.options) : List.of();
        this.required            = b.required;
        this.readOnly            = b.readOnly;
        this.tooltip             = b.tooltip;
    }

    public String       getPartialName()        { return partialName; }
    public String       getFullyQualifiedName() { return fullyQualifiedName; }
    public FieldType    getFieldType()          { return fieldType; }
    public String       getCurrentValue()       { return currentValue; }
    public List<String> getOptions()            { return options; }
    public boolean      isRequired()            { return required; }
    public boolean      isReadOnly()            { return readOnly; }
    public String       getTooltip()            { return tooltip; }

    @Override
    public String toString() {
        return String.format("FormField[name=%s, type=%s, value=%s]",
            fullyQualifiedName, fieldType, currentValue);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private String       partialName;
        private String       fullyQualifiedName;
        private FieldType    fieldType    = FieldType.UNKNOWN;
        private String       currentValue = null;
        private List<String> options      = null;
        private boolean      required     = false;
        private boolean      readOnly     = false;
        private String       tooltip      = null;

        public Builder(String partialName, String fullyQualifiedName) {
            this.partialName        = partialName;
            this.fullyQualifiedName = fullyQualifiedName;
        }
        public Builder fieldType(FieldType v)     { this.fieldType    = v; return this; }
        public Builder currentValue(String v)     { this.currentValue = v; return this; }
        public Builder options(List<String> v)    { this.options      = v; return this; }
        public Builder required(boolean v)        { this.required     = v; return this; }
        public Builder readOnly(boolean v)        { this.readOnly     = v; return this; }
        public Builder tooltip(String v)          { this.tooltip      = v; return this; }
        public FormField build()                  { return new FormField(this); }
    }
}
