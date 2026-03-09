package com.pdfcreator.template;

import java.util.List;

/**
 * Represents a single section within a PdfTemplate.
 *
 * Section types:
 *   heading     - Large bold text, underlined.
 *   subheading  - Medium bold text.
 *   body        - Wrapped paragraph text, multi-page aware.
 *   divider     - Horizontal rule.
 *   spacer      - Vertical whitespace gap.
 *   image       - Embedded image. content = file path or {{placeholder}}.
 *   table       - Variable-length tabular data from DocumentData list.
 *   summary     - Fixed key-value grid (opening/closing balance etc.).
 *   notice      - Boxed text with background color (regulatory notices).
 *   columns     - Two-column side-by-side layout block.
 *   pagebreak   - Forces content that follows onto a new page.
 *
 * COLUMNS sections carry a ColumnsSection companion object (columnsData)
 * rather than a content string. All other section types use content.
 */
public class TemplateSection {

    public enum Type {
        HEADING, SUBHEADING, BODY, DIVIDER, SPACER, IMAGE,
        TABLE, SUMMARY, NOTICE, COLUMNS, PAGEBREAK, FORM;

        public static Type fromString(String value) {
            if (value == null) throw new IllegalArgumentException("Section type cannot be null");
            return switch (value.trim().toUpperCase()) {
                case "HEADING"    -> HEADING;
                case "SUBHEADING" -> SUBHEADING;
                case "BODY"       -> BODY;
                case "DIVIDER"    -> DIVIDER;
                case "SPACER"     -> SPACER;
                case "IMAGE"      -> IMAGE;
                case "TABLE"      -> TABLE;
                case "SUMMARY"    -> SUMMARY;
                case "NOTICE"     -> NOTICE;
                case "COLUMNS"    -> COLUMNS;
                case "PAGEBREAK"  -> PAGEBREAK;
                case "FORM"       -> FORM;
                default -> throw new IllegalArgumentException(
                    "Unknown section type: '" + value + "'. Valid types: " +
                    "heading, subheading, body, divider, spacer, image, " +
                    "table, summary, notice, columns, pagebreak, form");
            };
        }
    }

    public enum Align { LEFT, CENTER, RIGHT }

    // Common
    private final Type   type;
    private final String content;

    // Image
    private final Align  align;
    private final int    widthPercent;

    // Table
    private final String          dataKey;
    private final List<ColumnDef> columns;
    private final String          headerBgColor;
    private final String          alternateRowColor;
    private final boolean         repeatHeaderOnPage;

    // Notice
    private final String          bgColor;

    // Columns layout
    private final ColumnsSection  columnsData;

    // Form (AcroForm field definitions)
    private final com.pdfcreator.forms.FormSection formData;

    private TemplateSection(Builder b) {
        this.type               = b.type;
        this.content            = b.content;
        this.align              = b.align;
        this.widthPercent       = b.widthPercent;
        this.dataKey            = b.dataKey;
        this.columns            = b.columns != null ? List.copyOf(b.columns) : List.of();
        this.headerBgColor      = b.headerBgColor;
        this.alternateRowColor  = b.alternateRowColor;
        this.repeatHeaderOnPage = b.repeatHeaderOnPage;
        this.bgColor            = b.bgColor;
        this.columnsData        = b.columnsData;
        this.formData           = b.formData;
    }

    public Type           getType()               { return type; }
    public String         getContent()            { return content; }
    public Align          getAlign()              { return align; }
    public int            getWidthPercent()       { return widthPercent; }
    public String         getDataKey()            { return dataKey; }
    public List<ColumnDef> getColumns()           { return columns; }
    public String         getHeaderBgColor()      { return headerBgColor; }
    public String         getAlternateRowColor()  { return alternateRowColor; }
    public boolean        isRepeatHeaderOnPage()  { return repeatHeaderOnPage; }
    public String         getBgColor()            { return bgColor; }
    public ColumnsSection getColumnsData()        { return columnsData; }
    public com.pdfcreator.forms.FormSection getFormData() { return formData; }

    public TemplateSection withContent(String resolvedContent) {
        return new Builder(this).content(resolvedContent).build();
    }

    @Override
    public String toString() {
        if (type == Type.TABLE)
            return String.format("TemplateSection[TABLE, dataKey=%s, columns=%d]", dataKey, columns.size());
        if (type == Type.COLUMNS)
            return "TemplateSection[COLUMNS, " + columnsData + "]";
        String preview = content != null && content.length() > 40
            ? content.substring(0, 40) + "..." : content;
        return String.format("TemplateSection[type=%s, content=%s]", type, preview);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private Type           type;
        private String         content            = null;
        private Align          align              = Align.LEFT;
        private int            widthPercent       = 100;
        private String         dataKey            = null;
        private List<ColumnDef> columns           = null;
        private String         headerBgColor      = null;
        private String         alternateRowColor  = null;
        private boolean        repeatHeaderOnPage = true;
        private String         bgColor            = null;
        private ColumnsSection columnsData        = null;
        private com.pdfcreator.forms.FormSection formData = null;

        public Builder(Type type)               { this.type = type; }
        public Builder(TemplateSection source)  {
            this.type               = source.type;
            this.content            = source.content;
            this.align              = source.align;
            this.widthPercent       = source.widthPercent;
            this.dataKey            = source.dataKey;
            this.columns            = source.columns;
            this.headerBgColor      = source.headerBgColor;
            this.alternateRowColor  = source.alternateRowColor;
            this.repeatHeaderOnPage = source.repeatHeaderOnPage;
            this.bgColor            = source.bgColor;
            this.columnsData        = source.columnsData;
            this.formData           = source.formData;
        }

        public Builder content(String v)            { this.content = v; return this; }
        public Builder align(Align v)               { this.align = v; return this; }
        public Builder widthPercent(int v)          { this.widthPercent = v; return this; }
        public Builder dataKey(String v)            { this.dataKey = v; return this; }
        public Builder columns(List<ColumnDef> v)   { this.columns = v; return this; }
        public Builder headerBgColor(String v)      { this.headerBgColor = v; return this; }
        public Builder alternateRowColor(String v)  { this.alternateRowColor = v; return this; }
        public Builder repeatHeaderOnPage(boolean v){ this.repeatHeaderOnPage = v; return this; }
        public Builder bgColor(String v)            { this.bgColor = v; return this; }
        public Builder columnsData(ColumnsSection v){ this.columnsData = v; return this; }
        public Builder formData(com.pdfcreator.forms.FormSection v) { this.formData = v; return this; }

        public TemplateSection build() {
            if (type == null) throw new IllegalStateException("Section type is required");
            return new TemplateSection(this);
        }
    }
}
