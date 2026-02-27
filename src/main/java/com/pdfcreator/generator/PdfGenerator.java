package com.pdfcreator.generator;

import com.pdfcreator.config.PdfConfig;
import com.pdfcreator.template.PageHeader;
import com.pdfcreator.template.TemplateSection;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Generates PDF documents from a PdfConfig preset and provided content.
 *
 * Features:
 *   - Multi-page support: text automatically flows onto new pages
 *   - Image embedding: images are scaled to fit within page margins
 *   - Font colors: configurable per-preset via hex strings
 *   - Background colors: configurable per-preset, applied to every page
 *   - Title underline with title color
 *
 * Entry point: generate(config, title, author, bodyText, imagePaths, outputPath)
 */

/**
 * Generates PDF documents from either:
 *   A) A list of resolved TemplateSection objects  (template mode)
 *   B) Raw title / author / bodyText strings       (legacy direct mode)
 *
 * New vs previous version:
 *   - generateFromSections() now accepts a PageHeader for per-page branding
 *   - renderImage() respects align and widthPercent from TemplateSection
 */
public class PdfGenerator {

    private static final Logger logger = Logger.getLogger(PdfGenerator.class.getName());

    // --- Entry point A: Template mode ---

    public void generateFromSections(PdfConfig config,
                                     List<TemplateSection> sections,
                                     PageHeader header,
                                     String outputPath) throws IOException {

        logger.info("Generating PDF from " + sections.size() + " template sections, config: " + config.getId());

        try (PDDocument document = new PDDocument()) {
            PageContext ctx = new PageContext(document, config,
                                             resolvePageSize(config.getPageSize()), header);
            ctx.open();

            for (TemplateSection section : sections) {
                renderSection(ctx, config, document, section);
            }

            ctx.close();
            document.save(outputPath);
            logger.info("PDF saved: " + outputPath + " (" + ctx.getPageNumber() + " page(s))");
            System.out.println("Pages generated: " + ctx.getPageNumber());
        }
    }

    // --- Entry point B: Legacy direct mode ---

    public void generate(PdfConfig config,
                         String title, String author, String bodyText,
                         List<String> imagePaths,
                         String outputPath) throws IOException {

        logger.info("Generating PDF (direct mode) with config: " + config.getId());

        try (PDDocument document = new PDDocument()) {
            if (title  != null) document.getDocumentInformation().setTitle(title);
            if (author != null) document.getDocumentInformation().setAuthor(author);

            PageContext ctx = new PageContext(document, config, resolvePageSize(config.getPageSize()));
            ctx.open();

            if (title    != null) renderHeading(ctx, config, title);
            if (author   != null) renderAuthor(ctx, config, author);
            if (bodyText != null && !bodyText.isBlank()) renderBody(ctx, config, bodyText);
            if (imagePaths != null) {
                for (String p : imagePaths)
                    renderImage(ctx, config, document, p, TemplateSection.Align.LEFT, 100);
            }

            ctx.close();
            document.save(outputPath);
            logger.info("PDF saved: " + outputPath + " (" + ctx.getPageNumber() + " page(s))");
            System.out.println("Pages generated: " + ctx.getPageNumber());
        }
    }

    // --- Section dispatcher ---

    private void renderSection(PageContext ctx, PdfConfig config,
                                PDDocument document, TemplateSection section) throws IOException {
        switch (section.getType()) {
            case HEADING    -> renderHeading(ctx, config, section.getContent());
            case SUBHEADING -> renderSubheading(ctx, config, section.getContent());
            case BODY       -> { if (section.getContent() != null) renderBody(ctx, config, section.getContent()); }
            case DIVIDER    -> renderDivider(ctx, config);
            case SPACER     -> renderSpacer(ctx, config);
            case IMAGE      -> { if (section.getContent() != null)
                                     renderImage(ctx, config, document, section.getContent(),
                                                 section.getAlign(), section.getWidthPercent()); }
        }
    }

    // --- Renderers ---

    private void renderHeading(PageContext ctx, PdfConfig config, String text) throws IOException {
        if (text == null || text.isBlank()) return;
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        int fontSize     = config.getTitleFontSize();
        Color color      = ColorUtil.fromHex(config.getTitleColor(), Color.BLACK);

        PDPageContentStream cs = ctx.getContentStream();
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(config.getMarginLeft(), ctx.getYPos());
        cs.showText(text);
        cs.endText();

        float underlineY = ctx.getYPos() - (fontSize * config.getLineSpacing()) - 2;
        cs.setStrokingColor(color);
        cs.moveTo(config.getMarginLeft(), underlineY);
        cs.lineTo(ctx.getPageWidth() - config.getMarginRight(), underlineY);
        cs.stroke();

        ctx.setYPos(underlineY - 12);
    }

    private void renderSubheading(PageContext ctx, PdfConfig config, String text) throws IOException {
        if (text == null || text.isBlank()) return;
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        int fontSize     = (int) ((config.getTitleFontSize() + config.getBodyFontSize()) / 2.0);
        Color color      = ColorUtil.fromHex(config.getTitleColor(), Color.BLACK);

        PDPageContentStream cs = ctx.getContentStream();
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(config.getMarginLeft(), ctx.getYPos());
        cs.showText(text);
        cs.endText();

        ctx.advanceY(fontSize * config.getLineSpacing() + 6);
    }

