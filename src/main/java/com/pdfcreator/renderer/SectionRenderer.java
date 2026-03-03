package com.pdfcreator.renderer;

import com.pdfcreator.config.PdfConfig;
import com.pdfcreator.datasource.DocumentData;
import com.pdfcreator.generator.PageContext;
import com.pdfcreator.template.TemplateSection;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;

/**
 * Renders a single TemplateSection onto a PageContext.
 *
 * Each section type has its own SectionRenderer implementation, registered
 * in SectionRendererRegistry. New section types are added by implementing
 * this interface and registering it — nothing else in the pipeline changes.
 *
 * The DocumentData is passed for renderers that need list data (e.g. TableRenderer).
 * Simple text renderers typically only use section.getContent().
 */
public interface SectionRenderer {

    /**
     * Renders the section onto the current page context.
     *
     * @param section  the section to render (content already placeholder-resolved)
     * @param ctx      the current page state (cursor, content stream)
     * @param config   the active style preset
     * @param data     the full document data (for table/list sections)
     * @param document the PDDocument (needed for image embedding)
     */
    void render(TemplateSection section,
                PageContext ctx,
                PdfConfig config,
                DocumentData data,
                PDDocument document) throws IOException;
}
