package com.pdfcreator.datasource;

import java.util.*;

/**
 * The unified data model passed through the render pipeline.
 *
 * Replaces the previous Map<String, String> with two compartments:
 *
 *   scalars  — flat key-value pairs used for {{placeholder}} substitution
 *              e.g. "customer_name" -> "Jane Smith"
 *
 *   lists    — named collections of rows used for table rendering
 *              e.g. "transactions" -> [ {date, description, debit, credit, balance}, ... ]
 *
 * All values are strings. Type conversion (formatting decimals, dates) is the
 * responsibility of the data source that produces the DocumentData, not the renderer.
 *
 * Example JSON data file that produces this structure:
 * {
 *   "customer_name": "Jane Smith",
 *   "account_no":    "12-34-56 / 87654321",
 *   "transactions": [
 *     { "date": "03 Feb", "description": "TESCO", "debit": "42.30", "credit": "", "balance": "4167.70" }
 *   ]
 * }
 */
public class DocumentData {

    private final Map<String, String>                   scalars;
    private final Map<String, List<Map<String, String>>> lists;

    private DocumentData(Builder builder) {
        this.scalars = Collections.unmodifiableMap(new LinkedHashMap<>(builder.scalars));
        this.lists   = Collections.unmodifiableMap(new LinkedHashMap<>(builder.lists));
    }

    /** Returns the scalar value for a key, or null if not present. */
    public String getScalar(String key) {
        return scalars.get(key);
    }

    /** Returns all scalar key-value pairs. */
    public Map<String, String> getScalars() {
        return scalars;
    }

    /**
     * Returns the named list of rows, or an empty list if not present.
     * Each row is a Map<String, String> of column key -> cell value.
     */
    public List<Map<String, String>> getList(String key) {
        return lists.getOrDefault(key, List.of());
    }

    /** Returns all named lists. */
    public Map<String, List<Map<String, String>>> getLists() {
        return lists;
    }

    public boolean hasScalar(String key) { return scalars.containsKey(key); }
    public boolean hasList(String key)   { return lists.containsKey(key) && !lists.get(key).isEmpty(); }

    @Override
    public String toString() {
        return String.format("DocumentData[scalars=%d, lists=%s]",
            scalars.size(),
            lists.entrySet().stream()
                 .map(e -> e.getKey() + "(" + e.getValue().size() + " rows)")
                 .toList());
    }

    // --- Builder ---

    public static class Builder {
        private final Map<String, String>                    scalars = new LinkedHashMap<>();
        private final Map<String, List<Map<String, String>>> lists   = new LinkedHashMap<>();

        public Builder scalar(String key, String value) {
            scalars.put(key, value);
            return this;
        }

        public Builder scalars(Map<String, String> map) {
            scalars.putAll(map);
            return this;
        }

        public Builder list(String key, List<Map<String, String>> rows) {
            lists.put(key, rows);
            return this;
        }

        public DocumentData build() {
            return new DocumentData(this);
        }
    }

    /** Convenience factory for data with only scalars (e.g. simple letters). */
    public static DocumentData ofScalars(Map<String, String> scalars) {
        return new Builder().scalars(scalars).build();
    }
}
