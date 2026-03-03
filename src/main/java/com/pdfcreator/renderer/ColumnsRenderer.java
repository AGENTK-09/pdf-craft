package com.pdfcreator.renderer;

import com.pdfcreator.config.PdfConfig;
import com.pdfcreator.datasource.DocumentData;
import com.pdfcreator.generator.PageContext;
import com.pdfcreator.template.ColumnsSection;
import com.pdfcreator.template.TemplateSection;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Renders a COLUMNS section — a two-column side-by-side layout block.
 *
 * Strategy:
 *   1. Record the Y start position
 *   2. Render the left column using a clipped virtual PageContext
 *      that constrains drawing to [marginLeft .. marginLeft + leftWidth]
 *   3. Reset Y to the start position
 *   4. Render the right column using a virtual PageContext
 *      that constrains drawing to [marginLeft + leftWidth .. marginLeft + usableWidth]
 *   5. Advance cursor past whichever column was taller
 *
 * The "virtual context" approach reuses the same underlying PDDocument and
 * PDPageContentStream — only the X offset and usable width differ.
 * No page break can occur mid-columns block (the block starts a new page first
 * if it wouldn't fit, then renders fully on that page).
 *
 * Limitation: if a single column's content exceeds a full page, it overflows
 * without page-breaking (acceptable for address/summary blocks which are always short).
 */
public class ColumnsRenderer implements SectionRenderer {

    private static final Logger logger = Logger.getLogger(ColumnsRenderer.class.getName());

    private final SectionRendererRegistry registry;

    public ColumnsRenderer(SectionRendererRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void render(TemplateSection section, PageContext ctx, PdfConfig config,
                       DocumentData data, PDDocument document) throws IOException {

        ColumnsSection cols = section.getColumnsData();
        if (cols == null) {
            logger.warning("COLUMNS section has no columnsData. Skipping.");
            return;
        }

        float usableWidth = ctx.getUsableWidth();
        float leftWidth   = usableWidth * cols.getLeftWidthPct()  / 100f;
        float rightWidth  = usableWidth * cols.getRightWidthPct() / 100f;
        float leftStartX  = config.getMarginLeft();
        float rightStartX = leftStartX + leftWidth + 8f; // 8pt gutter

        float startY = ctx.getYPos();

        // Render left column — virtualised with its own X offset and width
        float leftEndY = renderColumn(cols.getLeft(), ctx, config, data, document,
                                      leftStartX, leftWidth, startY);

        // Reset Y, render right column
        ctx.setYPos(startY);
        float rightEndY = renderColumn(cols.getRight(), ctx, config, data, document,
                                       rightStartX, rightWidth, startY);

        // Advance past the taller of the two columns
        float endY = Math.min(leftEndY, rightEndY); // lower Y = more content
        ctx.setYPos(endY - 6); // 6pt gap below

        logger.info(String.format("Rendered COLUMNS block: left=%d sections, right=%d sections, startY=%.0f, endY=%.0f",
            cols.getLeft().size(), cols.getRight().size(), startY, endY));
    }

    /**
     * Renders a list of sections into a virtual column with a constrained X and width.
     * Returns the Y position after rendering (lowest Y reached).
     */
    private float renderColumn(List<TemplateSection> sections,
                                PageContext ctx, PdfConfig config,
                                DocumentData data, PDDocument document,
                                float columnX, float columnWidth, float startY) throws IOException {

        // Build a column-scoped PdfConfig that overrides margins to match the column
        PdfConfig columnConfig = new PdfConfig.Builder(config)
            .marginLeft(columnX)
            .marginRight(ctx.getPageWidth() - columnX - columnWidth)
            .build();

        for (TemplateSection section : sections) {
            // Skip types that don't make sense inside a column
            if (section.getType() == TemplateSection.Type.COLUMNS  ||
                section.getType() == TemplateSection.Type.PAGEBREAK ||
                section.getType() == TemplateSection.Type.TABLE) {
                logger.warning("Section type " + section.getType() + " not supported inside COLUMNS. Skipping.");
                continue;
            }
            registry.get(section.getType()).render(section, ctx, columnConfig, data, document);
        }

        return ctx.getYPos();
    }
}