    private void renderBody(PageContext ctx, PdfConfig config, String bodyText) throws IOException {
        PDType1Font font   = fontFromFamily(config.getFontFamily());
        int fontSize       = config.getBodyFontSize();
        float lineHeight   = fontSize * config.getLineSpacing();
        Color color        = ColorUtil.fromHex(config.getFontColor(), Color.BLACK);
        List<String> lines = wrapText(bodyText, font, fontSize, ctx.getUsableWidth());

        for (String line : lines) {
            if (ctx.wouldOverflow(lineHeight)) ctx.advanceY(lineHeight);
            PDPageContentStream cs = ctx.getContentStream();
            cs.beginText();
            cs.setFont(font, fontSize);
            cs.setNonStrokingColor(color);
            cs.newLineAtOffset(config.getMarginLeft(), ctx.getYPos());
            cs.showText(line);
            cs.endText();
            ctx.advanceY(lineHeight);
        }
    }

    private void renderAuthor(PageContext ctx, PdfConfig config, String author) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
        int fontSize     = config.getBodyFontSize();
        Color color      = ColorUtil.fromHex(config.getFontColor(), Color.BLACK);

        PDPageContentStream cs = ctx.getContentStream();
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(config.getMarginLeft(), ctx.getYPos());
        cs.showText("Author: " + author);
        cs.endText();

        ctx.advanceY(fontSize * config.getLineSpacing() + 12);
    }

    private void renderDivider(PageContext ctx, PdfConfig config) throws IOException {
        Color color = ColorUtil.fromHex(config.getTitleColor(), Color.BLACK);
        PDPageContentStream cs = ctx.getContentStream();
        cs.setStrokingColor(color);
        cs.setLineWidth(0.5f);
        cs.moveTo(config.getMarginLeft(), ctx.getYPos());
        cs.lineTo(ctx.getPageWidth() - config.getMarginRight(), ctx.getYPos());
        cs.stroke();
        ctx.advanceY(12);
    }

    private void renderSpacer(PageContext ctx, PdfConfig config) throws IOException {
        ctx.advanceY(config.getBodyFontSize() * config.getLineSpacing());
    }

    /**
     * Renders an image with configurable alignment and width.
     *
     * @param align        LEFT / CENTER / RIGHT within the usable page width
     * @param widthPercent 1–100: what percentage of the usable width to use
     */
    private void renderImage(PageContext ctx, PdfConfig config, PDDocument document,
                              String imagePath,
                              TemplateSection.Align align, int widthPercent) throws IOException {
        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            logger.warning("Image not found, skipping: " + imagePath);
            System.err.println("Warning: Image not found: " + imagePath);
            return;
        }

        PDImageXObject image = PDImageXObject.createFromFile(imagePath, document);

        float usableWidth  = ctx.getUsableWidth();
        float maxWidth     = usableWidth * Math.max(1, Math.min(100, widthPercent)) / 100f;

        // Scale proportionally to fit within maxWidth
        float scale      = Math.min(1.0f, maxWidth / image.getWidth());
        float drawWidth  = image.getWidth()  * scale;
        float drawHeight = image.getHeight() * scale;

        ctx.advanceY(12); // spacing before image
        if (ctx.wouldOverflow(drawHeight)) ctx.advanceY(ctx.getYPos()); // force new page

        // Calculate X based on alignment
        float x = switch (align) {
            case LEFT   -> config.getMarginLeft();
            case RIGHT  -> ctx.getPageWidth() - config.getMarginRight() - drawWidth;
            case CENTER -> config.getMarginLeft() + (usableWidth - drawWidth) / 2f;
        };

        float y = ctx.getYPos() - drawHeight;
        ctx.getContentStream().drawImage(image, x, y, drawWidth, drawHeight);

        logger.info(String.format("Embedded image: %s (%.0fx%.0f, align=%s, width=%d%%)",
            imagePath, drawWidth, drawHeight, align, widthPercent));

        ctx.setYPos(y - 8);
    }

    // --- Helpers ---

    private PDRectangle resolvePageSize(String pageSize) {
        return switch (pageSize.toUpperCase()) {
            case "LETTER" -> PDRectangle.LETTER;
            case "A3"     -> PDRectangle.A3;
            case "A5"     -> PDRectangle.A5;
            default       -> PDRectangle.A4;
        };
    }

    private PDType1Font fontFromFamily(String fontFamily) {
        return switch (fontFamily.toUpperCase()) {
            case "TIMES_ROMAN" -> new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            case "COURIER"     -> new PDType1Font(Standard14Fonts.FontName.COURIER);
            default            -> new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        };
    }

    private List<String> wrapText(String text, PDType1Font font, int fontSize,
                                   float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\n")) {
            String[] words = paragraph.trim().split(" ");
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                if (word.isBlank()) continue;
                String test = current.isEmpty() ? word : current + " " + word;
                float width = font.getStringWidth(test) / 1000f * fontSize;
                if (width > maxWidth && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(test);
                }
            }
            if (!current.isEmpty()) lines.add(current.toString());
            lines.add("");
        }
        return lines;
    }
}
