package com.pdfcreator.renderer;

import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared utility methods used across multiple SectionRenderer implementations.
 */
public class RenderUtil {

    private RenderUtil() {}

    /**
     * Word-wraps text to fit within maxWidth points.
     * Each \n in the input produces a paragraph break (blank line inserted).
     */
    public static List<String> wrapText(String text, PDFont font,
                                         int fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;

        for (String paragraph : text.split("\n")) {
            String[] words = paragraph.trim().split(" ");
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                if (word.isBlank()) continue;
                String test  = current.isEmpty() ? word : current + " " + word;
                float  width = font.getStringWidth(test) / 1000f * fontSize;
                if (width > maxWidth && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(test);
                }
            }
            if (!current.isEmpty()) lines.add(current.toString());
            lines.add(""); // blank line between paragraphs
        }
        return lines;
    }

    /**
     * Truncates text to fit within maxWidth, appending "…" if truncated.
     * Used for table cells where overflow would break column alignment.
     */
    public static String truncateToFit(String text, PDFont font,
                                        int fontSize, float maxWidth) throws IOException {
        if (text == null) return "";
        float width = font.getStringWidth(text) / 1000f * fontSize;
        if (width <= maxWidth) return text;

        // Binary-search the truncation point
        String ellipsis = "…";
        float ellipsisW = font.getStringWidth(ellipsis) / 1000f * fontSize;
        float budget = maxWidth - ellipsisW;

        int lo = 0, hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            float w = font.getStringWidth(text.substring(0, mid)) / 1000f * fontSize;
            if (w <= budget) lo = mid; else hi = mid - 1;
        }
        return text.substring(0, lo) + ellipsis;
    }

    /**
     * Calculates the X offset for text within a cell based on alignment.
     *
     * @param cellX      left edge of the cell
     * @param cellWidth  width of the cell
     * @param textWidth  rendered width of the text
     * @param align      desired alignment
     * @param padding    horizontal padding inside the cell
     */
    public static float alignedX(float cellX, float cellWidth, float textWidth,
                                  com.pdfcreator.template.ColumnDef.Align align, float padding) {
        return switch (align) {
            case RIGHT  -> cellX + cellWidth - textWidth - padding;
            case CENTER -> cellX + (cellWidth - textWidth) / 2f;
            default     -> cellX + padding;
        };
    }
}
