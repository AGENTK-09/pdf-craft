package com.pdfcreator.forms;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.apache.pdfbox.pdmodel.interactive.form.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Stateless engine that creates PDFBox interactive form fields.
 *
 * ── RESPONSIBILITIES ─────────────────────────────────────────────────────
 *
 * 1. Ensure a PDAcroForm exists on the document catalog (created once,
 *    reused for all subsequent calls).
 * 2. Register fonts into the AcroForm's DefaultResources dictionary so
 *    the DA (Default Appearance) string can reference them by alias.
 * 3. Create the correct PDField subclass for each FormFieldType.
 * 4. Create a PDAnnotationWidget, set its rectangle, add it to the field
 *    and to the PDPage's annotation list.
 * 5. Apply default value, required/readOnly flags, tooltip.
 *
 * ── DEFAULT APPEARANCE STRING (DA) ───────────────────────────────────────
 *
 * The DA string controls how viewers render text inside a field:
 *
 *   "/<fontAlias> <fontSize> Tf 0 g"
 *
 * The critical requirement: <fontAlias> MUST be a name registered in
 * acroForm.getDefaultResources(). font.getName() returns the font's
 * internal PostScript name — NOT a resource alias — and using it directly
 * in the DA string will cause viewers to fail to resolve the font.
 *
 * Correct pattern (COSName.DA written directly — PDFBox 3.x API):
 *
 *   PDResources resources = acroForm.getDefaultResources();
 *   if (resources == null) resources = new PDResources();
 *   COSName alias = resources.add(font);          // returns the resource alias
 *   acroForm.setDefaultResources(resources);
 *   String da = "/" + alias.getName() + " 12 Tf 0 g";
 *   field.getCOSObject().setString(COSName.DA, da);
 *
 * For checkboxes and radio buttons, ZapfDingbats (/ZaDb) must be
 * pre-registered under the name "ZaDb" — this is the Adobe convention
 * and all viewers expect it at that exact name.
 *
 * ── setDefaultAppearance(String) REMOVED IN PDFBox 3.x ───────────────────
 *
 * In PDFBox 2.x, setDefaultAppearance(String) was defined on PDVariableText
 * and inherited by PDTextField, PDComboBox, PDListBox.
 * In PDFBox 3.x this method was removed from the public API entirely.
 * The replacement is a direct COS dictionary write:
 *   field.getCOSObject().setString(COSName.DA, daString)
 * which is what PDFBox's own internal code does when writing the /DA entry.
 *
 * ── FIELD NAMING ─────────────────────────────────────────────────────────
 *
 * AcroForm field names must be unique within the form. FormFieldDef.fieldName
 * is used directly. Callers are responsible for ensuring uniqueness across
 * all FormFieldDef instances passed to the renderer.
 *
 * ── RADIO BUTTON GROUPS ──────────────────────────────────────────────────
 *
 * For RADIO fields, one PDRadioButton field is created for the entire group
 * (shared fieldName). Each option in FormFieldDef.options gets its own
 * PDAnnotationWidget on the page. The export value for each button equals
 * its option string.
 */
public class AcroFormBuilder {

    private static final Logger logger = Logger.getLogger(AcroFormBuilder.class.getName());

    // Adobe-standard resource name for ZapfDingbats used in checkboxes/radio buttons.
    // ALL viewers expect this exact name in the AcroForm DefaultResources dictionary.
    private static final String ZADB_NAME = "ZaDb";

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns the PDAcroForm from the document catalog, creating it if absent.
     * Also ensures ZaDb (ZapfDingbats) is registered in DefaultResources so
     * checkbox and radio widget DA strings can reference it.
     * Sets NeedAppearances according to the FormSection option.
     */
    public PDAcroForm ensureAcroForm(PDDocument document, boolean needAppearances) {
        PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
        if (acroForm == null) {
            acroForm = new PDAcroForm(document);
            document.getDocumentCatalog().setAcroForm(acroForm);
            logger.fine("PDAcroForm created on document catalog");
        }
        acroForm.setNeedAppearances(needAppearances);

        // Ensure DefaultResources exists and ZaDb is registered under its
        // conventional name. Without this, checkbox/radio DA strings that
        // reference "/ZaDb" cannot be resolved by any viewer.
        ensureDefaultResources(acroForm);
        return acroForm;
    }

