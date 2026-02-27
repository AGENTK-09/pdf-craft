package com.pdfcreator.template;

import java.util.Collections;
import java.util.List;

/**
 * Immutable data model representing a PDF template definition.
 *
 * New vs previous version:
 *   - header (PageHeader) : optional branding header drawn on every page
 *
 * Example JSON:
 * {
 *   "id": "branded-invoice",
 *   "description": "Invoice with company branding",
 *   "configId": "report",
 *   "header": {
 *     "logoPath":         "assets/logo.png",
 *     "logoAlign":        "right",
 *     "logoWidthPercent": 20,
 *     "bandColor":        "#003366",
 *     "bandHeight":       60
 *   },
 *   "sections": [ ... ]
 * }
 */
public class PdfTemplate {

    private final String               id;
    private final String               description;
    private final String               configId;
    private final PageHeader           header;    // null = no per-page header
    private final List<TemplateSection> sections;

    private PdfTemplate(Builder builder) {
        this.id          = builder.id;
        this.description = builder.description;
        this.configId    = builder.configId;
        this.header      = builder.header;
        this.sections    = Collections.unmodifiableList(builder.sections);
    }

    public String                getId()          { return id; }
    public String                getDescription() { return description; }
    public String                getConfigId()    { return configId; }
    public PageHeader            getHeader()      { return header; }   // may be null
    public List<TemplateSection>  getSections()   { return sections; }
    public boolean               hasHeader()      { return header != null; }

    @Override
    public String toString() {
        return String.format("PdfTemplate[id=%s, configId=%s, sections=%d, header=%s]",
            id, configId, sections.size(), header != null ? header : "none");
    }

    public static class Builder {
        private String               id;
        private String               description = "";
        private String               configId    = "default";
        private PageHeader           header      = null;
        private List<TemplateSection> sections   = List.of();

        public Builder id(String id)                           { this.id = id; return this; }
        public Builder description(String v)                   { this.description = v; return this; }
        public Builder configId(String v)                      { this.configId = v; return this; }
        public Builder header(PageHeader v)                    { this.header = v; return this; }
        public Builder sections(List<TemplateSection> sections){ this.sections = sections; return this; }

        public PdfTemplate build() {
            if (id == null || id.isBlank())
                throw new IllegalStateException("PdfTemplate must have an id");
            if (sections == null || sections.isEmpty())
                throw new IllegalStateException("PdfTemplate '" + id + "' must have at least one section");
            return new PdfTemplate(this);
        }
    }
}
