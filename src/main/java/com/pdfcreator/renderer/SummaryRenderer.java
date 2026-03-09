package com.pdfcreator.renderer;

import com.pdfcreator.config.PdfConfig;
import com.pdfcreator.datasource.DocumentData;
import com.pdfcreator.pdfa.FontLoader;
import com.pdfcreator.generator.ColorUtil;
import com.pdfcreator.generator.PageContext;
import com.pdfcreator.template.TemplateSection;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a SUMMARY section — a two-column key-value grid.
 *
 * Used for things like:
 *   Opening Balance    £4,210.00
 *   Total Debits      -£1,823.45
 *   Total Credits      £3,200.00
 *   Closing Balance    £5,586.55
 *
 * The content field contains newline-separated "Label: {{placeholder}}" pairs.
 * The placeholder values are already resolved before this renderer is called.
 *
 * Example template section:
 * {
 *   "type": "summary",
 *   "content": "Opening Balance: {{opening_balance}}\nTotal Debits: {{total_debits}}\nClosing Balance: {{closing_balance}}"
 * }
 */
public class SummaryRenderer implements SectionRenderer {

    private static final float LABEL_WIDTH_PCT  = 0.55f; // 55% of usable width for label
    private static final float ROW_PADDING_V    = 4f;
    private static final float CELL_PADDING_H   = 6f;

    @Override
    public void render(TemplateSection section, PageContext ctx, PdfConfig config,
                       DocumentData data, PDDocument document,
                       FontLoader fontLoader) throws IOException {

        String content = section.getContent();
        if (content == null || content.isBlank()) return;

        PDFont labelFont = fontLoader.regularFor(config.getFontFamily());
        PDFont valueFont = fontLoader.boldFor(config.getFontFamily());
        int fontSize  = config.getBodyFontSize();
        float rowH    = fontSize + (ROW_PADDING_V * 2);
        float usable  = ctx.getUsableWidth();
        float labelW  = usable * LABEL_WIDTH_PCT;
        float valueW  = usable - labelW;
        Color fgColor = ColorUtil.fromHex(config.getFontColor(), Color.BLACK);
        Color bgColor = ColorUtil.fromHex(config.getTitleColor(), new Color(0x003366));
        Color altBg   = new Color(0xF0F4F8);

        // Parse "Label: value" pairs
        List<String[]> pairs = parsePairs(content);
        float totalH = pairs.size() * rowH + 4;

        if (ctx.wouldOverflow(totalH)) ctx.advanceY(ctx.getYPos());

        ctx.advanceY(4);
        float startX = config.getMarginLeft();

        for (int i = 0; i < pairs.size(); i++) {
            String label = pairs.get(i)[0];
            String value = pairs.get(i)[1];
            float  rowY  = ctx.getYPos();
            boolean isLast = (i == pairs.size() - 1);

            PDPageContentStream cs = ctx.getContentStream();

            // Highlight last row (typically closing balance) differently
            if (isLast) {
                cs.setNonStrokingColor(bgColor);
                cs.addRect(startX, rowY - rowH, usable, rowH);
                cs.fill();
            } else if (i % 2 == 1) {
                cs.setNonStrokingColor(altBg);
                cs.addRect(startX, rowY - rowH, usable, rowH);
                cs.fill();
            }

            Color textColor = isLast ? Color.WHITE : fgColor;
            float textY     = rowY - rowH + ROW_PADDING_V;

            // Label (left-aligned)
            cs.beginText();
            cs.setFont(labelFont, fontSize);
            cs.setNonStrokingColor(textColor);
            cs.newLineAtOffset(startX + CELL_PADDING_H, textY);
            cs.showText(label);
            cs.endText();

            // Value (right-aligned)
            float valueTextW = valueFont.getStringWidth(value) / 1000f * fontSize;
            float valueX     = startX + labelW + valueW - valueTextW - CELL_PADDING_H;
            cs.beginText();
            cs.setFont(valueFont, fontSize);
            cs.setNonStrokingColor(textColor);
            cs.newLineAtOffset(valueX, textY);
            cs.showText(value);
            cs.endText();

            ctx.setYPos(rowY - rowH);
        }

        ctx.advanceY(8);
    }

    /** Parses "Label: value\nLabel: value" into a list of [label, value] pairs. */
    private List<String[]> parsePairs(String content) {
        List<String[]> pairs = new ArrayList<>();
        for (String line : content.split("\n")) {
            int colon = line.indexOf(':');
            if (colon == -1) continue;
            String label = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (!label.isBlank()) pairs.add(new String[]{ label, value });
        }
        return pairs;
    }
}
