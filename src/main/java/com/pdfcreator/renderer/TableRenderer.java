package com.pdfcreator.renderer;

import com.pdfcreator.config.PdfConfig;
import com.pdfcreator.datasource.DocumentData;
import com.pdfcreator.pdfa.FontLoader;
import com.pdfcreator.generator.ColorUtil;
import com.pdfcreator.generator.PageContext;
import com.pdfcreator.template.ColumnDef;
import com.pdfcreator.template.TemplateSection;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.awt.Color;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Renders a TABLE section from a named list in DocumentData.
 *
 * Features:
 *   - Column headers with configurable background color
 *   - Alternating row colors (zebra striping)
 *   - Per-column alignment (left / center / right)
 *   - Cell content truncated with ellipsis if too wide
 *   - Header row repeated at the top of each new page
 *   - Gracefully handles empty data (renders "No data available" row)
 *
 * All font access goes through FontLoader — supports both PDF/A and standard modes.
 */
public class TableRenderer implements SectionRenderer {

    private static final Logger logger = Logger.getLogger(TableRenderer.class.getName());

    private static final float ROW_PADDING_V  = 4f;
    private static final float CELL_PADDING_H = 4f;

    @Override
    public void render(TemplateSection section, PageContext ctx, PdfConfig config,
                       DocumentData data, PDDocument document,
                       FontLoader fontLoader) throws IOException {

        List<ColumnDef>           columns = section.getColumns();
        List<Map<String, String>> rows    = data.getList(section.getDataKey());

        if (columns == null || columns.isEmpty()) {
            logger.warning("TABLE section has no columns defined. Skipping.");
            return;
        }

        PDFont headerFont = fontLoader.boldFor(config.getFontFamily());
        PDFont bodyFont   = fontLoader.regularFor(config.getFontFamily());
        int    fontSize   = config.getBodyFontSize();
        float  rowHeight  = fontSize + (ROW_PADDING_V * 2);
        float  usableW    = ctx.getUsableWidth();

        // Calculate absolute column widths from percentages
        float totalPct    = columns.stream().mapToInt(ColumnDef::getWidthPct).sum();
        float[] colWidths = new float[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            colWidths[i] = (columns.get(i).getWidthPct() / totalPct) * usableW;
        }

        Color headerBg  = ColorUtil.fromHex(section.getHeaderBgColor(), new Color(0x003366));
        Color headerFg  = Color.WHITE;
        Color altRowBg  = ColorUtil.fromHex(section.getAlternateRowColor(), new Color(0xF5F5F5));
        Color bodyFg    = ColorUtil.fromHex(config.getFontColor(), Color.BLACK);
        Color gridColor = new Color(0xCCCCCC);

        ctx.advanceY(6);

        drawHeaderRow(ctx, config, headerFont, fontSize, rowHeight,
                      colWidths, columns, headerBg, headerFg, gridColor);

        if (rows.isEmpty()) {
            drawEmptyRow(ctx, config, bodyFont, fontSize, rowHeight,
                         usableW, bodyFg, gridColor);
        } else {
            for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
                if (ctx.wouldOverflow(rowHeight)) {
                    ctx.advanceY(ctx.getYPos());
                    if (section.isRepeatHeaderOnPage()) {
                        ctx.advanceY(4);
                        drawHeaderRow(ctx, config, headerFont, fontSize, rowHeight,
                                      colWidths, columns, headerBg, headerFg, gridColor);
                    }
                }
                boolean isAltRow = (rowIdx % 2 == 1);
                drawDataRow(ctx, config, bodyFont, fontSize, rowHeight,
                            colWidths, columns, rows.get(rowIdx),
                            isAltRow ? altRowBg : null, bodyFg, gridColor);
            }
        }

        drawHorizontalLine(ctx, config, ctx.getYPos(), usableW, gridColor);
        ctx.advanceY(8);

