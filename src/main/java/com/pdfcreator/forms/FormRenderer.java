package com.pdfcreator.forms;

import com.pdfcreator.config.PdfConfig;
import com.pdfcreator.datasource.DocumentData;
import com.pdfcreator.generator.ColorUtil;
import com.pdfcreator.generator.PageContext;
import com.pdfcreator.pdfa.FontLoader;
import com.pdfcreator.renderer.RenderUtil;
import com.pdfcreator.renderer.SectionRenderer;
import com.pdfcreator.template.TemplateSection;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;

import java.awt.Color;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * SectionRenderer for TemplateSection.Type.FORM.
 *
 * Lays out all form fields defined in the section's FormSection companion
 * object top-to-bottom, advancing the rendering cursor as it goes.
 *
 * For each FormFieldDef:
 *   1. Print the label text above the widget using the body font.
 *   2. Draw a light-grey background rectangle behind the widget area
 *      (makes the field visually distinct from body text).
 *   3. Call AcroFormBuilder.addField() to create the interactive widget
 *      annotation at the current cursor position.
 *   4. Advance the cursor by the widget height + fieldSpacing.
 *
 * ── RADIO OPTIONS LABEL ───────────────────────────────────────────────────
 *
 * For RADIO fields, each option is labelled inline to the right of its
 * button. The total height consumed is:
 *   options.size() × (toggleSize + 4pt gap) + fieldSpacing
 *
 * ── COORDINATE SYSTEM ─────────────────────────────────────────────────────
 *
 * PageContext.yPos is the top of the next thing to draw (PDF coordinates
 * with origin bottom-left, y increases upward).
 *
 * Widget rectangle lower-left = yPos - widgetHeight
 * Widget rectangle upper-right = (marginLeft + usableWidth, yPos)
 *
 * ── MULTI-PAGE ────────────────────────────────────────────────────────────
 *
 * If a field (label + widget) does not fit on the remaining page,
 * ctx.advanceY(ctx.getYPos()) is called to trigger a new page before
 * that field is rendered. The AcroForm widget is then placed on the new page.
 */
public class FormRenderer implements SectionRenderer {

    private static final Logger logger = Logger.getLogger(FormRenderer.class.getName());

    private static final float WIDGET_LABEL_GAP = 2f;   // gap between label text and widget top
    private static final float BG_PADDING       = 2f;   // extra padding around widget background

    private final AcroFormBuilder builder = new AcroFormBuilder();

