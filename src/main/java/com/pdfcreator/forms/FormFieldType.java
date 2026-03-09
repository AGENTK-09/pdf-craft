package com.pdfcreator.forms;

/**
 * The set of AcroForm field types that PdfCreator can generate.
 *
 * Mapping to PDFBox interactive form classes:
 *
 *   TEXT       → PDTextField          (single-line text input)
 *   MULTILINE  → PDTextField          (PDTextField with multiline flag set)
 *   CHECKBOX   → PDCheckBox           (on/off toggle)
 *   RADIO      → PDRadioButton        (exclusive selection within a group)
 *   COMBO      → PDComboBox           (drop-down, one selection)
 *   LISTBOX    → PDListBox            (scrollable list, one or many selections)
 *
 * These cover the six field types in ISO 32000-1 §12.7.4 that are
 * meaningful for form generation (signature and push-button fields
 * are handled by PdfSignatureManager and are out of scope here).
 */
public enum FormFieldType {

    TEXT,
    MULTILINE,
    CHECKBOX,
    RADIO,
    COMBO,
    LISTBOX;

    public static FormFieldType fromString(String value) {
        if (value == null) throw new IllegalArgumentException("FormFieldType cannot be null");
        return switch (value.trim().toUpperCase()) {
            case "TEXT"      -> TEXT;
            case "MULTILINE" -> MULTILINE;
            case "CHECKBOX"  -> CHECKBOX;
            case "RADIO"     -> RADIO;
            case "COMBO"     -> COMBO;
            case "LISTBOX"   -> LISTBOX;
            default -> throw new IllegalArgumentException(
                "Unknown FormFieldType: '" + value + "'. " +
                "Valid types: text, multiline, checkbox, radio, combo, listbox");
        };
    }

    /** Returns true if this field type accepts a list of selectable options. */
    public boolean hasOptions() {
        return this == COMBO || this == LISTBOX || this == RADIO;
    }

    /** Returns true if this field type renders as a visual checkbox/radio widget. */
    public boolean isToggle() {
        return this == CHECKBOX || this == RADIO;
    }
}
