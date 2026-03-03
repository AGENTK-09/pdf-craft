package com.pdfcreator.renderer;

import com.pdfcreator.template.TemplateSection;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registry mapping TemplateSection.Type -> SectionRenderer.
 *
 * Adding a new section type:
 *   1. Add to TemplateSection.Type enum
 *   2. Implement SectionRenderer
 *   3. Register here
 *   Nothing else in the pipeline changes.
 *
 * ColumnsRenderer receives the registry itself so it can recursively
 * render sub-sections within each column.
 */
public class SectionRendererRegistry {

    private final Map<TemplateSection.Type, SectionRenderer> renderers =
        new EnumMap<>(TemplateSection.Type.class);

    public SectionRendererRegistry() {
        renderers.put(TemplateSection.Type.HEADING,    new SimpleRenderers.HeadingRenderer());
        renderers.put(TemplateSection.Type.SUBHEADING, new SimpleRenderers.SubheadingRenderer());
        renderers.put(TemplateSection.Type.BODY,       new SimpleRenderers.BodyRenderer());
        renderers.put(TemplateSection.Type.DIVIDER,    new SimpleRenderers.DividerRenderer());
        renderers.put(TemplateSection.Type.SPACER,     new SimpleRenderers.SpacerRenderer());
        renderers.put(TemplateSection.Type.IMAGE,      new SimpleRenderers.ImageRenderer());
        renderers.put(TemplateSection.Type.NOTICE,     new SimpleRenderers.NoticeRenderer());
        renderers.put(TemplateSection.Type.PAGEBREAK,  new SimpleRenderers.PageBreakRenderer());
        renderers.put(TemplateSection.Type.TABLE,      new TableRenderer());
        renderers.put(TemplateSection.Type.SUMMARY,    new SummaryRenderer());
        // ColumnsRenderer gets a reference to this registry for recursive dispatch
        renderers.put(TemplateSection.Type.COLUMNS,    new ColumnsRenderer(this));
    }

    public SectionRenderer get(TemplateSection.Type type) {
        SectionRenderer renderer = renderers.get(type);
        if (renderer == null)
            throw new IllegalStateException("No renderer registered for section type: " + type);
        return renderer;
    }

    public void register(TemplateSection.Type type, SectionRenderer renderer) {
        renderers.put(type, renderer);
    }
}
