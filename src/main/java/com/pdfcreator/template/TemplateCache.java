package com.pdfcreator.template;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * In-memory cache for PdfTemplate definitions.
 *
 * Mirrors the design of ConfigCache exactly — same API, same eviction model,
 * same future extension path toward Redis or a DB-backed implementation.
 *
 * Future evolution:
 *   1. Extract a TemplateCache interface
 *   2. Keep InMemoryTemplateCache as one implementation
 *   3. Add RedisTemplateCache / DbTemplateCache as alternatives
 *   4. Inject via TemplateService constructor
 */
public class TemplateCache {

    private static final Logger logger = Logger.getLogger(TemplateCache.class.getName());

    private final ConcurrentHashMap<String, PdfTemplate> store = new ConcurrentHashMap<>();

    public void populate(Map<String, PdfTemplate> templates) {
        store.clear();
        store.putAll(templates);
        logger.info("Template cache populated with " + store.size() + " template(s): " + store.keySet());
    }

    public Optional<PdfTemplate> get(String id) {
        PdfTemplate t = store.get(id);
        if (t != null) logger.fine("Template cache HIT: " + id);
        else           logger.info("Template cache MISS: " + id);
        return Optional.ofNullable(t);
    }

    public void put(String id, PdfTemplate template) {
        store.put(id, template);
        logger.fine("Template cache PUT: " + id);
    }

    public void evict(String id)  { store.remove(id); }
    public void clear()           { store.clear(); }
    public boolean contains(String id) { return store.containsKey(id); }
    public int size()             { return store.size(); }
}
