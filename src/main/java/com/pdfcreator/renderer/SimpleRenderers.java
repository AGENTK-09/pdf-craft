package com.pdfcreator.renderer;

import com.pdfcreator.config.PdfConfig;
import com.pdfcreator.datasource.DocumentData;
import com.pdfcreator.generator.ColorUtil;
import com.pdfcreator.generator.PageContext;
import com.pdfcreator.template.TemplateSection;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * All simple (non-table) section renderer implementations.
 * Each is a small focused class implementing SectionRenderer.
 */
public class SimpleRenderers {

    private static final Logger logger = Logger.getLogger(SimpleRenderers.class.getName());

    // -----------------------------------------------------------------------
    // HEADING
    // -----------------------------------------------------------------------

    public static class HeadingRenderer implements SectionRenderer {
        @Override
        public void render(TemplateSection section, PageContext ctx, PdfConfig config,
                           DocumentData data, PDDocument document) throws IOException {
            String text = section.getContent();
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
    }

    // -----------------------------------------------------------------------
    // SUBHEADING
    // -----------------------------------------------------------------------

    public static class SubheadingRenderer implements SectionRenderer {
        @Override
        public void render(TemplateSection section, PageContext ctx, PdfConfig config,
                           DocumentData data, PDDocument document) throws IOException {
            String text = section.getContent();
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
    }

    // -----------------------------------------------------------------------
    // BODY
    // -----------------------------------------------------------------------

