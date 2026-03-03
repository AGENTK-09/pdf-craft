package com.pdfcreator.template;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Single entry point for retrieving PdfTemplate definitions.
 *
 * Mirrors ConfigService exactly:
 *   1. On first access, loads all templates from the JSON file and warms the cache.
 *   2. Subsequent accesses are served from cache.
 *   3. Cache miss triggers a reload from the file before failing.
 */
public class TemplateService {

    private static final Logger logger = Logger.getLogger(TemplateService.class.getName());

    private final TemplateLoader loader;
    private final TemplateCache  cache;
    private boolean loaded = false;

    public TemplateService(String templateFilePath) {
        this.loader = new TemplateLoader(templateFilePath);
        this.cache  = new TemplateCache();
    }

    TemplateService(TemplateLoader loader, TemplateCache cache) {
        this.loader = loader;
        this.cache  = cache;
    }

    /**
     * Returns the PdfTemplate for the given id.
     * Throws IllegalArgumentException if not found after reload.
     */
    public PdfTemplate getTemplate(String id) throws IOException {
        ensureLoaded();

        Optional<PdfTemplate> cached = cache.get(id);
        if (cached.isPresent()) return cached.get();

        logger.info("Template '" + id + "' not in cache. Reloading...");
        reload();

        return cache.get(id).orElseThrow(() ->
            new IllegalArgumentException(
                "No template found with id: '" + id + "'. "                
            )
        );
    }

    public void reload() throws IOException {
        cache.populate(loader.loadAll());
        loaded = true;
    }

    public String availableIds() throws IOException {
        ensureLoaded();
        return cache.size() == 0 ? "(none)" : loader.loadAll().keySet().toString();
    }

    private void ensureLoaded() throws IOException {
        if (!loaded) reload();
    }
}
