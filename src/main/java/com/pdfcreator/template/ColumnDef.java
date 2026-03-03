package com.pdfcreator.template;

/**
 * Defines a single column in a TABLE section.
 *
 * JSON representation inside a template section:
 * {
 *   "key":        "balance",    -- key in each row map from DocumentData
 *   "header":     "Balance",    -- column header label
 *   "widthPct":   16,           -- percentage of usable page width
 *   "align":      "right"       -- left | center | right (default: left)
 * }
 *
 * The sum of all widthPct values in a table should equal 100.
 * If they don't, columns are rendered proportionally to their declared widths.
 */
public class ColumnDef {

    public enum Align { LEFT, CENTER, RIGHT }

    private final String key;
    private final String header;
    private final int    widthPct;
    private final Align  align;

    public ColumnDef(String key, String header, int widthPct, Align align) {
        this.key      = key;
        this.header   = header;
        this.widthPct = widthPct;
        this.align    = align;
    }

    public String getKey()      { return key; }
    public String getHeader()   { return header; }
    public int    getWidthPct() { return widthPct; }
    public Align  getAlign()    { return align; }

    public static Align alignFromString(String v) {
        if (v == null) return Align.LEFT;
        return switch (v.trim().toUpperCase()) {
            case "CENTER" -> Align.CENTER;
            case "RIGHT"  -> Align.RIGHT;
            default       -> Align.LEFT;
        };
    }

    @Override
    public String toString() {
        return String.format("ColumnDef[key=%s, header=%s, width=%d%%, align=%s]",
            key, header, widthPct, align);
    }
}
