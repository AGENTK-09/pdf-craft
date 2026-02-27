package com.pdfcreator.cache;

import com.pdfcreator.config.PdfConfig;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * In-memory cache for PdfConfig presets.
 *
 * Design intent:
 *   This class is the single point of config retrieval for the rest of the app.
 *   Today it's backed by a ConcurrentHashMap. In the future, replace or extend
 *   this with a Redis, Memcached, or database-backed implementation by swapping
 *   the backing store — the interface exposed to callers stays the same.
 *
 * Future evolution path:
 *   1. Extract a ConfigCache interface
 *   2. Keep InMemoryConfigCache as one implementation
 *   3. Add RedisConfigCache / DbConfigCache as other implementations
 *   4. Inject via constructor (or a DI framework like Spring)
 */
public class ConfigCache {

    private static final Logger logger = Logger.getLogger(ConfigCache.class.getName());

    // The backing store — swap this for a Redis/DB client in future
    private final ConcurrentHashMap<String, PdfConfig> store = new ConcurrentHashMap<>();

    /**
     * Populates the cache from a pre-loaded map (typically from ConfigLoader).
     */
    public void populate(Map<String, PdfConfig> configs) {
        store.clear();
        store.putAll(configs);
        logger.info("Cache populated with " + store.size() + " config(s): " + store.keySet());
    }

    /**
     * Retrieves a config by id.
     * Returns empty Optional on cache miss.
     */
    public Optional<PdfConfig> get(String id) {
        PdfConfig config = store.get(id);
        if (config != null) {
            logger.fine("Cache HIT for config id: " + id);
        } else {
            logger.info("Cache MISS for config id: " + id);
        }
        return Optional.ofNullable(config);
    }

    /**
     * Adds or updates a single entry. Useful for dynamic config registration.
     */
    public void put(String id, PdfConfig config) {
        store.put(id, config);
        logger.fine("Cache PUT for config id: " + id);
    }

    /**
     * Removes an entry from cache (e.g. to force reload on next access).
     */
    public void evict(String id) {
        store.remove(id);
        logger.fine("Cache EVICT for config id: " + id);
    }

    /**
     * Clears all entries.
     */
    public void clear() {
        store.clear();
        logger.info("Cache cleared.");
    }

    /**
     * Returns true if the given id is currently cached.
     */
    public boolean contains(String id) {
        return store.containsKey(id);
    }

    /**
     * Returns the number of cached configs.
     */
    public int size() {
        return store.size();
    }
}
