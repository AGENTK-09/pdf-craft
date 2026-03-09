package com.pdfcreator.template;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.*;

/**
 * Loads PdfTemplate definitions from a JSON file.
 *
 * Added vs previous version:
 *   - Parses optional "footer" object into a PageFooter
 *   - Parses COLUMNS sections (nested left/right section arrays)
 */
public class TemplateLoader {

    private static final Logger logger = Logger.getLogger(TemplateLoader.class.getName());
    private final String templateFilePath;

    public TemplateLoader(String templateFilePath) { this.templateFilePath = templateFilePath; }

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
                logger.fine("Loaded: " + t);
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
    // Template building
    // -----------------------------------------------------------------------

    private PdfTemplate buildTemplate(String objJson) {
        Map<String, String> flat = parseScalarFields(objJson);
        String id          = require(flat, "id");
        String description = flat.getOrDefault("description", "");
        String configId    = flat.getOrDefault("configId", "default");

        PageHeader       header   = parseHeaderObject(extractNestedObject(objJson, "header"));
        PageFooter       footer   = parseFooterObject(extractNestedObject(objJson, "footer"));
        DocumentMetadata metadata = parseMetadataObject(extractNestedObject(objJson, "metadata"));

        String sectionsRaw = extractArrayContent(objJson, "sections");
        List<TemplateSection> sections = sectionsRaw != null
            ? parseSections(sectionsRaw) : List.of();

        return new PdfTemplate.Builder()
            .id(id).description(description).configId(configId)
            .header(header).footer(footer).metadata(metadata).sections(sections)
            .build();
    }

    private DocumentMetadata parseMetadataObject(String json) {
        if (json == null || json.isBlank()) return null;
        Map<String, String> m = parseScalarFields(json);
        if (m.isEmpty()) return null;
        DocumentMetadata.Builder b = new DocumentMetadata.Builder();
        if (m.containsKey("title"))    b.title(m.get("title"));
        if (m.containsKey("author"))   b.author(m.get("author"));
        if (m.containsKey("subject"))  b.subject(m.get("subject"));
        if (m.containsKey("keywords")) b.keywords(m.get("keywords"));
        if (m.containsKey("creator"))  b.creator(m.get("creator"));
        if (m.containsKey("producer")) b.producer(m.get("producer"));
        return b.build();
    }

    private PageHeader parseHeaderObject(String json) {
        if (json == null || json.isBlank()) return null;
        Map<String, String> m = parseScalarFields(json);
        if (m.isEmpty()) return null;
        return new PageHeader.Builder()
            .logoPath(m.get("logoPath"))
            .logoAlign(m.getOrDefault("logoAlign", "left"))
            .logoWidthPercent(intVal(m, "logoWidthPercent", 25))
            .bandColor(m.get("bandColor"))
            .bandHeight(floatVal(m, "bandHeight", 60f))
            .build();
    }

    private PageFooter parseFooterObject(String json) {
        if (json == null || json.isBlank()) return null;
        Map<String, String> m = parseScalarFields(json);
        if (m.isEmpty()) return null;
        return new PageFooter.Builder()
            .leftText(m.get("leftText"))
            .centerText(m.get("centerText"))
            .rightText(m.get("rightText"))
            .showPageNumbers(!"false".equalsIgnoreCase(m.getOrDefault("showPageNumbers", "true")))
            .pageNumberFormat(m.get("pageNumberFormat"))
            .bandColor(m.get("bandColor"))
            .bandHeight(floatVal(m, "bandHeight", 28f))
            .build();
    }

    // -----------------------------------------------------------------------
    // Section parsing
    // -----------------------------------------------------------------------

    private List<TemplateSection> parseSections(String sectionsJson) {
        List<TemplateSection> sections = new ArrayList<>();
        int depth = 0, objStart = -1;

        for (int i = 0; i < sectionsJson.length(); i++) {
            char c = sectionsJson.charAt(i);
            if      (c == '{') { if (depth == 0) objStart = i; depth++; }
            else if (c == '}') {
                depth--;
                if (depth == 0 && objStart != -1) {
                    try { sections.add(parseSection(sectionsJson.substring(objStart, i + 1))); }
                    catch (Exception e) { logger.warning("Skipping section: " + e.getMessage()); }
                    objStart = -1;
                }
            }
        }
        return sections;
    }

