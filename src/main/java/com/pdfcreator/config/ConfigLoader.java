package com.pdfcreator.config;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Loads PDF configuration presets from a JSON file.
 *
 * Supported JSON fields per config entry:
 *   id, pageSize, titleFontSize, bodyFontSize,
 *   marginTop, marginBottom, marginLeft, marginRight,
 *   lineSpacing, fontFamily,
 *   fontColor, titleColor, backgroundColor   ← new
 *
 * Colors are expressed as hex strings: "#RRGGBB"
 * backgroundColor can be omitted to leave the page background as white (default PDF).
 */
public class ConfigLoader {

    private static final Logger logger = Logger.getLogger(ConfigLoader.class.getName());

    private final String configFilePath;

    public ConfigLoader(String configFilePath) {
        this.configFilePath = configFilePath;
    }

    public Map<String, PdfConfig> loadAll() throws IOException {
        Path path = Path.of(configFilePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Config file not found: " + configFilePath);
        }

        String json = Files.readString(path);
        logger.info("Loading configs from: " + configFilePath);

        Map<String, PdfConfig> configs = new LinkedHashMap<>();
        for (Map<String, String> entry : parseConfigEntries(json)) {
            try {
                PdfConfig config = buildFromEntry(entry);
                configs.put(config.getId(), config);
                logger.fine("Loaded config: " + config.getId());
            } catch (Exception e) {
                logger.warning("Skipping invalid config entry: " + e.getMessage());
            }
        }

        logger.info("Loaded " + configs.size() + " config(s).");
        return configs;
    }

    public Optional<PdfConfig> loadById(String id) throws IOException {
        return Optional.ofNullable(loadAll().get(id));
    }

    // -----------------------------------------------------------------------
    // Minimal JSON parser — handles flat array-of-objects structure.
    // Replace with Jackson/Gson if the schema grows.
    // -----------------------------------------------------------------------

    private List<Map<String, String>> parseConfigEntries(String json) {
        List<Map<String, String>> entries = new ArrayList<>();
        int arrayStart = json.indexOf('[');
        int arrayEnd   = json.lastIndexOf(']');
        if (arrayStart == -1 || arrayEnd == -1) return entries;

        String arrayContent = json.substring(arrayStart + 1, arrayEnd);
        int depth = 0, objStart = -1;

        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            if (c == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart != -1) {
                    entries.add(parseObject(arrayContent.substring(objStart + 1, i)));
                    objStart = -1;
                }
            }
        }
        return entries;
    }

    private Map<String, String> parseObject(String objContent) {
        Map<String, String> map = new LinkedHashMap<>();
        // Split on commas that are not inside quotes
        String[] tokens = objContent.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String token : tokens) {
            int colon = token.indexOf(':');
            if (colon == -1) continue;
            String key   = token.substring(0, colon).trim().replaceAll("\"", "");
            String value = token.substring(colon + 1).trim().replaceAll("\"", "");
            map.put(key, value);
        }
        return map;
    }

    private PdfConfig buildFromEntry(Map<String, String> e) {
        return new PdfConfig.Builder()
            .id(require(e, "id"))
            .pageSize(e.getOrDefault("pageSize", "A4"))
            .titleFontSize(intVal(e, "titleFontSize", 20))
            .bodyFontSize(intVal(e, "bodyFontSize", 12))
            .marginTop(floatVal(e, "marginTop", 50f))
            .marginBottom(floatVal(e, "marginBottom", 50f))
            .marginLeft(floatVal(e, "marginLeft", 50f))
            .marginRight(floatVal(e, "marginRight", 50f))
            .lineSpacing(floatVal(e, "lineSpacing", 1.4f))
            .fontFamily(e.getOrDefault("fontFamily", "HELVETICA"))
            .fontColor(e.getOrDefault("fontColor", "#000000"))
            .titleColor(e.getOrDefault("titleColor", "#000000"))
            .backgroundColor(e.getOrDefault("backgroundColor", null))
            .build();
    }

    private String require(Map<String, String> e, String key) {
        String val = e.get(key);
        if (val == null || val.isBlank())
            throw new IllegalArgumentException("Missing required field: " + key);
        return val;
    }

    private int intVal(Map<String, String> e, String key, int def) {
        try { return Integer.parseInt(e.getOrDefault(key, String.valueOf(def)).trim()); }
        catch (NumberFormatException ex) { return def; }
    }

    private float floatVal(Map<String, String> e, String key, float def) {
        try { return Float.parseFloat(e.getOrDefault(key, String.valueOf(def)).trim()); }
        catch (NumberFormatException ex) { return def; }
    }
}
