package com.pdfcreator.config;

/**
 * Immutable data model representing a PDF configuration preset.
 *
 * Added vs previous version:
 *   - Copy constructor on Builder(PdfConfig source) — used by ColumnsRenderer
 *     to produce column-scoped configs with overridden margins.
 */
public class PdfConfig {

    private final String id;
    private final String pageSize;
    private final int    titleFontSize;
    private final int    bodyFontSize;
    private final float  marginTop;
    private final float  marginBottom;
    private final float  marginLeft;
    private final float  marginRight;
    private final float  lineSpacing;
    private final String fontFamily;
    private final String fontColor;
    private final String titleColor;
    private final String backgroundColor;

    private PdfConfig(Builder builder) {
        this.id              = builder.id;
        this.pageSize        = builder.pageSize;
        this.titleFontSize   = builder.titleFontSize;
        this.bodyFontSize    = builder.bodyFontSize;
        this.marginTop       = builder.marginTop;
        this.marginBottom    = builder.marginBottom;
        this.marginLeft      = builder.marginLeft;
        this.marginRight     = builder.marginRight;
        this.lineSpacing     = builder.lineSpacing;
        this.fontFamily      = builder.fontFamily;
        this.fontColor       = builder.fontColor;
        this.titleColor      = builder.titleColor;
        this.backgroundColor = builder.backgroundColor;
    }

    public String getId()              { return id; }
    public String getPageSize()        { return pageSize; }
    public int    getTitleFontSize()   { return titleFontSize; }
    public int    getBodyFontSize()    { return bodyFontSize; }
    public float  getMarginTop()       { return marginTop; }
    public float  getMarginBottom()    { return marginBottom; }
    public float  getMarginLeft()      { return marginLeft; }
    public float  getMarginRight()     { return marginRight; }
    public float  getLineSpacing()     { return lineSpacing; }
    public String getFontFamily()      { return fontFamily; }
    public String getFontColor()       { return fontColor; }
    public String getTitleColor()      { return titleColor; }
    public String getBackgroundColor() { return backgroundColor; }

    @Override
    public String toString() {
        return String.format(
            "PdfConfig[id=%s, pageSize=%s, titleFont=%d, bodyFont=%d, " +
            "margins=(t:%.0f b:%.0f l:%.0f r:%.0f), spacing=%.1f, " +
            "font=%s, fontColor=%s, titleColor=%s]",
            id, pageSize, titleFontSize, bodyFontSize,
            marginTop, marginBottom, marginLeft, marginRight,
            lineSpacing, fontFamily, fontColor, titleColor);
    }

    public static class Builder {
        private String id;
        private String pageSize        = "A4";
        private int    titleFontSize   = 20;
        private int    bodyFontSize    = 12;
        private float  marginTop       = 50f;
        private float  marginBottom    = 50f;
        private float  marginLeft      = 50f;
        private float  marginRight     = 50f;
        private float  lineSpacing     = 1.4f;
        private String fontFamily      = "HELVETICA";
        private String fontColor       = "#000000";
        private String titleColor      = "#000000";
        private String backgroundColor = null;

        public Builder() {}

        /** Copy constructor — inherits all values from an existing config. */
        public Builder(PdfConfig source) {
            this.id              = source.id;
            this.pageSize        = source.pageSize;
            this.titleFontSize   = source.titleFontSize;
            this.bodyFontSize    = source.bodyFontSize;
            this.marginTop       = source.marginTop;
            this.marginBottom    = source.marginBottom;
            this.marginLeft      = source.marginLeft;
            this.marginRight     = source.marginRight;
            this.lineSpacing     = source.lineSpacing;
            this.fontFamily      = source.fontFamily;
            this.fontColor       = source.fontColor;
            this.titleColor      = source.titleColor;
            this.backgroundColor = source.backgroundColor;
        }

        public Builder id(String id)               { this.id = id; return this; }
        public Builder pageSize(String v)          { this.pageSize = v; return this; }
        public Builder titleFontSize(int v)        { this.titleFontSize = v; return this; }
        public Builder bodyFontSize(int v)         { this.bodyFontSize = v; return this; }
        public Builder marginTop(float v)          { this.marginTop = v; return this; }
        public Builder marginBottom(float v)       { this.marginBottom = v; return this; }
        public Builder marginLeft(float v)         { this.marginLeft = v; return this; }
        public Builder marginRight(float v)        { this.marginRight = v; return this; }
        public Builder lineSpacing(float v)        { this.lineSpacing = v; return this; }
        public Builder fontFamily(String v)        { this.fontFamily = v; return this; }
        public Builder fontColor(String v)         { this.fontColor = v; return this; }
        public Builder titleColor(String v)        { this.titleColor = v; return this; }
        public Builder backgroundColor(String v)   { this.backgroundColor = v; return this; }

        public PdfConfig build() {
            if (id == null || id.isBlank())
                throw new IllegalStateException("PdfConfig must have an id");
            return new PdfConfig(this);
        }
    }
}
