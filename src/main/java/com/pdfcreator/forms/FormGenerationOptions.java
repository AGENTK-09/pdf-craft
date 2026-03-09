package com.pdfcreator.forms;

import com.pdfcreator.template.DocumentMetadata;

import java.util.List;

/**
 * Options for generating a standalone PDF with an AcroForm via PdfFormGenerator.
 *
 * Unlike template-driven generation, PdfFormGenerator builds the entire PDF
 * from this options object — no JSON template file is required. This is
 * useful for generating forms programmatically or via the CLI --generate-form flag.
 *
 * ── EXAMPLE ──────────────────────────────────────────────────────────────
 *
 *   FormGenerationOptions opts = new FormGenerationOptions.Builder()
 *       .outputPath("output/application-form.pdf")
 *       .title("Loan Application")
 *       .configId("default")
 *       .addField(new FormFieldDef.Builder("fullName")
 *           .type(FormFieldType.TEXT)
 *           .label("Full Name")
 *           .required(true)
 *           .build())
 *       .addField(new FormFieldDef.Builder("loanType")
 *           .type(FormFieldType.COMBO)
 *           .label("Loan Type")
 *           .options(List.of("Personal", "Home", "Auto"))
 *           .build())
 *       .build();
 *
 *   String path = new PdfFormGenerator().generate(opts);
 */
public class FormGenerationOptions {

    private final String           outputPath;
    private final String           title;
    private final String           configId;
    private final String           configFile;
    private final DocumentMetadata metadata;
    private final List<FormFieldDef> fieldDefs;
    private final boolean          needAppearances;
    private final float            fieldSpacing;
    private final int              labelFontSize;
    private final boolean          pdfaMode;

    private FormGenerationOptions(Builder b) {
        this.outputPath      = b.outputPath;
        this.title           = b.title;
        this.configId        = b.configId;
        this.configFile      = b.configFile;
        this.metadata        = b.metadata;
        this.fieldDefs       = b.fieldDefs != null ? List.copyOf(b.fieldDefs) : List.of();
        this.needAppearances = b.needAppearances;
        this.fieldSpacing    = b.fieldSpacing;
        this.labelFontSize   = b.labelFontSize;
        this.pdfaMode        = b.pdfaMode;
    }

    public String             getOutputPath()      { return outputPath; }
    public String             getTitle()           { return title; }
    public String             getConfigId()        { return configId; }
    public String             getConfigFile()      { return configFile; }
    public DocumentMetadata   getMetadata()        { return metadata; }
    public List<FormFieldDef> getFieldDefs()       { return fieldDefs; }
    public boolean            isNeedAppearances()  { return needAppearances; }
    public float              getFieldSpacing()    { return fieldSpacing; }
    public int                getLabelFontSize()   { return labelFontSize; }
    public boolean            isPdfaMode()         { return pdfaMode; }

    public static class Builder {
        private String             outputPath      = "form-output.pdf";
        private String             title           = "Form";
        private String             configId        = "default";
        private String             configFile      = "configs/pdf-configs.json";
        private DocumentMetadata   metadata        = null;
        private List<FormFieldDef> fieldDefs       = null;
        private boolean            needAppearances = true;
        private float              fieldSpacing    = 8f;
        private int                labelFontSize   = 0;
        private boolean            pdfaMode        = false;

        public Builder outputPath(String v)           { this.outputPath = v; return this; }
        public Builder title(String v)                { this.title = v; return this; }
        public Builder configId(String v)             { this.configId = v; return this; }
        public Builder configFile(String v)           { this.configFile = v; return this; }
        public Builder metadata(DocumentMetadata v)   { this.metadata = v; return this; }
        public Builder fieldDefs(List<FormFieldDef> v){ this.fieldDefs = v; return this; }
        public Builder needAppearances(boolean v)     { this.needAppearances = v; return this; }
        public Builder fieldSpacing(float v)          { this.fieldSpacing = v; return this; }
        public Builder labelFontSize(int v)           { this.labelFontSize = v; return this; }
        public Builder pdfaMode(boolean v)            { this.pdfaMode = v; return this; }

        public FormGenerationOptions build() {
            if (outputPath == null || outputPath.isBlank())
                throw new IllegalStateException("outputPath is required");
            if (fieldDefs == null || fieldDefs.isEmpty())
                throw new IllegalStateException("At least one FormFieldDef is required");
            return new FormGenerationOptions(this);
        }
    }
}