    /**
     * Creates a single form field and its widget annotation(s) on the given page.
     *
     * @param document    the PDDocument
     * @param page        the PDPage on which to place the widget
     * @param acroForm    the PDAcroForm (from ensureAcroForm)
     * @param fieldDef    the field specification
     * @param widgetRect  bounding box for the field widget (PDF page coordinates)
     * @param bodyFont    font to register and reference in the DA string
     * @param fontSize    font size for the DA string
     */
    public void addField(PDDocument document,
                         PDPage page,
                         PDAcroForm acroForm,
                         FormFieldDef fieldDef,
                         PDRectangle widgetRect,
                         PDFont bodyFont,
                         int fontSize) throws IOException {

        // Register bodyFont into AcroForm DefaultResources and get the alias.
        // This MUST happen before building the DA string — font.getName() is
        // the PostScript name, not the resource alias.
        String fontAlias = registerFont(acroForm, bodyFont);
        String da        = "/" + fontAlias + " " + fontSize + " Tf 0 g";

        switch (fieldDef.getFieldType()) {
            case TEXT      -> addTextField(page, acroForm, fieldDef,
                                           widgetRect, da, false);
            case MULTILINE -> addTextField(page, acroForm, fieldDef,
                                           widgetRect, da, true);
            case CHECKBOX  -> addCheckBox(page, acroForm, fieldDef, widgetRect);
            case RADIO     -> addRadioGroup(page, acroForm, fieldDef,
                                            widgetRect, fieldDef.getToggleSize());
            case COMBO     -> addComboBox(page, acroForm, fieldDef, widgetRect, da);
            case LISTBOX   -> addListBox(page, acroForm, fieldDef, widgetRect, da);
        }
    }

    // -----------------------------------------------------------------------
    // TextField (TEXT and MULTILINE)
    // -----------------------------------------------------------------------

    private void addTextField(PDPage page,
                               PDAcroForm acroForm,
                               FormFieldDef fieldDef,
                               PDRectangle rect,
                               String da,
                               boolean multiline) throws IOException {

        PDTextField field = new PDTextField(acroForm);
        field.setPartialName(fieldDef.getFieldName());
        // PDFBox 3.x removed setDefaultAppearance(String) from PDVariableText.
        // The DA entry must be written directly to the field's COS dictionary.
        field.getCOSObject().setString(COSName.DA, da);
        field.setMultiline(multiline);
        applyCommonFlags(field, fieldDef);

        PDAnnotationWidget widget = createWidget(rect, fieldDef);
        field.getWidgets().add(widget);
        widget.setPage(page);

        if (fieldDef.getDefaultValue() != null) {
            field.setValue(fieldDef.getDefaultValue());
        }

        acroForm.getFields().add(field);
        addWidgetToPage(page, widget);

        logger.fine("Added " + (multiline ? "MULTILINE" : "TEXT") +
            " field: " + fieldDef.getFieldName());
    }

    // -----------------------------------------------------------------------
    // CheckBox
    // -----------------------------------------------------------------------

