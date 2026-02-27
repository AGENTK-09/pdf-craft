package com.pdfcreator.generator;

import java.awt.Color;

/**
 * Utility for converting hex color strings to java.awt.Color objects
 * which PDFBox uses for setNonStrokingColor / setStrokingColor.
 *
 * Supported formats:
 *   "#RRGGBB"   e.g. "#003366"
 *   "RRGGBB"    e.g. "003366"   (# is optional)
 */
public class ColorUtil {

    private ColorUtil() {}

    /**
     * Parses a hex color string. Returns the fallback color if parsing fails.
     */
    public static Color fromHex(String hex, Color fallback) {
        if (hex == null || hex.isBlank()) return fallback;
        try {
            String clean = hex.trim().replace("#", "");
            int r = Integer.parseInt(clean.substring(0, 2), 16);
            int g = Integer.parseInt(clean.substring(2, 4), 16);
            int b = Integer.parseInt(clean.substring(4, 6), 16);
            return new Color(r, g, b);
        } catch (Exception e) {
            System.err.println("Warning: Invalid color value '" + hex + "', using fallback.");
            return fallback;
        }
    }

    public static Color fromHex(String hex) {
        return fromHex(hex, Color.BLACK);
    }
}
