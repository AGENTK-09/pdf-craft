package com.pdfcreator.datasource;

import java.io.IOException;

/**
 * Abstraction over the source of document data for a single render job.
 *
 * The rest of the pipeline (RenderPipeline, BatchRunner, renderers) never
 * touches files, databases, or HTTP directly. They only see DocumentData.
 * DataSource is the seam where data origin is decided.
 *
 * ┌─────────────────────────────────────────────────────┐
 * │                    DataSource                        │
 * │                  <<interface>>                       │
 * │  + load()     : DocumentData                         │
 * │  + describe() : String                               │
 * └──────────────────┬──────────────────────────────────┘
 *                    │ implemented by
 *       ┌────────────┼────────────┬──────────────┐
 *       ▼            ▼            ▼              ▼
 * JsonFile      InMemory      Jdbc          RestApi
 * DataSource    DataSource    DataSource    DataSource
 *
 * ─────────────────────────────────────────────────────
 * JsonFileDataSource
 *   Reads a .json file. Supports scalar key-value pairs and named
 *   arrays of row objects. Default for CLI usage and development.
 *   new JsonFileDataSource("data/CUST-001.json")
 *
 * InMemoryDataSource
 *   Wraps a DocumentData object already in memory.
 *   Used by CsvBatchJobFactory (one row = one DocumentData),
 *   and for unit testing without touching the file system.
 *   new InMemoryDataSource(data, "test customer")
 *
 * JdbcDataSource
 *   Executes JDBC queries against a relational database.
 *   Scalar query → scalars, list queries → lists.
 *   One Connection per load() call, obtained from a pool supplier.
 *   new JdbcDataSource.Builder()
 *       .connectionSupplier(pool::getConnection)
 *       .scalarQuery(SQL, List.of(accountNo))
 *       .listQuery("transactions", TXN_SQL, List.of(accountNo))
 *       .build()
 *
 * RestApiDataSource
 *   Fetches JSON from an HTTP endpoint.
 *   Response body must follow the same JSON shape as a data file.
 *   new RestApiDataSource.Builder()
 *       .url("https://api.bank.internal/statements/" + accountNo)
 *       .bearerToken(token)
 *       .build()
 *
 * ─────────────────────────────────────────────────────
 * ADDING A NEW DATASOURCE
 *
 * Implement this interface, produce a DocumentData in load().
 * Nothing else in the pipeline needs to change.
 *
 * Example skeleton:
 *   public class MyCustomDataSource implements DataSource {
 *       @Override
 *       public DocumentData load() throws IOException {
 *           // fetch your data from wherever
 *           return new DocumentData.Builder()
 *               .scalar("customer_name", ...)
 *               .list("transactions", rows)
 *               .build();
 *       }
 *       @Override
 *       public String describe() { return "MyCustom[...]"; }
 *   }
 */
public interface DataSource {

    /**
     * Loads and returns the document data for this render job.
     *
     * Called once per render job. Implementations should be idempotent
     * (calling load() twice should return equivalent data) but are not
     * required to cache results — that is the caller's responsibility
     * if needed.
     *
     * @throws IOException if the data cannot be read for any reason
     *                     (file not found, DB connection failure, HTTP error, etc.)
     */
    DocumentData load() throws IOException;

    /**
     * Returns a human-readable description of this data source.
     * Used in console output, logs, and BatchResult summaries.
     * Should identify the data origin clearly enough to trace a failure.
     *
     * Examples:
     *   "JSON file: data/CUST-001.json"
     *   "JDBC[account 12345678]"
     *   "REST[GET /statements/12345678?period=2026-02]"
     *   "InMemory[CSV row CUST-003]"
     */
    String describe();
}
