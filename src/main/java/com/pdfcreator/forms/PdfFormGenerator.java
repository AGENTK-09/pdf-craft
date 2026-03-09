package com.pdfcreator.forms;

import com.pdfcreator.config.PdfConfig;
import com.pdfcreator.datasource.DocumentData;
import com.pdfcreator.generator.PageContext;
import com.pdfcreator.pdfa.FontLoader;
import com.pdfcreator.pdfa.PdfACompliance;
import com.pdfcreator.service.ConfigService;
import com.pdfcreator.template.DocumentMetadata;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Logger;

/**
 * Generates a standalone PDF document containing an AcroForm.
 *
 * Does not require a JSON template file. The entire document is described
 * by a {@link FormGenerationOptions} object, which specifies the title,
 * field definitions, config preset, and output path.
 *
 * ── DOCUMENT STRUCTURE ───────────────────────────────────────────────────
 *
 * The generated PDF contains:
 *   1. A heading section with the form title.
 *   2. A thin divider rule below the title.
 *   3. One field per FormFieldDef, laid out top-to-bottom with labels.
 *   4. A "Required fields are marked *" footnote at the bottom if any
 *      field has required=true.
 *
 * ── PDF/A INCOMPATIBILITY ─────────────────────────────────────────────────
 *
 * PDF/A-1b (ISO 19005-1 §6.1.3) forbids interactive features. Forms are
 * interactive by definition and cannot be PDF/A-1b compliant. If pdfaMode
 * is true in FormGenerationOptions, the generator logs a warning and
 * proceeds without PDF/A markers (the flag is ignored).
 *
 * ── USAGE ────────────────────────────────────────────────────────────────
 *
 *   PdfFormGenerator generator = new PdfFormGenerator("configs/pdf-configs.json");
 *   String outputPath = generator.generate(opts);
 *
 * The return value is the resolved absolute output path.
 */
public class PdfFormGenerator {

    private static final Logger logger = Logger.getLogger(PdfFormGenerator.class.getName());

    // Title heading style constants
    private static final float TITLE_FONT_SIZE  = 16f;
    private static final float DIVIDER_HEIGHT   = 1f;
    private static final float DIVIDER_GAP      = 8f;
    private static final float FOOTNOTE_SIZE    = 8f;

    private final ConfigService configService;

    public PdfFormGenerator(String configFilePath) {
        this.configService = new ConfigService(configFilePath);
    }

    // -----------------------------------------------------------------------
    // Main generation entry point
    // -----------------------------------------------------------------------

    /**
     * Generates a PDF with an AcroForm defined by the given options.
     *
     * @param opts describes the form fields, title, config preset, and output path
     * @return the output path the PDF was written to
     * @throws IOException if config loading or PDF writing fails
     */
    public String generate(FormGenerationOptions opts) throws IOException {

        if (opts.isPdfaMode()) {
            logger.warning("PDF/A-1b is incompatible with interactive AcroForms " +
                "(ISO 19005-1 §6.1.3). pdfaMode flag will be ignored.");
        }

        PdfConfig config = configService.getConfig(opts.getConfigId());

        try (PDDocument document = new PDDocument()) {

            // Apply document information dictionary
            applyDocumentInfo(document, opts, config);

            FontLoader fontLoader = new FontLoader(document, false); // standard mode
            PDFont     bodyFont   = fontLoader.regularFor(config.getFontFamily());
            PDFont     boldFont   = fontLoader.boldFor(config.getFontFamily());
            int        bodySize   = opts.getLabelFontSize() > 0
                                    ? opts.getLabelFontSize()
                                    : config.getBodyFontSize();
            float      lineH      = bodySize * config.getLineSpacing();

            PDRectangle pageSize = PDRectangle.A4;
            PageContext ctx      = new PageContext(document, config, pageSize);
            ctx.open();

            // ── 1. Title heading ──────────────────────────────────────────
            renderTitle(ctx, config, boldFont, opts.getTitle());

            // ── 2. Divider ────────────────────────────────────────────────
            renderDivider(ctx, config);

            // ── 3. AcroForm fields ────────────────────────────────────────
            AcroFormBuilder acroBuilder = new AcroFormBuilder();
            PDAcroForm acroForm = acroBuilder.ensureAcroForm(document, opts.isNeedAppearances());

            boolean hasRequiredField = false;
            for (FormFieldDef fieldDef : opts.getFieldDefs()) {
                if (fieldDef.isRequired()) hasRequiredField = true;
                renderField(ctx, config, document, acroForm, acroBuilder,
                            fieldDef, bodyFont, bodySize, lineH, opts.getFieldSpacing());
            }

            // ── 4. Required-field footnote ────────────────────────────────
            if (hasRequiredField) {
                renderRequiredFootnote(ctx, config, bodyFont);
            }

            ctx.close();

            // ── 5. Save ───────────────────────────────────────────────────
            // Forms are never saved with CompressParameters.NO_COMPRESSION
            // because PDF/A mode is disabled for forms. Default compression
            // is fine for interactive documents.
            document.save(opts.getOutputPath());
            logger.info("Form PDF saved: " + opts.getOutputPath() +
                " (" + opts.getFieldDefs().size() + " field(s))");

            System.out.println("Form generated → " + opts.getOutputPath());
            System.out.println("Fields: " + opts.getFieldDefs().size());

            return opts.getOutputPath();
        }
    }

