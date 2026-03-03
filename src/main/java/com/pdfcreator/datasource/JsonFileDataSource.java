package com.pdfcreator.datasource;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.*;

/**
 * DataSource implementation that reads a JSON file.
 *
 * Supported JSON structure:
 * {
 *   "scalar_key": "scalar value",
 *   "another_key": "another value",
 *   "transactions": [
 *     { "date": "03 Feb", "description": "TESCO", "debit": "42.30", "credit": "", "balance": "4167.70" },
 *     { "date": "05 Feb", "description": "SALARY", "debit": "", "credit": "3200.00", "balance": "7367.70" }
 *   ],
 *   "line_items": [
 *     { "description": "Consulting", "qty": "3", "rate": "800.00", "amount": "2400.00" }
 *   ]
 * }
 *
 * Rules:
 *   - Top-level string values  → scalars
 *   - Top-level array values   → lists (each element must be a flat object)
 *   - Nested objects           → not supported (use flat structure)
 *   - \n in string values      → preserved as newlines (paragraph breaks in body text)
 */
public class JsonFileDataSource implements DataSource {

    private static final Logger logger = Logger.getLogger(JsonFileDataSource.class.getName());

    private final String filePath;

    public JsonFileDataSource(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public DocumentData load() throws IOException {
        Path path = Path.of(filePath);
        if (!Files.exists(path))
            throw new FileNotFoundException("Data file not found: " + filePath);
        if (Files.isDirectory(path))
            throw new IllegalArgumentException("Data file path is a directory: " + filePath);

        String json = Files.readString(path).trim();
        if (json.isBlank())
            throw new IllegalArgumentException("Data file is empty: " + filePath);

        DocumentData data = parse(json);
        logger.info(String.format("Loaded data from %s: %s", filePath, data));
        return data;
    }

    @Override
    public String describe() {
        return "JSON file: " + filePath;
    }

    // -----------------------------------------------------------------------
    // Parsing
    // -----------------------------------------------------------------------

    private DocumentData parse(String json) {
        DocumentData.Builder builder = new DocumentData.Builder();

        // Strip outer braces
        int start = json.indexOf('{');
        int end   = json.lastIndexOf('}');
        if (start == -1 || end == -1)
            throw new IllegalArgumentException("Not a valid JSON object: " + filePath);

        String body = json.substring(start + 1, end);

        // Walk through top-level keys
        int i = 0;
        while (i < body.length()) {
            // Find next key
            int keyStart = body.indexOf('"', i);
            if (keyStart == -1) break;
            int keyEnd = body.indexOf('"', keyStart + 1);
            if (keyEnd == -1) break;
            String key = body.substring(keyStart + 1, keyEnd);

            // Find the colon
            int colon = body.indexOf(':', keyEnd + 1);
            if (colon == -1) break;

            // What follows the colon?
            int valueStart = colon + 1;
            while (valueStart < body.length() && Character.isWhitespace(body.charAt(valueStart)))
                valueStart++;

            if (valueStart >= body.length()) break;
            char firstChar = body.charAt(valueStart);

            if (firstChar == '"') {
                // Scalar string value
                int[] result = readStringValue(body, valueStart);
                String value = unescape(body.substring(valueStart + 1, result[0]));
                builder.scalar(key, value);
                i = result[1];

            } else if (firstChar == '[') {
                // Array value → list of row maps
                int arrayEnd = findMatchingBracket(body, valueStart);
                if (arrayEnd == -1) break;
                String arrayContent = body.substring(valueStart + 1, arrayEnd);
                List<Map<String, String>> rows = parseRowArray(arrayContent);
                builder.list(key, rows);
                i = arrayEnd + 1;

            } else {
                // Number or boolean scalar — treat as string
                int nextComma  = body.indexOf(',', valueStart);
                int nextNewKey = body.indexOf('"', valueStart);
                int end2 = (nextComma != -1 && (nextNewKey == -1 || nextComma < nextNewKey))
                    ? nextComma : nextNewKey;
                if (end2 == -1) end2 = body.length();
                String value = body.substring(valueStart, end2).trim().replaceAll("[,}]", "");
                builder.scalar(key, value);
                i = end2;
            }
        }

        return builder.build();
    }

    /** Parses a JSON array of flat objects into a List of row maps. */
    private List<Map<String, String>> parseRowArray(String arrayContent) {
        List<Map<String, String>> rows = new ArrayList<>();
        int depth = 0, objStart = -1;

        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            if      (c == '{') { if (depth == 0) objStart = i; depth++; }
            else if (c == '}') {
                depth--;
                if (depth == 0 && objStart != -1) {
                    Map<String, String> row = parseFlatObject(arrayContent.substring(objStart + 1, i));
                    if (!row.isEmpty()) rows.add(row);
                    objStart = -1;
                }
            }
        }
        return rows;
    }

    /** Parses "key": "value" pairs from an object body. */
    private Map<String, String> parseFlatObject(String objBody) {
        Map<String, String> map = new LinkedHashMap<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([\\d.\\-+eE]+)|(true|false|null))")
                           .matcher(objBody);
        while (m.find()) {
            String key   = m.group(1);
            String value = m.group(2) != null ? unescape(m.group(2))
                         : m.group(3) != null ? m.group(3)
                         : m.group(4) != null ? m.group(4)
                         : "";
            map.put(key, value);
        }
        return map;
    }

    /**
     * Reads a quoted string value starting at position pos (which points at the opening quote).
     * Returns [end_content_pos, next_scan_pos].
     */
    private int[] readStringValue(String s, int pos) {
        int i = pos + 1; // skip opening quote
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') { i += 2; continue; } // skip escaped char
            if (c == '"')  break;
            i++;
        }
        return new int[]{ i, i + 1 };
    }

    private int findMatchingBracket(String s, int openPos) {
        int depth = 0;
        for (int i = openPos; i < s.length(); i++) {
            if      (s.charAt(i) == '[') depth++;
            else if (s.charAt(i) == ']') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private String unescape(String v) {
        return v.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
