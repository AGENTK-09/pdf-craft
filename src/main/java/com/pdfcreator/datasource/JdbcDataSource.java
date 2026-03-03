package com.pdfcreator.datasource;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * DataSource implementation that fetches document data from a relational database
 * via JDBC.
 *
 * Design:
 *   A JdbcDataSource is configured with:
 *     - A JDBC connection supplier (Connection is not shared across threads)
 *     - A scalar query: one row of key-value pairs (customer name, account no, etc.)
 *     - Zero or more list queries: each produces a named list of rows (transactions, charges)
 *
 * This maps directly onto DocumentData's two-compartment model:
 *   scalar query  →  DocumentData.scalars
 *   list queries  →  DocumentData.lists
 *
 * Thread safety:
 *   Each call to load() opens and closes its own Connection using the supplier.
 *   The supplier itself must be thread-safe (e.g. a connection pool like HikariCP).
 *   Never share a Connection across threads.
 *
 * ---
 *
 * BANK STATEMENT EXAMPLE
 *
 * Scalar query — one row per customer:
 *   SELECT
 *     c.full_name        AS customer_name,
 *     a.account_no,
 *     a.sort_code,
 *     '01 Feb 2026'      AS period_from,
 *     '28 Feb 2026'      AS period_to,
 *     a.opening_balance,
 *     a.closing_balance
 *   FROM customers c
 *   JOIN accounts a ON a.customer_id = c.id
 *   WHERE a.account_no = ?
 *
 * List query "transactions" — variable rows:
 *   SELECT
 *     TO_CHAR(t.txn_date, 'DD Mon')   AS date,
 *     t.txn_type                       AS type,
 *     t.description,
 *     CASE WHEN t.amount < 0 THEN ABS(t.amount)::TEXT ELSE '' END AS debit,
 *     CASE WHEN t.amount > 0 THEN t.amount::TEXT       ELSE '' END AS credit
 *   FROM transactions t
 *   WHERE t.account_no = ?
 *     AND t.txn_date BETWEEN '2026-02-01' AND '2026-02-28'
 *   ORDER BY t.txn_date
 *
 * Usage:
 *   JdbcDataSource ds = new JdbcDataSource.Builder()
 *       .connectionSupplier(() -> dataSource.getConnection())
 *       .scalarQuery(
 *           "SELECT full_name AS customer_name, account_no, ... FROM accounts WHERE account_no = ?",
 *           List.of("12345678")   // bind parameters
 *       )
 *       .listQuery("transactions",
 *           "SELECT TO_CHAR(txn_date,'DD Mon') AS date, ... FROM transactions WHERE account_no = ?",
 *           List.of("12345678")
 *       )
 *       .build();
 *
 *   pipeline.render("bank-statement", ds, "output/12345678-feb26.pdf");
 *
 * ---
 *
 * BATCH USAGE
 *
 * In a batch run, create one JdbcDataSource per customer and pass it to BatchRunner:
 *
 *   List<BatchJob> jobs = customerIds.stream().map(id ->
 *       new BatchJob(
 *           id,
 *           "bank-statement",
 *           new JdbcDataSource.Builder()
 *               .connectionSupplier(pool::getConnection)
 *               .scalarQuery(SCALAR_SQL, List.of(id))
 *               .listQuery("transactions", TXN_SQL, List.of(id))
 *               .build(),
 *           "output/statements/" + id + ".pdf"
 *       )
 *   ).toList();
 *
 *   new BatchRunner(pipeline, 8).run(jobs);
 *
 * Each job gets its own Connection from the pool, so 8 threads = 8 concurrent
 * connections. Size the pool accordingly (e.g. HikariCP maximumPoolSize = 10).
 */
public class JdbcDataSource implements DataSource {

    private static final Logger logger = Logger.getLogger(JdbcDataSource.class.getName());

    // Functional interface so callers can pass a connection pool supplier
    @FunctionalInterface
    public interface ConnectionSupplier {
        Connection get() throws SQLException;
    }

    private final ConnectionSupplier      connectionSupplier;
    private final String                  scalarSql;
    private final List<Object>            scalarParams;
    private final Map<String, ListQuery>  listQueries;  // listName -> query
    private final String                  description;

    private JdbcDataSource(Builder b) {
        this.connectionSupplier = b.connectionSupplier;
        this.scalarSql          = b.scalarSql;
        this.scalarParams       = Collections.unmodifiableList(b.scalarParams);
        this.listQueries        = Collections.unmodifiableMap(b.listQueries);
        this.description        = b.description;
    }