    private void addCheckBox(PDPage page,
                              PDAcroForm acroForm,
                              FormFieldDef fieldDef,
                              PDRectangle rect) throws IOException {

        PDCheckBox field = new PDCheckBox(acroForm);
        field.setPartialName(fieldDef.getFieldName());
        // ZaDb is already registered in DefaultResources by ensureAcroForm().
        // Font size 0 means "auto-size to fit the widget" — correct for checkboxes.
        field.getCOSObject().setString(COSName.DA, "/" + ZADB_NAME + " 0 Tf 0 g");
        applyCommonFlags(field, fieldDef);

        PDAnnotationWidget widget = createWidget(rect, fieldDef);
        field.getWidgets().add(widget);
        widget.setPage(page);

        String dv = fieldDef.getDefaultValue();
        if ("Yes".equalsIgnoreCase(dv) || "true".equalsIgnoreCase(dv) || "on".equalsIgnoreCase(dv)) {
            field.check();
        } else {
            field.unCheck();
        }

        acroForm.getFields().add(field);
        addWidgetToPage(page, widget);

        logger.fine("Added CHECKBOX field: " + fieldDef.getFieldName());
    }

    // -----------------------------------------------------------------------
    // RadioButton group
    // -----------------------------------------------------------------------

    private void addRadioGroup(PDPage page,
                                PDAcroForm acroForm,
                                FormFieldDef fieldDef,
                                PDRectangle firstRect,
                                float buttonSize) throws IOException {

        PDRadioButton field = new PDRadioButton(acroForm);
        field.setPartialName(fieldDef.getFieldName());
        // ZaDb is already registered in DefaultResources by ensureAcroForm().
        field.getCOSObject().setString(COSName.DA, "/" + ZADB_NAME + " 0 Tf 0 g");
        field.setRadiosInUnison(false);
        applyCommonFlags(field, fieldDef);

        List<PDAnnotationWidget> widgets = new ArrayList<>();
        float y = firstRect.getLowerLeftY();

        for (String option : fieldDef.getOptions()) {
            PDRectangle buttonRect = new PDRectangle(
                firstRect.getLowerLeftX(), y, buttonSize, buttonSize);

            PDAnnotationWidget widget = new PDAnnotationWidget();
            widget.setPage(page);
            widget.setRectangle(buttonRect);
            applyBorderStyle(widget);
            widget.setContents(option);

            widgets.add(widget);
            addWidgetToPage(page, widget);
            y -= (buttonSize + 4f);
        }

        field.setWidgets(widgets);
        field.setExportValues(fieldDef.getOptions());

        if (fieldDef.getDefaultValue() != null) {
            field.setValue(fieldDef.getDefaultValue());
        }

        acroForm.getFields().add(field);

        logger.fine("Added RADIO group: " + fieldDef.getFieldName() +
            " (" + fieldDef.getOptions().size() + " options)");
    }

    // -----------------------------------------------------------------------
    // ComboBox
    // -----------------------------------------------------------------------

    private void addComboBox(PDPage page,
                              PDAcroForm acroForm,
                              FormFieldDef fieldDef,
                              PDRectangle rect,
                              String da) throws IOException {

        PDComboBox field = new PDComboBox(acroForm);
        field.setPartialName(fieldDef.getFieldName());
        field.getCOSObject().setString(COSName.DA, da);
        field.setOptions(fieldDef.getOptions());
        applyCommonFlags(field, fieldDef);

        PDAnnotationWidget widget = createWidget(rect, fieldDef);
        field.getWidgets().add(widget);
        widget.setPage(page);

        if (fieldDef.getDefaultValue() != null) {
            field.setValue(fieldDef.getDefaultValue());
        }

        acroForm.getFields().add(field);
        addWidgetToPage(page, widget);

        logger.fine("Added COMBO field: " + fieldDef.getFieldName() +
            " (" + fieldDef.getOptions().size() + " options)");
    }

    // -----------------------------------------------------------------------
    // ListBox
    // -----------------------------------------------------------------------

    private void addListBox(PDPage page,
                             PDAcroForm acroForm,
                             FormFieldDef fieldDef,
                             PDRectangle rect,
                             String da) throws IOException {

        PDListBox field = new PDListBox(acroForm);
        field.setPartialName(fieldDef.getFieldName());
        field.getCOSObject().setString(COSName.DA, da);
        field.setOptions(fieldDef.getOptions());
        applyCommonFlags(field, fieldDef);

        PDAnnotationWidget widget = createWidget(rect, fieldDef);
        field.getWidgets().add(widget);
        widget.setPage(page);

        if (fieldDef.getDefaultValue() != null) {
            field.setValue(fieldDef.getDefaultValue());
        }

        acroForm.getFields().add(field);
        addWidgetToPage(page, widget);

        logger.fine("Added LISTBOX field: " + fieldDef.getFieldName() +
            " (" + fieldDef.getOptions().size() + " options)");
    }