    // -----------------------------------------------------------------------
    // Document information
    // -----------------------------------------------------------------------

    private void applyDocumentInfo(PDDocument document,
                                   FormGenerationOptions opts,
                                   PdfConfig config) {
        PDDocumentInformation info = document.getDocumentInformation();
        info.setTitle(opts.getTitle());
        info.setProducer("PdfCreator / Apache PDFBox 3");
        info.setCreator("PdfCreator");

        DocumentMetadata meta = opts.getMetadata();
        if (meta != null) {
            if (meta.getAuthor()   != null) info.setAuthor(meta.getAuthor());
            if (meta.getSubject()  != null) info.setSubject(meta.getSubject());
            if (meta.getKeywords() != null) info.setKeywords(meta.getKeywords());
        }

        Calendar now = Calendar.getInstance();
        info.setCreationDate(now);
        info.setModificationDate(now);
    }

    // -----------------------------------------------------------------------
    // Title rendering
    // -----------------------------------------------------------------------

    private void renderTitle(PageContext ctx,
                              PdfConfig config,
                              PDFont boldFont,
                              String title) throws IOException {

        float fontSize = TITLE_FONT_SIZE;
        java.awt.Color color = com.pdfcreator.generator.ColorUtil.fromHex(
            config.getTitleColor(), java.awt.Color.BLACK);

        ctx.getContentStream().beginText();
        ctx.getContentStream().setFont(boldFont, (int) fontSize);
        ctx.getContentStream().setNonStrokingColor(color);
        ctx.getContentStream().newLineAtOffset(config.getMarginLeft(), ctx.getYPos() - fontSize);
        ctx.getContentStream().showText(title);
        ctx.getContentStream().endText();

        ctx.setYPos(ctx.getYPos() - fontSize - 6f);
    }

    // -----------------------------------------------------------------------
    // Divider
    // -----------------------------------------------------------------------

    private void renderDivider(PageContext ctx, PdfConfig config) throws IOException {
        float x1 = config.getMarginLeft();
        float x2 = ctx.getPageWidth() - config.getMarginRight();
        float y  = ctx.getYPos() - DIVIDER_GAP;

        java.awt.Color color = com.pdfcreator.generator.ColorUtil.fromHex(
            config.getTitleColor(), java.awt.Color.GRAY);

        ctx.getContentStream().setStrokingColor(color);
        ctx.getContentStream().setLineWidth(DIVIDER_HEIGHT);
        ctx.getContentStream().moveTo(x1, y);
        ctx.getContentStream().lineTo(x2, y);
        ctx.getContentStream().stroke();

        ctx.setYPos(y - DIVIDER_GAP);
    }

    // -----------------------------------------------------------------------
    // Field rendering
    // -----------------------------------------------------------------------