    @Override
    public DocumentData load() throws IOException {
        try (Connection conn = connectionSupplier.get()) {
            DocumentData.Builder builder = new DocumentData.Builder();

            // Execute scalar query — expects exactly one row
            if (scalarSql != null) {
                Map<String, String> scalars = executeScalarQuery(conn, scalarSql, scalarParams);
                builder.scalars(scalars);
                logger.fine("Scalar query returned " + scalars.size() + " fields.");
            }

            // Execute each list query
            for (Map.Entry<String, ListQuery> entry : listQueries.entrySet()) {
                String    listName = entry.getKey();
                ListQuery lq       = entry.getValue();
                List<Map<String, String>> rows = executeListQuery(conn, lq.sql, lq.params);
                builder.list(listName, rows);
                logger.fine("List query '" + listName + "' returned " + rows.size() + " rows.");
            }

            DocumentData data = builder.build();
            logger.info("JdbcDataSource loaded: " + data);
            return data;

        } catch (SQLException e) {
            throw new IOException("Database error in JdbcDataSource [" + description + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public String describe() {
        return "JDBC[" + description + "]";
    }

    // -----------------------------------------------------------------------
    // Query execution
    // -----------------------------------------------------------------------

    /**
     * Executes a query expected to return one row.
     * Column labels become keys, values become strings.
     * If the query returns no rows, returns an empty map (and logs a warning).
     * If it returns multiple rows, only the first is used.
     */
    private Map<String, String> executeScalarQuery(Connection conn, String sql,
                                                    List<Object> params) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                if (!rs.next()) {
                    logger.warning("Scalar query returned no rows: " + sql);
                    return Map.of();
                }

                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    String key   = meta.getColumnLabel(i).toLowerCase();
                    String value = rs.getString(i);
                    row.put(key, value != null ? value : "");
                }
                return row;
            }
        }
    }

    /**
     * Executes a query that returns multiple rows.
     * Each row becomes a Map<String, String> using column labels as keys.
     */
    private List<Map<String, String>> executeListQuery(Connection conn, String sql,
                                                        List<Object> params) throws SQLException {
        List<Map<String, String>> rows = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta     = rs.getMetaData();
                int               colCount = meta.getColumnCount();

                while (rs.next()) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        String key   = meta.getColumnLabel(i).toLowerCase();
                        String value = rs.getString(i);
                        row.put(key, value != null ? value : "");
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private void bindParams(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            stmt.setObject(i + 1, params.get(i));
        }
    }

    // -----------------------------------------------------------------------
    // Internal models
    // -----------------------------------------------------------------------

    private static class ListQuery {
        final String       sql;
        final List<Object> params;

        ListQuery(String sql, List<Object> params) {
            this.sql    = sql;
            this.params = params;
        }
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private ConnectionSupplier             connectionSupplier;
        private String                         scalarSql    = null;
        private List<Object>                   scalarParams = List.of();
        private final Map<String, ListQuery>   listQueries  = new LinkedHashMap<>();
        private String                         description  = "unknown";

        /**
         * Sets the connection supplier. Typically a lambda over a connection pool:
         *   .connectionSupplier(hikariPool::getConnection)
         */
        public Builder connectionSupplier(ConnectionSupplier supplier) {
            this.connectionSupplier = supplier;
            return this;
        }

        /**
         * Sets the scalar query — run once, expected to return one row.
         * Column AS aliases become DocumentData scalar keys.
         *
         * @param sql    parameterised SQL (use ? for bind parameters)
         * @param params bind parameter values in order
         */
        public Builder scalarQuery(String sql, List<Object> params) {
            this.scalarSql    = sql;
            this.scalarParams = params != null ? params : List.of();
            return this;
        }

        public Builder scalarQuery(String sql) {
            return scalarQuery(sql, List.of());
        }

        /**
         * Adds a list query whose results populate DocumentData.lists under listName.
         * Multiple list queries can be added (e.g. "transactions" and "charges").
         *
         * @param listName key in DocumentData.lists (must match template's dataKey)
         * @param sql      parameterised SQL
         * @param params   bind parameter values
         */
        public Builder listQuery(String listName, String sql, List<Object> params) {
            listQueries.put(listName, new ListQuery(sql, params != null ? params : List.of()));
            return this;
        }

        public Builder listQuery(String listName, String sql) {
            return listQuery(listName, sql, List.of());
        }

        /** Human-readable description for logging, e.g. customer ID or account number. */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public JdbcDataSource build() {
            if (connectionSupplier == null)
                throw new IllegalStateException("JdbcDataSource requires a connectionSupplier");
            return new JdbcDataSource(this);
        }
    }
}
