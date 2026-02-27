package com.pdfcreator.template;

import java.util.*;
import java.util.logging.Logger;
import java.util.regex.*;

/**
 * Resolves {{placeholder}} tokens in template section content
 * by substituting values from a data map.
 *
 * Placeholder syntax:  {{key}}
 *   - Keys are case-sensitive
 *   - Whitespace inside braces is ignored: {{ key }} resolves the same as {{key}}
 *   - Unresolved placeholders (keys not in the data map) are left as-is
 *     and a warning is logged so the caller knows what data is missing
 *
 * Example:
 *   Template content : "Invoice #{{invoice_no}} — {{client_name}}"
 *   Data map         : { "invoice_no": "0042", "client_name": "Acme Ltd" }
 *   Resolved output  : "Invoice #0042 — Acme Ltd"
 */
public class PlaceholderResolver {

    private static final Logger logger = Logger.getLogger(PlaceholderResolver.class.getName());

    // Matches {{key}} with optional surrounding whitespace inside braces
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*\\}\\}");

    private final Map<String, String> data;

    public PlaceholderResolver(Map<String, String> data) {
        this.data = data != null ? data : Map.of();
    }

    /**
     * Resolves all placeholders in the given text.
     * Returns the original text if it contains no placeholders.
     * Returns null unchanged if input is null.
     */
    public String resolve(String text) {
        if (text == null || text.isBlank()) return text;

        Matcher matcher = PLACEHOLDER.matcher(text);
        if (!matcher.find()) return text; // fast path — no placeholders

        // Reset and process
        matcher.reset();
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key   = matcher.group(1);
            String value = data.get(key);
            if (value != null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(value));
            } else {
                logger.warning("Unresolved placeholder: {{" + key + "}} — no value provided in data file.");
                matcher.appendReplacement(result, matcher.group(0)); // leave as-is
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Resolves all placeholders across an entire list of sections.
     * Returns a new list of sections with resolved content — originals are unchanged.
     */
    public List<TemplateSection> resolveSections(List<TemplateSection> sections) {
        List<TemplateSection> resolved = new ArrayList<>(sections.size());
        for (TemplateSection section : sections) {
            String resolvedContent = resolve(section.getContent());
            resolved.add(section.withContent(resolvedContent));
        }
        return resolved;
    }

    /**
     * Returns all placeholder keys found in a list of sections.
     * Useful for validating that all required data keys are present before rendering.
     */
    public static Set<String> extractPlaceholders(List<TemplateSection> sections) {
        Set<String> keys = new LinkedHashSet<>();
        for (TemplateSection section : sections) {
            if (section.getContent() == null) continue;
            Matcher m = PLACEHOLDER.matcher(section.getContent());
            while (m.find()) keys.add(m.group(1).trim());
        }
        return keys;
    }

    /**
     * Validates that all placeholders in the given sections have a corresponding
     * value in the data map. Returns a list of missing keys (empty = all good).
     */
    public List<String> findMissingKeys(List<TemplateSection> sections) {
        List<String> missing = new ArrayList<>();
        for (String key : extractPlaceholders(sections)) {
            if (!data.containsKey(key)) missing.add(key);
        }
        return missing;
    }
}