    // -----------------------------------------------------------------------
    // Font registration
    // -----------------------------------------------------------------------

    /**
     * Registers a font in the AcroForm's DefaultResources dictionary and
     * returns the resource alias name (e.g. "F1", "F2") that must be used
     * in DA strings.
     *
     * PDResources.add(font) returns a COSName — that COSName.getName() is
     * the alias the viewer will look up when rendering field text.
     *
     * If the same font object is registered twice, PDResources returns the
     * same alias, so this is idempotent for repeated calls with the same font.
     */
    private String registerFont(PDAcroForm acroForm, PDFont font) throws IOException {
        PDResources resources = acroForm.getDefaultResources();
        if (resources == null) {
            resources = new PDResources();
            acroForm.setDefaultResources(resources);
        }
        COSName alias = resources.add(font);
        acroForm.setDefaultResources(resources);   // ensure the reference is persisted
        return alias.getName();
    }

    /**
     * Ensures the AcroForm DefaultResources dictionary exists and contains
     * ZapfDingbats registered under the conventional name "ZaDb".
     *
     * Adobe Acrobat and all major PDF viewers expect ZaDb at this exact name.
     * Without it, checkbox and radio button widget appearances (which use
     * ZapfDingbats check-mark glyphs) cannot be rendered.
     */
    private void ensureDefaultResources(PDAcroForm acroForm) {
        PDResources resources = acroForm.getDefaultResources();
        if (resources == null) {
            resources = new PDResources();
            acroForm.setDefaultResources(resources);
        }
        // Register ZaDb only if not already present — avoid overwriting
        // an existing registration in forms that already have it.
        COSName zaDbKey = COSName.getPDFName(ZADB_NAME);
        if (!resources.getCOSObject().containsKey(zaDbKey)) {
            resources.put(zaDbKey,
                new PDType1Font(Standard14Fonts.FontName.ZAPF_DINGBATS));
            logger.fine("Registered ZaDb (ZapfDingbats) in AcroForm DefaultResources");
        }
    }

    // -----------------------------------------------------------------------
    // Widget helpers
    // -----------------------------------------------------------------------

    private PDAnnotationWidget createWidget(PDRectangle rect, FormFieldDef fieldDef) {
        PDAnnotationWidget widget = new PDAnnotationWidget();
        widget.setRectangle(rect);
        applyBorderStyle(widget);

        String tooltip = fieldDef.getTooltip() != null && !fieldDef.getTooltip().isBlank()
            ? fieldDef.getTooltip()
            : (fieldDef.getLabel() != null && !fieldDef.getLabel().isBlank()
               ? fieldDef.getLabel() : null);
        if (tooltip != null) widget.setContents(tooltip);

        return widget;
    }

    private void applyBorderStyle(PDAnnotationWidget widget) {
        PDBorderStyleDictionary border = new PDBorderStyleDictionary();
        border.setStyle(PDBorderStyleDictionary.STYLE_SOLID);
        border.setWidth(0.75f);
        widget.setBorderStyle(border);
    }

    private void applyCommonFlags(PDField field, FormFieldDef fieldDef) {
        field.setRequired(fieldDef.isRequired());
        field.setReadOnly(fieldDef.isReadOnly());
    }

    @SuppressWarnings("unchecked")
    private void addWidgetToPage(PDPage page, PDAnnotationWidget widget) throws IOException {
        List<org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation> annotations =
            page.getAnnotations();
        annotations.add(widget);
        page.setAnnotations(annotations);
    }
}
