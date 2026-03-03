package com.pdfcreator.pipeline;

import com.pdfcreator.config.PdfConfig;
import com.pdfcreator.datasource.DataSource;
import com.pdfcreator.datasource.DocumentData;
import com.pdfcreator.generator.ColorUtil;
import com.pdfcreator.generator.PageContext;
import com.pdfcreator.renderer.SectionRenderer;
import com.pdfcreator.renderer.SectionRendererRegistry;
import com.pdfcreator.service.ConfigService;
import com.pdfcreator.template.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.Color;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * The single orchestrator for the full render pipeline.
 *
 * Steps:
 *   1. Load template  (TemplateService → cache → file)
 *   2. Load data      (DataSource → DocumentData)
 *   3. Resolve {{placeholders}} in sections, header, footer
 *   4. Load config    (ConfigService → cache → file)
 *   5. Render all sections via SectionRendererRegistry
 *   6. Stamp page footers in a second pass (page count known after step 5)
 *   7. Save PDF
 *
 * Added vs previous version:
 *   - Resolves and stamps the configurable PageFooter from the template
 *   - Footer text supports {{placeholder}} substitution from DocumentData
 */
public class RenderPipeline {

    private static final Logger logger = Logger.getLogger(RenderPipeline.class.getName());

    private final TemplateService         templateService;
    private final ConfigService           configService;
    private final SectionRendererRegistry registry;

    public RenderPipeline(String templateFilePath, String configFilePath) {
        this.templateService = new TemplateService(templateFilePath);
        this.configService   = new ConfigService(configFilePath);
        this.registry        = new SectionRendererRegistry();
    }

    public void render(String templateId, DataSource dataSource, String outputPath) throws IOException {

        // 1. Load template
        PdfTemplate template = templateService.getTemplate(templateId);
        logger.info("Template: " + template);

        // 2. Load data
        DocumentData data = dataSource.load();
        logger.info("Data: " + data);

        // 3. Resolve placeholders
        PlaceholderResolver resolver = new PlaceholderResolver(data);

        List<String> missing = resolver.findMissingKeys(template.getSections());
        if (!missing.isEmpty())
            System.err.println("Warning: Unresolved placeholders: " + missing);

        List<TemplateSection> sections = resolver.resolveSections(template.getSections());
        PageHeader header = resolver.resolveHeader(template.getHeader());
        PageFooter footer = resolver.resolveFooter(template.getFooter());

        // 4. Load config
        PdfConfig config = configService.getConfig(template.getConfigId());

        System.out.printf("Template : %s — %s%n", template.getId(), template.getDescription());
        System.out.printf("Data     : %s%n", dataSource.describe());
        System.out.printf("Config   : %s%n", config.getId());
        System.out.printf("Header   : %s%n", header != null ? header : "none");
        System.out.printf("Footer   : %s%n", footer != null ? footer : "none");
        System.out.printf("Output   : %s%n", outputPath);
        System.out.printf("Sections : %d%n", sections.size());

        // 5. Render
        PDRectangle pageSize = resolvePageSize(config.getPageSize());

        try (PDDocument document = new PDDocument()) {
            document.getDocumentInformation().setTitle(template.getDescription());

            // Reserve bottom space for footer band before PageContext is created
            PdfConfig renderConfig = footer != null && footer.hasBand()
                ? new PdfConfig.Builder(config)
                      .marginBottom(config.getMarginBottom() + footer.getBandHeight())
                      .build()
                : config;

            PageContext ctx = new PageContext(document, renderConfig, pageSize, header);
            ctx.open();

            for (TemplateSection section : sections) {
                SectionRenderer renderer = registry.get(section.getType());
                renderer.render(section, ctx, renderConfig, data, document);
            }

            ctx.close();

            // 6. Stamp page footers
            int totalPages = ctx.getPageNumber();
            stampFooters(document, config, footer, totalPages, pageSize);

            document.save(outputPath);
            logger.info("PDF saved: " + outputPath + " (" + totalPages + " page(s))");
            System.out.println("Pages: " + totalPages + " → " + outputPath);
        }
    }

