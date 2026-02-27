package com.pdfcreator.template;

import com.pdfcreator.config.PdfConfig;
import com.pdfcreator.generator.PdfGenerator;
import com.pdfcreator.service.ConfigService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Orchestrates the full template-to-PDF pipeline.
 *
 * New vs previous version:
 *   - Resolves {{placeholders}} in PageHeader.logoPath before rendering
 *   - Passes the resolved PageHeader to PdfGenerator.generateFromSections()
 */
public class TemplateRenderer {

    private static final Logger logger = Logger.getLogger(TemplateRenderer.class.getName());

    private final TemplateService templateService;
    private final ConfigService   configService;
    private final DataLoader      dataLoader;
    private final PdfGenerator    generator;

    public TemplateRenderer(String templateFilePath, String configFilePath) {
        this.templateService = new TemplateService(templateFilePath);
        this.configService   = new ConfigService(configFilePath);
        this.dataLoader      = new DataLoader();
        this.generator       = new PdfGenerator();
    }

    public void render(String templateId, String dataFilePath, String outputPath) throws IOException {

        // 1. Load template
        PdfTemplate template = templateService.getTemplate(templateId);
        logger.info("Using template: " + template);

        // 2. Load data
        Map<String, String> data = dataLoader.load(dataFilePath);
        logger.info("Loaded " + data.size() + " data value(s).");

        PlaceholderResolver resolver = new PlaceholderResolver(data);

        // 3. Warn on missing placeholder keys
        List<String> missingKeys = resolver.findMissingKeys(template.getSections());
        if (!missingKeys.isEmpty()) {
            System.err.println("Warning: Unresolved placeholders (no value in data file): " + missingKeys);
        }

        // 4. Resolve section content placeholders
        List<TemplateSection> resolvedSections = resolver.resolveSections(template.getSections());

        // 5. Resolve logo path placeholder in header (if present)
        PageHeader header = resolveHeader(template.getHeader(), resolver);

        // 6. Load config
        PdfConfig config = configService.getConfig(template.getConfigId());
        logger.info("Using config: " + config);

        // 7. Generate
        System.out.println("Template      : " + template.getId() + " — " + template.getDescription());
        System.out.println("Config        : " + config.getId());
        System.out.println("Header        : " + (header != null ? header : "none"));
        System.out.println("Output file   : " + outputPath);
        System.out.println("Sections      : " + resolvedSections.size());

        generator.generateFromSections(config, resolvedSections, header, outputPath);
    }

    /**
     * Resolves any {{placeholder}} in the header's logoPath.
     * Returns null if no header is defined on the template.
     */
    private PageHeader resolveHeader(PageHeader header, PlaceholderResolver resolver) {
        if (header == null) return null;
        if (!header.hasLogo()) return header;

        String resolvedLogoPath = resolver.resolve(header.getLogoPath());
        if (resolvedLogoPath.equals(header.getLogoPath())) return header; // nothing changed

        // Rebuild with resolved path
        return new PageHeader.Builder()
            .logoPath(resolvedLogoPath)
            .logoAlign(header.getLogoAlign().name())
            .logoWidthPercent(header.getLogoWidthPercent())
            .bandColor(header.getBandColor())
            .bandHeight(header.getBandHeight())
            .build();
    }
}
