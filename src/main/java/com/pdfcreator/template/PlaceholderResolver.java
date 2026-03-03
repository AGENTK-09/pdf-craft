package com.pdfcreator.template;

import com.pdfcreator.datasource.DocumentData;

import java.util.*;
import java.util.logging.Logger;
import java.util.regex.*;

/**
 * Resolves {{placeholder}} tokens using DocumentData scalar values.
 *
 * Added vs previous version:
 *   - resolveFooter() — resolves text fields in PageFooter
 *   - resolveSections() handles COLUMNS sub-sections recursively
 */
public class PlaceholderResolver {

    private static final Logger  logger      = Logger.getLogger(PlaceholderResolver.class.getName());
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*\\}\\}");

    private final Map<String, String> scalars;

    public PlaceholderResolver(DocumentData data) {
        this.scalars = data != null ? data.getScalars() : Map.of();
    }

    public String resolve(String text) {
        if (text == null || text.isBlank()) return text;
        Matcher m = PLACEHOLDER.matcher(text);
        if (!m.find()) return text;
        m.reset();
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key   = m.group(1);
            String value = scalars.get(key);
            if (value != null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(value));
            } else {
                logger.warning("Unresolved placeholder: {{" + key + "}}");
                m.appendReplacement(sb, m.group(0));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Resolves placeholders in all sections, including COLUMNS sub-sections. */
    public List<TemplateSection> resolveSections(List<TemplateSection> sections) {
        List<TemplateSection> resolved = new ArrayList<>(sections.size());
        for (TemplateSection section : sections) {
            if (section.getType() == TemplateSection.Type.COLUMNS && section.getColumnsData() != null) {
                ColumnsSection cols = section.getColumnsData();
                ColumnsSection resolvedCols = new ColumnsSection(
                    cols.getLeftWidthPct(),
                    resolveSections(cols.getLeft()),
                    resolveSections(cols.getRight())
                );
                resolved.add(new TemplateSection.Builder(section).columnsData(resolvedCols).build());
            } else {
                resolved.add(section.withContent(resolve(section.getContent())));
            }
        }
        return resolved;
    }

    /** Resolves {{placeholders}} in PageHeader logo path. */
    public PageHeader resolveHeader(PageHeader header) {
        if (header == null || !header.hasLogo()) return header;
        String resolved = resolve(header.getLogoPath());
        if (resolved.equals(header.getLogoPath())) return header;
        return new PageHeader.Builder()
            .logoPath(resolved)
            .logoAlign(header.getLogoAlign().name())
            .logoWidthPercent(header.getLogoWidthPercent())
            .bandColor(header.getBandColor())
            .bandHeight(header.getBandHeight())
            .build();
    }

    /** Resolves {{placeholders}} in all PageFooter text fields. */
    public PageFooter resolveFooter(PageFooter footer) {
        if (footer == null) return null;
        return footer.withResolvedText(
            resolve(footer.getLeftText()),
            resolve(footer.getCenterText()),
            resolve(footer.getRightText())
        );
    }

    public static Set<String> extractPlaceholders(List<TemplateSection> sections) {
        Set<String> keys = new LinkedHashSet<>();
        for (TemplateSection s : sections) {
            if (s.getType() == TemplateSection.Type.COLUMNS && s.getColumnsData() != null) {
                keys.addAll(extractPlaceholders(s.getColumnsData().getLeft()));
                keys.addAll(extractPlaceholders(s.getColumnsData().getRight()));
            } else if (s.getContent() != null) {
                Matcher m = PLACEHOLDER.matcher(s.getContent());
                while (m.find()) keys.add(m.group(1).trim());
            }
        }
        return keys;
    }

    public List<String> findMissingKeys(List<TemplateSection> sections) {
        List<String> missing = new ArrayList<>();
        for (String key : extractPlaceholders(sections))
            if (!scalars.containsKey(key)) missing.add(key);
        return missing;
    }
}
