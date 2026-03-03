package com.pdfcreator.datasource;

import java.util.List;
import java.util.Map;

/**
 * DataSource backed by a pre-built DocumentData object held in memory.
 *
 * Primary use cases:
 *   1. Unit testing — build DocumentData in code, render, assert output.
 *   2. Batch CSV processing — CsvBatchJobFactory builds a DocumentData per
 *      row and wraps it in an InMemoryDataSource. No file I/O per job.
 *   3. Programmatic integration — when a calling system already has the data
 *      in memory (e.g. fetched from a REST API, assembled from a database
 *      result set) and just wants to render without writing a temp file.
 *
 * Example (test):
 *   DocumentData data = new DocumentData.Builder()
 *       .scalar("customer_name", "Jane Smith")
 *       .scalar("account_no",    "12345678")
 *       .list("transactions", List.of(
 *           Map.of("date", "03 Feb", "description", "TESCO", "debit", "42.30", "credit", "", "balance", "4167.70")
 *       ))
 *       .build();
 *
 *   new RenderPipeline(templateFile, configFile)
 *       .render("bank-statement", new InMemoryDataSource(data, "test customer"), "output/test.pdf");
 */
public class InMemoryDataSource implements DataSource {

    private final DocumentData data;
    private final String       description;

    public InMemoryDataSource(DocumentData data, String description) {
        if (data == null) throw new IllegalArgumentException("DocumentData must not be null");
        this.data        = data;
        this.description = description != null ? description : "in-memory";
    }

    /** Convenience constructor — scalars only, no lists. */
    public InMemoryDataSource(Map<String, String> scalars, String description) {
        this(DocumentData.ofScalars(scalars), description);
    }

    @Override
    public DocumentData load() {
        return data;
    }

    @Override
    public String describe() {
        return "InMemory[" + description + "]";
    }
}
