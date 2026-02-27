package com.pdfcreator.template;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.*;

/**
 * Loads PdfTemplate definitions from a JSON file.
 *
 * New vs previous version:
 *   - Parses optional "header" object into a PageHeader
 *   - Parses optional "align" and "widthPercent" fields on image sections
 */
public class TemplateLoader {

    private static final Logger logger = Logger.getLogger(TemplateLoader.class.getName());

    private final String templateFilePath;

    public TemplateLoader(String templateFilePath) {
        this.templateFilePath = templateFilePath;
    }

    public Map<String, PdfTemplate> loadAll() throws IOException {
        Path path = Path.of(templateFilePath);
        if (!Files.exists(path))
            throw new FileNotFoundException("Template file not found: " + templateFilePath);

        String json = Files.readString(path);
        logger.info("Loading templates from: " + templateFilePath);

        Map<String, PdfTemplate> templates = new LinkedHashMap<>();
        for (String objJson : extractTopLevelObjects(json)) {
            try {
                PdfTemplate t = buildTemplate(objJson);
                templates.put(t.getId(), t);
                logger.fine("Loaded template: " + t);
            } catch (Exception e) {
                logger.warning("Skipping invalid template: " + e.getMessage());
            }
        }
        logger.info("Loaded " + templates.size() + " template(s).");
        return templates;
    }

    public Optional<PdfTemplate> loadById(String id) throws IOException {
        return Optional.ofNullable(loadAll().get(id));
    }

    // -----------------------------------------------------------------------
    // Parsing
    // -----------------------------------------------------------------------

    /** Extracts raw JSON strings for each object in the top-level "templates" array. */
    private List<String> extractTopLevelObjects(String json) {
        List<String> objects = new ArrayList<>();
        int arrayStart = json.indexOf('[');
        int arrayEnd   = json.lastIndexOf(']');
        if (arrayStart == -1 || arrayEnd == -1) return objects;
        String array = json.substring(arrayStart + 1, arrayEnd);

        int depth = 0, objStart = -1;
        for (int i = 0; i < array.length(); i++) {
            char c = array.charAt(i);
            if      (c == '{') { if (depth == 0) objStart = i; depth++; }
            else if (c == '}') { depth--; if (depth == 0 && objStart != -1) { objects.add(array.substring(objStart, i + 1)); objStart = -1; } }
        }
        return objects;
    }

    private PdfTemplate buildTemplate(String objJson) {
        Map<String, String> flat = parseScalarFields(objJson);

        String id          = require(flat, "id");
        String description = flat.getOrDefault("description", "");
        String configId    = flat.getOrDefault("configId", "default");

        // Parse optional header object
        PageHeader header = parseHeaderObject(extractNestedObject(objJson, "header"));

        // Parse sections array
        String sectionsRaw = extractArrayContent(objJson, "sections");
        List<TemplateSection> sections = sectionsRaw != null
            ? parseSections(sectionsRaw) : List.of();

        return new PdfTemplate.Builder()
            .id(id)
            .description(description)
            .configId(configId)
            .header(header)
            .sections(sections)
            .build();
    }

    /** Parses a "header": { ... } block into a PageHeader. Returns null if absent. */
    private PageHeader parseHeaderObject(String headerJson) {
        if (headerJson == null || headerJson.isBlank()) return null;
        Map<String, String> m = parseScalarFields(headerJson);
        if (m.isEmpty()) return null;

        return new PageHeader.Builder()
            .logoPath(m.get("logoPath"))
            .logoAlign(m.getOrDefault("logoAlign", "left"))
            .logoWidthPercent(intVal(m, "logoWidthPercent", 25))
            .bandColor(m.get("bandColor"))
            .bandHeight(floatVal(m, "bandHeight", 60f))
            .build();
    }

    /** Parses the sections array into TemplateSection objects. */
    private List<TemplateSection> parseSections(String sectionsJson) {
        List<TemplateSection> sections = new ArrayList<>();
        int depth = 0, objStart = -1;
        for (int i = 0; i < sectionsJson.length(); i++) {
            char c = sectionsJson.charAt(i);
            if      (c == '{') { if (depth == 0) objStart = i; depth++; }
            else if (c == '}') {
                depth--;
                if (depth == 0 && objStart != -1) {
                    Map<String, String> m = parseScalarFields(sectionsJson.substring(objStart + 1, i));
                    try {
                        TemplateSection.Type  type         = TemplateSection.Type.fromString(m.get("type"));
                        String                content      = m.get("content");
                        TemplateSection.Align align        = parseAlign(m.getOrDefault("align", "left"));
                        int                   widthPercent = intVal(m, "widthPercent", 100);
                        sections.add(new TemplateSection(type, content, align, widthPercent));
                    } catch (Exception e) {
                        logger.warning("Skipping invalid section: " + e.getMessage());
                    }
                    objStart = -1;
                }
            }
        }
        return sections;
    }

    // -----------------------------------------------------------------------
    // JSON extraction helpers
    // -----------------------------------------------------------------------

    /** Extracts the content of a named nested object: "key": { ... } -> "{ ... }" */
    private String extractNestedObject(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyPos = json.indexOf(marker);
        if (keyPos == -1) return null;
        int braceStart = json.indexOf('{', keyPos + marker.length());
        if (braceStart == -1) return null;
        int depth = 0, end = -1;
        for (int i = braceStart; i < json.length(); i++) {
            if      (json.charAt(i) == '{') depth++;
            else if (json.charAt(i) == '}') { depth--; if (depth == 0) { end = i; break; } }
        }
        return end != -1 ? json.substring(braceStart, end + 1) : null;
    }

    /** Extracts the content of a named array: "key": [ ... ] -> contents between brackets */
    private String extractArrayContent(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyPos = json.indexOf(marker);
        if (keyPos == -1) return null;
        int bracketStart = json.indexOf('[', keyPos + marker.length());
        if (bracketStart == -1) return null;
        int depth = 0, end = -1;
        for (int i = bracketStart; i < json.length(); i++) {
            if      (json.charAt(i) == '[') depth++;
            else if (json.charAt(i) == ']') { depth--; if (depth == 0) { end = i; break; } }
        }
        return end != -1 ? json.substring(bracketStart + 1, end) : null;
    }

    /** Parses all "key": "value" or "key": number scalar pairs from an object body. */
    private Map<String, String> parseScalarFields(String objContent) {
        Map<String, String> map = new LinkedHashMap<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([\\d.]+))").matcher(objContent);
        while (m.find()) {
            String key   = m.group(1).trim();
            String value = m.group(2) != null ? unescape(m.group(2)) : m.group(3);
            map.put(key, value);
        }
        return map;
    }

    private String unescape(String v) {
        return v.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private TemplateSection.Align parseAlign(String v) {
        if (v == null) return TemplateSection.Align.LEFT;
        return switch (v.trim().toUpperCase()) {
            case "CENTER" -> TemplateSection.Align.CENTER;
            case "RIGHT"  -> TemplateSection.Align.RIGHT;
            default       -> TemplateSection.Align.LEFT;
        };
    }

    private String require(Map<String, String> m, String key) {
        String v = m.get(key);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing required field: " + key);
        return v;
    }

    private int intVal(Map<String, String> m, String key, int def) {
        try { return Integer.parseInt(m.getOrDefault(key, String.valueOf(def)).trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private float floatVal(Map<String, String> m, String key, float def) {
        try { return Float.parseFloat(m.getOrDefault(key, String.valueOf(def)).trim()); }
        catch (NumberFormatException e) { return def; }
    }
}
