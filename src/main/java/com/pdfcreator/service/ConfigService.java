package com.pdfcreator.service;

import com.pdfcreator.cache.ConfigCache;
import com.pdfcreator.config.ConfigLoader;
import com.pdfcreator.config.PdfConfig;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * ConfigService is the single entry point for retrieving PdfConfig presets.
 *
 * Workflow:
 *   1. On first access, loads all configs from the JSON file via ConfigLoader
 *      and warms the ConfigCache.
 *   2. On subsequent accesses, serves from the cache directly.
 *   3. If a cache miss occurs (e.g. entry evicted), reloads from file.
 *
 * This is the layer you'd extend when moving to a distributed cache:
 *   - Step 1: check local in-memory cache
 *   - Step 2 (future): check Redis/remote cache
 *   - Step 3 (future): fall back to DB or file, then re-populate caches
 */
public class ConfigService {

    private static final Logger logger = Logger.getLogger(ConfigService.class.getName());

    private final ConfigLoader loader;
    private final ConfigCache cache;
    private boolean loaded = false;

    public ConfigService(String configFilePath) {
        this.loader = new ConfigLoader(configFilePath);
        this.cache  = new ConfigCache();
    }

    // Package-private constructor for testing with injected dependencies
    ConfigService(ConfigLoader loader, ConfigCache cache) {
        this.loader = loader;
        this.cache  = cache;
    }

    /**
     * Retrieves a PdfConfig by id.
     *
     * Checks the cache first. On a miss, reloads from the config file.
     * Throws IllegalArgumentException if the id is not found after reload.
     */
    public PdfConfig getConfig(String id) throws IOException {
        ensureLoaded();

        Optional<PdfConfig> cached = cache.get(id);
        if (cached.isPresent()) {
            return cached.get();
        }

        // Cache miss — reload from source and try again
        logger.info("Config '" + id + "' not in cache. Reloading from file...");
        reload();

        return cache.get(id).orElseThrow(() ->
            new IllegalArgumentException(
                "No config found with id: '" + id + "'. "                
            )
        );
    }

    /**
     * Forces a full reload from the config file, clearing the cache first.
     */
    public void reload() throws IOException {
        logger.info("Reloading all configs from file...");
        cache.populate(loader.loadAll());
        loaded = true;
    }

    /**
     * Returns a human-readable list of available config ids.
     * Useful for error messages and the --help output.
     */
    public String listAvailableIds() throws IOException {
        ensureLoaded();
        return cache.size() == 0 ? "(none)" : loader.loadAll().keySet().toString();
    }

    private void ensureLoaded() throws IOException {
        if (!loaded) {
            reload();
        }
    }
}