    // -----------------------------------------------------------------------
    // Footer stamping — second pass, total page count known
    // -----------------------------------------------------------------------

    private void stampFooters(PDDocument document, PdfConfig config,
                               PageFooter footer, int totalPages,
                               PDRectangle pageSize) throws IOException {

        // Determine what to show
        boolean showPageNos = (footer == null) ? totalPages > 1 : footer.isShowPageNumbers();
        if (!showPageNos && footer == null) return;

        PDType1Font font  = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        int fontSize      = Math.max(7, (config.getBodyFontSize() - 3));
        Color fgColor     = ColorUtil.fromHex(config.getFontColor(), Color.DARK_GRAY);
        float bandHeight  = footer != null ? footer.getBandHeight() : 20f;
        float bandY       = config.getMarginBottom() / 2f - bandHeight / 2f;
        float pageW       = pageSize.getWidth();

        for (int i = 0; i < document.getNumberOfPages(); i++) {
            int pageNo = i + 1;

            try (PDPageContentStream cs = new PDPageContentStream(
                    document, document.getPage(i),
                    PDPageContentStream.AppendMode.APPEND, true)) {

                // Draw footer band background if configured
                if (footer != null && footer.hasBand()) {
                    Color bandColor = ColorUtil.fromHex(footer.getBandColor(), Color.LIGHT_GRAY);
                    cs.setNonStrokingColor(bandColor);
                    cs.addRect(0, bandY, pageW, bandHeight);
                    cs.fill();
                }

                float textY = bandY + (bandHeight - fontSize) / 2f;

                // Left text
                if (footer != null && footer.getLeftText() != null && !footer.getLeftText().isBlank()) {
                    cs.beginText();
                    cs.setFont(font, fontSize);
                    cs.setNonStrokingColor(footer.hasBand() ? Color.WHITE : fgColor);
                    cs.newLineAtOffset(config.getMarginLeft(), textY);
                    cs.showText(footer.getLeftText());
                    cs.endText();
                }

                // Center text (either footer centerText or "Page N of M")
                String centerStr = null;
                if (footer != null && footer.getCenterText() != null && !footer.getCenterText().isBlank()) {
                    centerStr = footer.getCenterText();
                } else if (showPageNos) {
                    String fmt = footer != null ? footer.getPageNumberFormat() : "Page {n} of {total}";
                    centerStr = fmt.replace("{n}", String.valueOf(pageNo))
                                   .replace("{total}", String.valueOf(totalPages));
                }
                if (centerStr != null) {
                    float centerW = font.getStringWidth(centerStr) / 1000f * fontSize;
                    cs.beginText();
                    cs.setFont(font, fontSize);
                    cs.setNonStrokingColor(footer != null && footer.hasBand() ? Color.WHITE : fgColor);
                    cs.newLineAtOffset((pageW - centerW) / 2f, textY);
                    cs.showText(centerStr);
                    cs.endText();
                }

                // Right text
                if (footer != null && footer.getRightText() != null && !footer.getRightText().isBlank()) {
                    String rightStr = footer.getRightText();
                    float rightW = font.getStringWidth(rightStr) / 1000f * fontSize;
                    cs.beginText();
                    cs.setFont(font, fontSize);
                    cs.setNonStrokingColor(footer.hasBand() ? Color.WHITE : fgColor);
                    cs.newLineAtOffset(pageW - config.getMarginRight() - rightW, textY);
                    cs.showText(rightStr);
                    cs.endText();
                }
            }
        }
    }

    private PDRectangle resolvePageSize(String pageSize) {
        return switch (pageSize.toUpperCase()) {
            case "LETTER" -> PDRectangle.LETTER;
            case "A3"     -> PDRectangle.A3;
            case "A5"     -> PDRectangle.A5;
            default       -> PDRectangle.A4;
        };
    }
}