        logger.info(String.format("Rendered table: dataKey=%s, %d rows, %d columns",
            section.getDataKey(), rows.size(), columns.size()));
    }

    // -----------------------------------------------------------------------
    // Row drawing
    // -----------------------------------------------------------------------

    private void drawHeaderRow(PageContext ctx, PdfConfig config,
                                PDFont font, int fontSize, float rowHeight,
                                float[] colWidths, List<ColumnDef> columns,
                                Color bgColor, Color fgColor, Color gridColor) throws IOException {
        float startY = ctx.getYPos();
        float startX = config.getMarginLeft();
        PDPageContentStream cs = ctx.getContentStream();

        cs.setNonStrokingColor(bgColor);
        cs.addRect(startX, startY - rowHeight, ctx.getUsableWidth(), rowHeight);
        cs.fill();

        drawHorizontalLine(ctx, config, startY, ctx.getUsableWidth(), gridColor);

        float x = startX;
        for (int i = 0; i < columns.size(); i++) {
            ColumnDef col  = columns.get(i);
            String    text = col.getHeader();
            float     textW = font.getStringWidth(text) / 1000f * fontSize;
            float     textX = RenderUtil.alignedX(x, colWidths[i], textW, ColumnDef.Align.LEFT, CELL_PADDING_H);
            float     textY = startY - rowHeight + ROW_PADDING_V;

            cs.beginText();
            cs.setFont(font, fontSize);
            cs.setNonStrokingColor(fgColor);
            cs.newLineAtOffset(textX, textY);
            cs.showText(text);
            cs.endText();

            if (i < columns.size() - 1) {
                drawVerticalLine(cs, x + colWidths[i], startY, rowHeight, gridColor);
            }
            x += colWidths[i];
        }

        drawHorizontalLine(ctx, config, startY - rowHeight, ctx.getUsableWidth(), gridColor);
        ctx.setYPos(startY - rowHeight);
    }

    private void drawDataRow(PageContext ctx, PdfConfig config,
                              PDFont font, int fontSize, float rowHeight,
                              float[] colWidths, List<ColumnDef> columns,
                              Map<String, String> row,
                              Color bgColor, Color fgColor, Color gridColor) throws IOException {
        float startY = ctx.getYPos();
        float startX = config.getMarginLeft();
        PDPageContentStream cs = ctx.getContentStream();

        if (bgColor != null) {
            cs.setNonStrokingColor(bgColor);
            cs.addRect(startX, startY - rowHeight, ctx.getUsableWidth(), rowHeight);
            cs.fill();
        }

        float x = startX;
        for (int i = 0; i < columns.size(); i++) {
            ColumnDef col     = columns.get(i);
            String rawValue   = row.getOrDefault(col.getKey(), "");
            String text       = RenderUtil.truncateToFit(rawValue, font, fontSize,
                                                          colWidths[i] - (CELL_PADDING_H * 2));
            float  textW      = font.getStringWidth(text) / 1000f * fontSize;
            float  textX      = RenderUtil.alignedX(x, colWidths[i], textW, col.getAlign(), CELL_PADDING_H);
            float  textY      = startY - rowHeight + ROW_PADDING_V;

            cs.beginText();
            cs.setFont(font, fontSize);
            cs.setNonStrokingColor(fgColor);
            cs.newLineAtOffset(textX, textY);
            cs.showText(text);
            cs.endText();

            if (i < columns.size() - 1) {
                drawVerticalLine(cs, x + colWidths[i], startY, rowHeight, gridColor);
            }
            x += colWidths[i];
        }
        ctx.setYPos(startY - rowHeight);
    }

    private void drawEmptyRow(PageContext ctx, PdfConfig config,
                               PDFont font, int fontSize, float rowHeight,
                               float usableWidth, Color fgColor, Color gridColor) throws IOException {
        float startY = ctx.getYPos();
        PDPageContentStream cs = ctx.getContentStream();
        float textX = config.getMarginLeft() + CELL_PADDING_H;
        float textY = startY - rowHeight + ROW_PADDING_V;

        cs.beginText();
        cs.setFont(font, fontSize);
        cs.setNonStrokingColor(fgColor);
        cs.newLineAtOffset(textX, textY);
        cs.showText("No data available");
        cs.endText();

        ctx.setYPos(startY - rowHeight);
    }

    // -----------------------------------------------------------------------
    // Grid line helpers
    // -----------------------------------------------------------------------

    private void drawHorizontalLine(PageContext ctx, PdfConfig config,
                                     float y, float width, Color color) throws IOException {
        PDPageContentStream cs = ctx.getContentStream();
        cs.setStrokingColor(color);
        cs.setLineWidth(0.3f);
        cs.moveTo(config.getMarginLeft(), y);
        cs.lineTo(config.getMarginLeft() + width, y);
        cs.stroke();
    }

    private void drawVerticalLine(PDPageContentStream cs,
                                   float x, float topY, float height, Color color) throws IOException {
        cs.setStrokingColor(color);
        cs.setLineWidth(0.3f);
        cs.moveTo(x, topY);
        cs.lineTo(x, topY - height);
        cs.stroke();
    }
}
