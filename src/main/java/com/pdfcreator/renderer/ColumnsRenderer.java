package com.pdfcreator.renderer;

import com.pdfcreator.config.PdfConfig;
import com.pdfcreator.datasource.DocumentData;
import com.pdfcreator.pdfa.FontLoader;
import com.pdfcreator.generator.PageContext;
import com.pdfcreator.template.ColumnsSection;
import com.pdfcreator.template.TemplateSection;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Renders a COLUMNS section — two-column side-by-side layout.
 * FontLoader is threaded through to all sub-renderer calls.
 */
public class ColumnsRenderer implements SectionRenderer {

    private static final Logger logger = Logger.getLogger(ColumnsRenderer.class.getName());

    private final SectionRendererRegistry registry;

    public ColumnsRenderer(SectionRendererRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void render(TemplateSection section, PageContext ctx, PdfConfig config,
                       DocumentData data, PDDocument document,
                       FontLoader fontLoader) throws IOException {

        ColumnsSection cols = section.getColumnsData();
        if (cols == null) {
            logger.warning("COLUMNS section has no columnsData. Skipping.");
            return;
        }

        float usableWidth = ctx.getUsableWidth();
        float leftWidth   = usableWidth * cols.getLeftWidthPct()  / 100f;
        float rightWidth  = usableWidth * cols.getRightWidthPct() / 100f;
        float leftStartX  = config.getMarginLeft();
        float rightStartX = leftStartX + leftWidth + 8f;

        float startY = ctx.getYPos();

        float leftEndY = renderColumn(cols.getLeft(), ctx, config, data, document, fontLoader,
                                      leftStartX, leftWidth);

        ctx.setYPos(startY);
        float rightEndY = renderColumn(cols.getRight(), ctx, config, data, document, fontLoader,
                                       rightStartX, rightWidth);

        float endY = Math.min(leftEndY, rightEndY);
        ctx.setYPos(endY - 6);

        logger.info(String.format("Rendered COLUMNS block: left=%d, right=%d sections",
            cols.getLeft().size(), cols.getRight().size()));
    }

    private float renderColumn(List<TemplateSection> sections,
                                PageContext ctx, PdfConfig config,
                                DocumentData data, PDDocument document,
                                FontLoader fontLoader,
                                float columnX, float columnWidth) throws IOException {

        PdfConfig columnConfig = new PdfConfig.Builder(config)
            .marginLeft(columnX)
            .marginRight(ctx.getPageWidth() - columnX - columnWidth)
            .build();

        for (TemplateSection section : sections) {
            if (section.getType() == TemplateSection.Type.COLUMNS  ||
                section.getType() == TemplateSection.Type.PAGEBREAK ||
                section.getType() == TemplateSection.Type.TABLE) {
                logger.warning("Section type " + section.getType() + " not supported inside COLUMNS. Skipping.");
                continue;
            }
            registry.get(section.getType()).render(section, ctx, columnConfig, data, document, fontLoader);
        }
        return ctx.getYPos();
    }
}