    @Override
    public void render(TemplateSection section,
                       PageContext ctx,
                       PdfConfig config,
                       DocumentData data,
                       PDDocument document,
                       FontLoader fontLoader) throws IOException {

        FormSection formSection = section.getFormData();
        if (formSection == null || formSection.getFieldDefs().isEmpty()) {
            logger.warning("FORM section has no formData or empty fieldDefs — skipping");
            return;
        }

        // Ensure AcroForm exists on this document (idempotent)
        PDAcroForm acroForm = builder.ensureAcroForm(document, formSection.isNeedAppearances());

        PDFont  bodyFont  = fontLoader.regularFor(config.getFontFamily());
        int     fontSize  = formSection.getLabelFontSize() > 0
                            ? formSection.getLabelFontSize()
                            : config.getBodyFontSize();
        float   lineH     = fontSize * config.getLineSpacing();
        Color   textColor = ColorUtil.fromHex(config.getFontColor(), Color.BLACK);
        Color   bgColor   = new Color(0.94f, 0.94f, 0.94f);  // #F0F0F0 widget background
        Color   borderClr = ColorUtil.fromHex(config.getTitleColor(), Color.GRAY);

        float marginLeft  = config.getMarginLeft();
        float usableWidth = ctx.getUsableWidth();
        float fieldSpacing = formSection.getFieldSpacing();

        for (FormFieldDef fieldDef : formSection.getFieldDefs()) {

            float widgetH      = fieldDef.effectiveHeight(lineH);
            float labelH       = lineH;
            float totalNeeded  = labelH + WIDGET_LABEL_GAP + widgetH + fieldSpacing;

            // Page break before this field if it won't fit
            if (ctx.wouldOverflow(totalNeeded)) {
                ctx.advanceY(ctx.getYPos());   // triggers newPage()
            }

            // ── 1. Draw label ─────────────────────────────────────────────
            String label = fieldDef.getLabel();
            if (label != null && !label.isBlank()) {
                ctx.getContentStream().beginText();
                ctx.getContentStream().setFont(bodyFont, fontSize);
                ctx.getContentStream().setNonStrokingColor(textColor);
                ctx.getContentStream().newLineAtOffset(marginLeft, ctx.getYPos() - fontSize);
                ctx.getContentStream().showText(label +
                    (fieldDef.isRequired() ? " *" : ""));
                ctx.getContentStream().endText();
            }
            ctx.setYPos(ctx.getYPos() - labelH - WIDGET_LABEL_GAP);

            // ── 2. Draw widget background ─────────────────────────────────
            float widgetY = ctx.getYPos() - widgetH;

            if (fieldDef.getFieldType() != FormFieldType.CHECKBOX &&
                fieldDef.getFieldType() != FormFieldType.RADIO) {

                float bgX = marginLeft - BG_PADDING;
                float bgW = usableWidth + BG_PADDING * 2;
                ctx.getContentStream().setNonStrokingColor(bgColor);
                ctx.getContentStream().addRect(bgX, widgetY - BG_PADDING,
                                               bgW, widgetH + BG_PADDING * 2);
                ctx.getContentStream().fill();

                // Border line
                ctx.getContentStream().setStrokingColor(borderClr);
                ctx.getContentStream().setLineWidth(0.5f);
                ctx.getContentStream().addRect(bgX, widgetY - BG_PADDING,
                                               bgW, widgetH + BG_PADDING * 2);
                ctx.getContentStream().stroke();
            }

            // ── 3. Build widget rect and create AcroForm field ────────────
            PDPage page = ctx.getCurrentPage();

            PDRectangle widgetRect;

            if (fieldDef.getFieldType() == FormFieldType.RADIO) {
                // For radio: rect covers all options stacked vertically
                float totalRadioH = fieldDef.getOptions().size() *
                    (fieldDef.getToggleSize() + 4f);

                // Inline option labels — draw them first
                float optY = ctx.getYPos();
                for (String option : fieldDef.getOptions()) {
                    float labelX = marginLeft + fieldDef.getToggleSize() + 6f;
                    ctx.getContentStream().beginText();
                    ctx.getContentStream().setFont(bodyFont, fontSize - 1);
                    ctx.getContentStream().setNonStrokingColor(textColor);
                    ctx.getContentStream().newLineAtOffset(
                        labelX, optY - fieldDef.getToggleSize() * 0.7f);
                    ctx.getContentStream().showText(option);
                    ctx.getContentStream().endText();
                    optY -= (fieldDef.getToggleSize() + 4f);
                }

                widgetRect = new PDRectangle(
                    marginLeft,
                    ctx.getYPos() - totalRadioH,
                    fieldDef.getToggleSize(),
                    totalRadioH);

            } else if (fieldDef.getFieldType() == FormFieldType.CHECKBOX) {
                float size = fieldDef.getToggleSize();
                // Checkbox label — inline to the right
                if (label != null && !label.isBlank()) {
                    // Already drawn above; no repeat needed
                }
                widgetRect = new PDRectangle(
                    marginLeft,
                    ctx.getYPos() - size,
                    size, size);

            } else {
                widgetRect = new PDRectangle(
                    marginLeft, widgetY, usableWidth, widgetH);
            }

            // Close content stream, add field, reopen — PDFBox requires the
            // content stream to be closed before mutating page annotations
            ctx.getContentStream().saveGraphicsState();
            ctx.getContentStream().restoreGraphicsState();

            builder.addField(document, page, acroForm, fieldDef,
                             widgetRect, bodyFont, fontSize);

            // ── 4. Advance cursor ──────────────────────────────────────────
            float consumed = widgetH + fieldSpacing;
            ctx.setYPos(ctx.getYPos() - consumed);
        }

        logger.fine("FormRenderer: rendered " +
            formSection.getFieldDefs().size() + " field(s)");
    }
}
