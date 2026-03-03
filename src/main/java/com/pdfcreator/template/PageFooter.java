package com.pdfcreator.template;

/**
 * Defines a configurable footer drawn on every page of a document.
 *
 * Mirrors the PageHeader model. Declared inside a template definition:
 *
 * "footer": {
 *   "centerText":  "National Bank plc | Authorised by the FCA | fca.org.uk",
 *   "rightText":   "{{branch_address}}",
 *   "showPageNumbers": true,
 *   "pageNumberFormat": "Page {n} of {total}",
 *   "bandColor":   "#003366",
 *   "bandHeight":  28
 * }
 *
 * Text fields may contain {{placeholders}} resolved from DocumentData scalars.
 * The special tokens {n} and {total} in pageNumberFormat are substituted during
 * the second-pass stamping phase (after total page count is known).
 *
 * Layout within the footer band:
 *   leftText    — left-aligned
 *   centerText  — centered
 *   rightText   — right-aligned
 *   page number — always bottom-center unless overridden by pageNumberFormat position
 */
public class PageFooter {

    private final String  leftText;
    private final String  centerText;
    private final String  rightText;
    private final boolean showPageNumbers;
    private final String  pageNumberFormat; // {n} = current page, {total} = total pages
    private final String  bandColor;
    private final float   bandHeight;

    private PageFooter(Builder b) {
        this.leftText         = b.leftText;
        this.centerText       = b.centerText;
        this.rightText        = b.rightText;
        this.showPageNumbers  = b.showPageNumbers;
        this.pageNumberFormat = b.pageNumberFormat;
        this.bandColor        = b.bandColor;
        this.bandHeight       = b.bandHeight;
    }

    public String  getLeftText()         { return leftText; }
    public String  getCenterText()       { return centerText; }
    public String  getRightText()        { return rightText; }
    public boolean isShowPageNumbers()   { return showPageNumbers; }
    public String  getPageNumberFormat() { return pageNumberFormat; }
    public String  getBandColor()        { return bandColor; }
    public float   getBandHeight()       { return bandHeight; }

    public boolean hasBand() { return bandColor != null && !bandColor.isBlank(); }

    /** Formats the page number string for page n of total. */
    public String formatPageNumber(int n, int total) {
        return pageNumberFormat
            .replace("{n}",     String.valueOf(n))
            .replace("{total}", String.valueOf(total));
    }

    @Override
    public String toString() {
        return String.format("PageFooter[center=%s, pageNos=%b, band=%s, height=%.0f]",
            centerText, showPageNumbers, bandColor, bandHeight);
    }

    /** Returns a copy with resolved placeholder text. */
    public PageFooter withResolvedText(String left, String center, String right) {
        return new Builder()
            .leftText(left)
            .centerText(center)
            .rightText(right)
            .showPageNumbers(this.showPageNumbers)
            .pageNumberFormat(this.pageNumberFormat)
            .bandColor(this.bandColor)
            .bandHeight(this.bandHeight)
            .build();
    }

    public static class Builder {
        private String  leftText         = null;
        private String  centerText       = null;
        private String  rightText        = null;
        private boolean showPageNumbers  = true;
        private String  pageNumberFormat = "Page {n} of {total}";
        private String  bandColor        = null;
        private float   bandHeight       = 28f;

        public Builder leftText(String v)         { this.leftText = v; return this; }
        public Builder centerText(String v)        { this.centerText = v; return this; }
        public Builder rightText(String v)         { this.rightText = v; return this; }
        public Builder showPageNumbers(boolean v)  { this.showPageNumbers = v; return this; }
        public Builder pageNumberFormat(String v)  { this.pageNumberFormat = v != null ? v : "Page {n} of {total}"; return this; }
        public Builder bandColor(String v)         { this.bandColor = v; return this; }
        public Builder bandHeight(float v)         { this.bandHeight = v > 0 ? v : 28f; return this; }

        public PageFooter build() { return new PageFooter(this); }
    }
}
