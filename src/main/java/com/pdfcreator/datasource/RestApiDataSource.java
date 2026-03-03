package com.pdfcreator.datasource;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.*;

/**
 * DataSource implementation that fetches document data from a REST API endpoint.
 *
 * The API is expected to return JSON in the same flat structure as a JSON data file:
 *   - Top-level string values   → DocumentData scalars
 *   - Top-level array values    → DocumentData lists (rows of flat objects)
 *
 * This means your existing JSON data file format *is* your API contract.
 * If you can serve that JSON from an endpoint, this DataSource works with no
 * changes to templates or renderers.
 *
 * ---
 *
 * BANK STATEMENT EXAMPLE
 *
 * API endpoint per customer:
 *   GET https://api.bank.internal/statements/{accountNo}?period=2026-02
 *
 * Response body (same shape as a JSON data file):
 *   {
 *     "customer_name":  "Jane Smith",
 *     "account_no":     "12345678",
 *     "opening_balance": "£4,210.00",
 *     "transactions": [
 *       { "date": "03 Feb", "description": "TESCO", "debit": "42.30", "credit": "", "balance": "4167.70" }
 *     ]
 *   }
 *
 * Usage:
 *   DataSource ds = new RestApiDataSource.Builder()
 *       .url("https://api.bank.internal/statements/12345678?period=2026-02")
 *       .header("Authorization", "Bearer " + token)
 *       .header("Accept", "application/json")
 *       .timeoutSeconds(10)
 *       .description("statement for 12345678")
 *       .build();
 *
 *   pipeline.render("bank-statement", ds, "output/12345678-feb26.pdf");
 *
 * ---
 *
 * BATCH USAGE
 *
 *   List<BatchJob> jobs = accountNumbers.stream().map(accountNo ->
 *       new BatchJob(
 *           accountNo,
 *           "bank-statement",
 *           new RestApiDataSource.Builder()
 *               .url("https://api.bank.internal/statements/" + accountNo + "?period=2026-02")
 *               .header("Authorization", "Bearer " + token)
 *               .build(),
 *           "output/statements/" + accountNo + ".pdf"
 *       )
 *   ).toList();
 *
 *   new BatchRunner(pipeline, 8).run(jobs);
 *
 * Note: rate limiting. If the API throttles requests, reduce the thread count
 * in BatchRunner or add a retry/backoff strategy in load().
 *
 * ---
 *
 * JSON PARSING
 *
 * Uses the same minimal parser as JsonFileDataSource (no external dependencies).
 * Delegates to JsonFileDataSource's parsing logic by writing the response body
 * to a temp file and using JsonFileDataSource to read it, avoiding duplication.
 *
 * If you introduce Jackson or Gson, replace both parsers at the same time.
 */
public class RestApiDataSource implements DataSource {

    private static final Logger logger = Logger.getLogger(RestApiDataSource.class.getName());

    private final String              url;
    private final Map<String, String> headers;
    private final int                 timeoutSeconds;
    private final String              description;

    private RestApiDataSource(Builder b) {
        this.url            = b.url;
        this.headers        = Collections.unmodifiableMap(new LinkedHashMap<>(b.headers));
        this.timeoutSeconds = b.timeoutSeconds;
        this.description    = b.description;
    }

    @Override
    public DocumentData load() throws IOException {
        logger.info("Fetching data from: " + url);

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(timeoutSeconds))
            .build();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .GET();

        headers.forEach(requestBuilder::header);

        HttpRequest  request = requestBuilder.build();
        HttpResponse<String> response;

        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted: " + url, e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(String.format(
                "API returned HTTP %d for %s", response.statusCode(), url));
        }

        String json = response.body();
        if (json == null || json.isBlank()) {
            throw new IOException("API returned empty body for: " + url);
        }

        // Parse using the same logic as JsonFileDataSource
        // Write to a temp file and delegate — avoids duplicating the parser
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("pdf-creator-api-", ".json");
        try {
            java.nio.file.Files.writeString(tempFile, json);
            DocumentData data = new JsonFileDataSource(tempFile.toString()).load();
            logger.info("API data loaded: " + data);
            return data;
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Override
    public String describe() {
        return "REST[" + description + " → " + url + "]";
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private String              url;
        private Map<String, String> headers        = new LinkedHashMap<>();
        private int                 timeoutSeconds = 30;
        private String              description    = "";

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public Builder bearerToken(String token) {
            return header("Authorization", "Bearer " + token);
        }

        public Builder basicAuth(String username, String password) {
            String encoded = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes());
            return header("Authorization", "Basic " + encoded);
        }

        public Builder timeoutSeconds(int seconds) {
            this.timeoutSeconds = seconds;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public RestApiDataSource build() {
            if (url == null || url.isBlank())
                throw new IllegalStateException("RestApiDataSource requires a URL");
            return new RestApiDataSource(this);
        }
    }
}
