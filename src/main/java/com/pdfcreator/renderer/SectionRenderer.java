package com.pdfcreator.renderer;

import com.pdfcreator.config.PdfConfig;
import com.pdfcreator.datasource.DocumentData;
import com.pdfcreator.generator.PageContext;
import com.pdfcreator.pdfa.FontLoader;
import com.pdfcreator.template.TemplateSection;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;

/**
 * Renders a single TemplateSection onto a PageContext.
 *
 * Each section type has its own SectionRenderer implementation, registered
 * in SectionRendererRegistry.
 *
 * The FontLoader parameter provides either embedded TrueType fonts (PDF/A mode)
 * or Standard 14 fonts (standard mode). Renderers must use it for all text,
 * never construct PDType1Font directly.
 */
public interface SectionRenderer {

    /**
     * Renders the section onto the current page context.
     *
     * @param section    the section to render (content already placeholder-resolved)
     * @param ctx        the current page state (cursor, content stream)
     * @param config     the active style preset
     * @param data       the full document data (for table/list sections)
     * @param document   the PDDocument (needed for image embedding)
     * @param fontLoader font provider — use this for all fonts, never PDType1Font directly
     */
    void render(TemplateSection section,
                PageContext ctx,
                PdfConfig config,
                DocumentData data,
                PDDocument document,
                FontLoader fontLoader) throws IOException;
}
