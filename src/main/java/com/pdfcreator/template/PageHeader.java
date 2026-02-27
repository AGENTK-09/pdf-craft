package com.pdfcreator.template;

/**
 * Defines an optional branding header to be drawn on every page of a document.
 *
 * A PageHeader is declared inside a template definition:
 *
 * "header": {
 *   "logoPath":        "assets/logo.png",   // fixed path or {{placeholder}}
 *   "logoAlign":       "right",             // left | center | right
 *   "logoWidthPercent": 20,                 // % of usable page width (default 25)
 *   "bandColor":       "#003366",           // background band behind the header (optional)
 *   "bandHeight":      60                   // height of the band in points (default 60)
 * }
 *
 * The header band is drawn first on each new page (before background color reset
 * so that it sits on top). The cursor starts below the band height.
 *
 * Any field may contain a {{placeholder}} which is resolved from the data file
 * before rendering — this lets each generated document use a different logo.
 */
public class PageHeader {

    public enum Align { LEFT, CENTER, RIGHT }

    private final String logoPath;        // file path to logo image (may be null = no logo)
    private final Align  logoAlign;
    private final int    logoWidthPercent; // as % of usable page width
    private final String bandColor;        // hex color (may be null = no band)
    private final float  bandHeight;       // points

    private PageHeader(Builder b) {
        this.logoPath        = b.logoPath;
        this.logoAlign       = b.logoAlign;
        this.logoWidthPercent = b.logoWidthPercent;
        this.bandColor       = b.bandColor;
        this.bandHeight      = b.bandHeight;
    }

    public String  getLogoPath()         { return logoPath; }
    public Align   getLogoAlign()        { return logoAlign; }
    public int     getLogoWidthPercent() { return logoWidthPercent; }
    public String  getBandColor()        { return bandColor; }
    public float   getBandHeight()       { return bandHeight; }

    public boolean hasLogo()     { return logoPath != null && !logoPath.isBlank(); }
    public boolean hasBand()     { return bandColor != null && !bandColor.isBlank(); }

    @Override
    public String toString() {
        return String.format("PageHeader[logo=%s, align=%s, width=%d%%, band=%s, height=%.0f]",
            logoPath, logoAlign, logoWidthPercent, bandColor, bandHeight);
    }

    public static class Builder {
        private String logoPath        = null;
        private Align  logoAlign       = Align.LEFT;
        private int    logoWidthPercent = 25;
        private String bandColor       = null;
        private float  bandHeight      = 60f;

        public Builder logoPath(String v)         { this.logoPath = v; return this; }
        public Builder logoAlign(String v) {
            if (v == null) return this;
            this.logoAlign = switch (v.trim().toUpperCase()) {
                case "CENTER" -> Align.CENTER;
                case "RIGHT"  -> Align.RIGHT;
                default       -> Align.LEFT;
            };
            return this;
        }
        public Builder logoWidthPercent(int v)    { this.logoWidthPercent = v; return this; }
        public Builder bandColor(String v)        { this.bandColor = v; return this; }
        public Builder bandHeight(float v)        { this.bandHeight = v; return this; }

        public PageHeader build() { return new PageHeader(this); }
    }
}
