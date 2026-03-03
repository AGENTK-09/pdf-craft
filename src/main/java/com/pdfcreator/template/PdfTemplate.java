package com.pdfcreator.template;

import java.util.Collections;
import java.util.List;

/**
 * Immutable data model representing a PDF template definition.
 *
 * Fields added vs previous version:
 *   footer (PageFooter) — optional configurable footer on every page
 */
public class PdfTemplate {

    private final String               id;
    private final String               description;
    private final String               configId;
    private final PageHeader           header;
    private final PageFooter           footer;
    private final List<TemplateSection> sections;

    private PdfTemplate(Builder b) {
        this.id          = b.id;
        this.description = b.description;
        this.configId    = b.configId;
        this.header      = b.header;
        this.footer      = b.footer;
        this.sections    = Collections.unmodifiableList(b.sections);
    }

    public String               getId()          { return id; }
    public String               getDescription() { return description; }
    public String               getConfigId()    { return configId; }
    public PageHeader           getHeader()      { return header; }
    public PageFooter           getFooter()      { return footer; }
    public List<TemplateSection> getSections()   { return sections; }
    public boolean              hasHeader()      { return header != null; }
    public boolean              hasFooter()      { return footer != null; }

    @Override
    public String toString() {
        return String.format("PdfTemplate[id=%s, configId=%s, sections=%d, header=%s, footer=%s]",
            id, configId, sections.size(),
            header != null ? "yes" : "none",
            footer != null ? "yes" : "none");
    }

    public static class Builder {
        private String               id;
        private String               description = "";
        private String               configId    = "default";
        private PageHeader           header      = null;
        private PageFooter           footer      = null;
        private List<TemplateSection> sections   = List.of();

        public Builder id(String v)                            { this.id = v; return this; }
        public Builder description(String v)                   { this.description = v; return this; }
        public Builder configId(String v)                      { this.configId = v; return this; }
        public Builder header(PageHeader v)                    { this.header = v; return this; }
        public Builder footer(PageFooter v)                    { this.footer = v; return this; }
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