    private TemplateSection parseSection(String sectionJson) {
        Map<String, String> m = parseScalarFields(sectionJson);
        TemplateSection.Type type = TemplateSection.Type.fromString(m.get("type"));

        TemplateSection.Builder builder = new TemplateSection.Builder(type)
            .content(m.get("content"))
            .align(parseAlign(m.getOrDefault("align", "left")))
            .widthPercent(intVal(m, "widthPercent", 100))
            .bgColor(m.get("bgColor"));

        if (type == TemplateSection.Type.TABLE) {
            builder.dataKey(m.get("dataKey"))
                   .headerBgColor(m.get("headerBgColor"))
                   .alternateRowColor(m.get("alternateRowColor"))
                   .repeatHeaderOnPage(!"false".equalsIgnoreCase(m.getOrDefault("repeatHeaderOnPage", "true")));
            String columnsRaw = extractArrayContent(sectionJson, "columns");
            if (columnsRaw != null) builder.columns(parseColumns(columnsRaw));
        }

        if (type == TemplateSection.Type.COLUMNS) {
            // Parse left and right sub-section arrays
            String leftRaw  = extractArrayContent(sectionJson, "left");
            String rightRaw = extractArrayContent(sectionJson, "right");
            int leftWidth   = intVal(m, "leftWidth", 50);
            List<TemplateSection> left  = leftRaw  != null ? parseSections(leftRaw)  : List.of();
            List<TemplateSection> right = rightRaw != null ? parseSections(rightRaw) : List.of();
            builder.columnsData(new ColumnsSection(leftWidth, left, right));
        }

        return builder.build();
    }

    private List<ColumnDef> parseColumns(String columnsJson) {
        List<ColumnDef> columns = new ArrayList<>();
        int depth = 0, objStart = -1;
        for (int i = 0; i < columnsJson.length(); i++) {
            char c = columnsJson.charAt(i);
            if      (c == '{') { if (depth == 0) objStart = i; depth++; }
            else if (c == '}') {
                depth--;
                if (depth == 0 && objStart != -1) {
                    Map<String, String> m = parseScalarFields(columnsJson.substring(objStart + 1, i));
                    columns.add(new ColumnDef(
                        m.getOrDefault("key", ""),
                        m.getOrDefault("header", m.getOrDefault("key", "")),
                        intVal(m, "widthPct", 20),
                        ColumnDef.alignFromString(m.getOrDefault("align", "left"))
                    ));
                    objStart = -1;
                }
            }
        }
        return columns;
    }

    // -----------------------------------------------------------------------
    // JSON helpers
    // -----------------------------------------------------------------------

    private List<String> extractTopLevelObjects(String json) {
        List<String> objects = new ArrayList<>();
        int arrayStart = json.indexOf('['), arrayEnd = json.lastIndexOf(']');
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

    private Map<String, String> parseScalarFields(String objContent) {
        Map<String, String> map = new LinkedHashMap<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([\\d.\\-]+)|(true|false))")
                           .matcher(objContent);
        while (m.find()) {
            String key   = m.group(1).trim();
            String value = m.group(2) != null ? unescape(m.group(2))
                         : m.group(3) != null ? m.group(3)
                         : m.group(4);
            if (value != null) map.put(key, value);
        }
        return map;
    }

    private TemplateSection.Align parseAlign(String v) {
        if (v == null) return TemplateSection.Align.LEFT;
        return switch (v.trim().toUpperCase()) {
            case "CENTER" -> TemplateSection.Align.CENTER;
            case "RIGHT"  -> TemplateSection.Align.RIGHT;
            default       -> TemplateSection.Align.LEFT;
        };
    }

    private String unescape(String v) {
        return v.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String require(Map<String, String> m, String key) {
        String v = m.get(key);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing field: " + key);
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