    private void renderField(PageContext ctx,
                              PdfConfig config,
                              PDDocument document,
                              PDAcroForm acroForm,
                              AcroFormBuilder acroBuilder,
                              FormFieldDef fieldDef,
                              PDFont bodyFont,
                              int fontSize,
                              float lineH,
                              float fieldSpacing) throws IOException {

        float widgetH     = fieldDef.effectiveHeight(lineH);
        float labelH      = lineH;
        float totalNeeded = labelH + 2f + widgetH + fieldSpacing;

        if (ctx.wouldOverflow(totalNeeded)) {
            ctx.advanceY(ctx.getYPos());
        }

        java.awt.Color textColor = com.pdfcreator.generator.ColorUtil.fromHex(
            config.getFontColor(), java.awt.Color.BLACK);

        // Label
        String label = fieldDef.getLabel();
        if (label != null && !label.isBlank()) {
            ctx.getContentStream().beginText();
            ctx.getContentStream().setFont(bodyFont, fontSize);
            ctx.getContentStream().setNonStrokingColor(textColor);
            ctx.getContentStream().newLineAtOffset(
                config.getMarginLeft(), ctx.getYPos() - fontSize);
            ctx.getContentStream().showText(label +
                (fieldDef.isRequired() ? " *" : ""));
            ctx.getContentStream().endText();
        }
        ctx.setYPos(ctx.getYPos() - labelH - 2f);

        // Widget background for non-toggle fields
        float marginLeft  = config.getMarginLeft();
        float usableWidth = ctx.getUsableWidth();
        float widgetY     = ctx.getYPos() - widgetH;

        if (fieldDef.getFieldType() != FormFieldType.CHECKBOX &&
            fieldDef.getFieldType() != FormFieldType.RADIO) {

            ctx.getContentStream().setNonStrokingColor(new java.awt.Color(0.94f, 0.94f, 0.94f));
            ctx.getContentStream().addRect(marginLeft - 2f, widgetY - 2f, usableWidth + 4f, widgetH + 4f);
            ctx.getContentStream().fill();
            ctx.getContentStream().setStrokingColor(
                com.pdfcreator.generator.ColorUtil.fromHex(config.getTitleColor(), java.awt.Color.GRAY));
            ctx.getContentStream().setLineWidth(0.5f);
            ctx.getContentStream().addRect(marginLeft - 2f, widgetY - 2f, usableWidth + 4f, widgetH + 4f);
            ctx.getContentStream().stroke();
        }

        // Radio option labels (inline)
        if (fieldDef.getFieldType() == FormFieldType.RADIO) {
            float optY = ctx.getYPos();
            for (String option : fieldDef.getOptions()) {
                float labelX = marginLeft + fieldDef.getToggleSize() + 6f;
                ctx.getContentStream().beginText();
                ctx.getContentStream().setFont(bodyFont, fontSize - 1);
                ctx.getContentStream().setNonStrokingColor(textColor);
                ctx.getContentStream().newLineAtOffset(labelX, optY - fieldDef.getToggleSize() * 0.75f);
                ctx.getContentStream().showText(option);
                ctx.getContentStream().endText();
                optY -= (fieldDef.getToggleSize() + 4f);
            }
        }

        // Build widget rect
        PDRectangle widgetRect;
        if (fieldDef.getFieldType() == FormFieldType.RADIO) {
            float totalH = fieldDef.getOptions().size() * (fieldDef.getToggleSize() + 4f);
            widgetRect = new PDRectangle(marginLeft, ctx.getYPos() - totalH,
                fieldDef.getToggleSize(), totalH);
        } else if (fieldDef.getFieldType() == FormFieldType.CHECKBOX) {
            float size = fieldDef.getToggleSize();
            widgetRect = new PDRectangle(marginLeft, ctx.getYPos() - size, size, size);
        } else {
            widgetRect = new PDRectangle(marginLeft, widgetY, usableWidth, widgetH);
        }

        acroBuilder.addField(document, ctx.getCurrentPage(), acroForm,
                             fieldDef, widgetRect, bodyFont, fontSize);

        ctx.setYPos(ctx.getYPos() - widgetH - fieldSpacing);
    }

    // -----------------------------------------------------------------------
    // Required footnote
    // -----------------------------------------------------------------------

    private void renderRequiredFootnote(PageContext ctx,
                                         PdfConfig config,
                                         PDFont font) throws IOException {
        float y = config.getMarginBottom() + FOOTNOTE_SIZE + 4f;
        ctx.getContentStream().beginText();
        ctx.getContentStream().setFont(font, (int) FOOTNOTE_SIZE);
        ctx.getContentStream().setNonStrokingColor(java.awt.Color.GRAY);
        ctx.getContentStream().newLineAtOffset(config.getMarginLeft(), y);
        ctx.getContentStream().showText("* Required field");
        ctx.getContentStream().endText();
    }
}
