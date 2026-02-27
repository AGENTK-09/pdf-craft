package com.pdfcreator.template;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Loads the user-supplied data file (JSON) into a flat Map<String, String>.
 *
 * The data file provides values for the {{placeholder}} tokens defined in a template.
 *
 * Supported JSON format — a flat object of string key-value pairs:
 * {
 *   "company_name": "Acme Ltd",
 *   "invoice_no":   "0042",
 *   "client_name":  "Globex Corp",
 *   "due_date":     "2026-03-01",
 *   "notes":        "Payment due within 30 days."
 * }
 *
 * Multi-line values: use \n in the JSON string value to insert paragraph breaks.
 * Example: "body": "Line one.\nLine two.\nLine three."
 *
 * Note: nested objects and arrays are not supported. If the schema grows to need
 * those, replace this class with a Jackson/Gson-based implementation.
 */
public class DataLoader {

    private static final Logger logger = Logger.getLogger(DataLoader.class.getName());

    /**
     * Loads and returns all key-value pairs from the given JSON file.
     *
     * @param dataFilePath path to the JSON data file
     * @return map of placeholder key -> value
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if the file is empty or not valid flat JSON
     */
    public Map<String, String> load(String dataFilePath) throws IOException {
        Path path = Path.of(dataFilePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Data file not found: " + dataFilePath);
        }
        if (Files.isDirectory(path)) {
            throw new IllegalArgumentException("--data-file points to a directory: " + dataFilePath);
        }

        String json = Files.readString(path).trim();
        if (json.isBlank()) {
            throw new IllegalArgumentException("Data file is empty: " + dataFilePath);
        }

        Map<String, String> data = parseFlat(json);
        logger.info("Loaded " + data.size() + " data entries from: " + dataFilePath);
        return data;
    }

    // -----------------------------------------------------------------------
    // Minimal flat JSON parser
    // Handles: "key": "value" pairs at the top level.
    // Handles escaped quotes (\") and \n within values.
    // -----------------------------------------------------------------------

    private Map<String, String> parseFlat(String json) {
        Map<String, String> map = new LinkedHashMap<>();

        // Strip outer braces
        int start = json.indexOf('{');
        int end   = json.lastIndexOf('}');
        if (start == -1 || end == -1) {
            throw new IllegalArgumentException("Data file does not appear to be a JSON object.");
        }
        String content = json.substring(start + 1, end);

        // Match "key": "value" pairs including escaped characters in values
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\""
        );
        java.util.regex.Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String key   = matcher.group(1).trim();
            String value = unescapeJson(matcher.group(2));
            map.put(key, value);
        }
        return map;
    }

    /**
     * Converts JSON escape sequences to their actual characters.
     * Most importantly: \n -> newline (so template body text can have paragraphs).
     */
    private String unescapeJson(String value) {
        return value
            .replace("\\n",  "\n")
            .replace("\\t",  "\t")
            .replace("\\r",  "\r")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }
}