    public static class BodyRenderer implements SectionRenderer {
        @Override
        public void render(TemplateSection section, PageContext ctx, PdfConfig config,
                           DocumentData data, PDDocument document) throws IOException {
            String text = section.getContent();
            if (text == null || text.isBlank()) return;

            PDType1Font font    = fontFromFamily(config.getFontFamily());
            int fontSize        = config.getBodyFontSize();
            float lineHeight    = fontSize * config.getLineSpacing();
            Color color         = ColorUtil.fromHex(config.getFontColor(), Color.BLACK);

            for (String line : RenderUtil.wrapText(text, font, fontSize, ctx.getUsableWidth())) {
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
    }

    // -----------------------------------------------------------------------
    // DIVIDER
    // -----------------------------------------------------------------------

    public static class DividerRenderer implements SectionRenderer {
        @Override
        public void render(TemplateSection section, PageContext ctx, PdfConfig config,
                           DocumentData data, PDDocument document) throws IOException {
            Color color = ColorUtil.fromHex(config.getTitleColor(), Color.BLACK);
            PDPageContentStream cs = ctx.getContentStream();
            cs.setStrokingColor(color);
            cs.setLineWidth(0.5f);
            cs.moveTo(config.getMarginLeft(), ctx.getYPos());
            cs.lineTo(ctx.getPageWidth() - config.getMarginRight(), ctx.getYPos());
            cs.stroke();
            ctx.advanceY(12);
        }
    }

    // -----------------------------------------------------------------------
    // SPACER
    // -----------------------------------------------------------------------

    public static class SpacerRenderer implements SectionRenderer {
        @Override
        public void render(TemplateSection section, PageContext ctx, PdfConfig config,
                           DocumentData data, PDDocument document) throws IOException {
            ctx.advanceY(config.getBodyFontSize() * config.getLineSpacing());
        }
    }

    // -----------------------------------------------------------------------
    // PAGEBREAK
    // -----------------------------------------------------------------------

    public static class PageBreakRenderer implements SectionRenderer {
        @Override
        public void render(TemplateSection section, PageContext ctx, PdfConfig config,
                           DocumentData data, PDDocument document) throws IOException {
            // Force a new page by advancing past the bottom margin
            ctx.advanceY(ctx.getYPos());
        }
    }

    // -----------------------------------------------------------------------
    // NOTICE — boxed text with background color
    // -----------------------------------------------------------------------

    public static class NoticeRenderer implements SectionRenderer {
        private static final float PADDING = 8f;

        @Override
        public void render(TemplateSection section, PageContext ctx, PdfConfig config,
                           DocumentData data, PDDocument document) throws IOException {
            String text = section.getContent();
            if (text == null || text.isBlank()) return;

            PDType1Font font   = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
            int fontSize       = config.getBodyFontSize();
            float lineHeight   = fontSize * config.getLineSpacing();
            float usableWidth  = ctx.getUsableWidth() - (PADDING * 2);
            Color bgColor      = ColorUtil.fromHex(
                section.getBgColor() != null ? section.getBgColor() : "#FFF8DC", Color.WHITE);
            Color textColor    = ColorUtil.fromHex(config.getFontColor(), Color.BLACK);
            Color borderColor  = ColorUtil.fromHex(config.getTitleColor(), Color.GRAY);

            java.util.List<String> lines = RenderUtil.wrapText(text, font, fontSize, usableWidth);
            float boxHeight = (lines.size() * lineHeight) + (PADDING * 2);

            if (ctx.wouldOverflow(boxHeight)) ctx.advanceY(ctx.getYPos());

            float boxX = config.getMarginLeft();
            float boxY = ctx.getYPos() - boxHeight;
            float boxW = ctx.getUsableWidth();

            PDPageContentStream cs = ctx.getContentStream();

            // Fill background
            cs.setNonStrokingColor(bgColor);
            cs.addRect(boxX, boxY, boxW, boxHeight);
            cs.fill();

            // Draw border
            cs.setStrokingColor(borderColor);
            cs.setLineWidth(0.75f);
            cs.addRect(boxX, boxY, boxW, boxHeight);
            cs.stroke();

            // Draw text
            float textY = ctx.getYPos() - PADDING - lineHeight + 2;
            for (String line : lines) {
                cs.beginText();
                cs.setFont(font, fontSize);
                cs.setNonStrokingColor(textColor);
                cs.newLineAtOffset(boxX + PADDING, textY);
                cs.showText(line);
                cs.endText();
                textY -= lineHeight;
            }

            ctx.setYPos(boxY - 8);
        }
    }

    // -----------------------------------------------------------------------
    // IMAGE
    // -----------------------------------------------------------------------

    public static class ImageRenderer implements SectionRenderer {
        @Override
        public void render(TemplateSection section, PageContext ctx, PdfConfig config,
                           DocumentData data, PDDocument document) throws IOException {
            String imagePath = section.getContent();
            if (imagePath == null || imagePath.isBlank()) return;

            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                logger.warning("Image not found, skipping: " + imagePath);
                System.err.println("Warning: Image not found: " + imagePath);
                return;
            }

            PDImageXObject image = PDImageXObject.createFromFile(imagePath, document);
            float usableWidth = ctx.getUsableWidth();
            float maxWidth    = usableWidth * Math.max(1, Math.min(100, section.getWidthPercent())) / 100f;
            float scale       = Math.min(1.0f, maxWidth / image.getWidth());
            float drawWidth   = image.getWidth()  * scale;
            float drawHeight  = image.getHeight() * scale;

            ctx.advanceY(8);
            if (ctx.wouldOverflow(drawHeight)) ctx.advanceY(ctx.getYPos());

            float x = switch (section.getAlign()) {
                case RIGHT  -> ctx.getPageWidth() - config.getMarginRight() - drawWidth;
                case CENTER -> config.getMarginLeft() + (usableWidth - drawWidth) / 2f;
                default     -> config.getMarginLeft();
            };
            float y = ctx.getYPos() - drawHeight;

            ctx.getContentStream().drawImage(image, x, y, drawWidth, drawHeight);
            logger.info(String.format("Image: %s → %.0fx%.0f at (%s)", imagePath, drawWidth, drawHeight, section.getAlign()));
            ctx.setYPos(y - 8);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    static PDType1Font fontFromFamily(String fontFamily) {
        if (fontFamily == null) return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        return switch (fontFamily.toUpperCase()) {
            case "TIMES_ROMAN" -> new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            case "COURIER"     -> new PDType1Font(Standard14Fonts.FontName.COURIER);
            default            -> new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        };
    }
}
