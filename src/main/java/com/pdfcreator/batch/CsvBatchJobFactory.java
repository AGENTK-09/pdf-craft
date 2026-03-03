package com.pdfcreator.batch;

import com.pdfcreator.datasource.DocumentData;
import com.pdfcreator.datasource.DataSource;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Creates BatchJobs by reading a CSV file where each row is one render job.
 *
 * CSV format:
 *   - First row = column headers (used as DocumentData scalar keys)
 *   - Each subsequent row = one customer / one PDF
 *   - One column must be designated as the "reference ID" (default: "ref_id")
 *   - One column may be designated as the "output filename" (default: "output_file")
 *     If absent, output is named <ref_id>.pdf in the outputDir
 *
 * Example CSV:
 *   ref_id,output_file,customer_name,account_no,opening_balance,closing_balance
 *   CUST-001,CUST-001-feb26.pdf,Jane Smith,12345678,£4210.00,£5586.55
 *   CUST-002,CUST-002-feb26.pdf,Bob Jones,87654321,£892.10,£1240.30
 *
 * This factory only handles scalar data per row. For customers needing
 * tabular transaction data, use DirectoryBatchJobFactory (one JSON per customer)
 * or a DatabaseDataSource implementation.
 *
 * Usage:
 *   List<BatchJob> jobs = new CsvBatchJobFactory(
 *       "bank-statement",           // templateId
 *       "data/customers.csv",       // csvFilePath
 *       "statements/",              // outputDir
 *       "ref_id",                   // refIdColumn
 *       "output_file"               // outputFileColumn (or null)
 *   ).createJobs();
 */
public class CsvBatchJobFactory {

    private static final Logger logger = Logger.getLogger(CsvBatchJobFactory.class.getName());

    private final String templateId;
    private final String csvFilePath;
    private final String outputDir;
    private final String refIdColumn;
    private final String outputFileColumn; // may be null

    public CsvBatchJobFactory(String templateId, String csvFilePath,
                               String outputDir, String refIdColumn,
                               String outputFileColumn) {
        this.templateId       = templateId;
        this.csvFilePath      = csvFilePath;
        this.outputDir        = outputDir.endsWith("/") ? outputDir : outputDir + "/";
        this.refIdColumn      = refIdColumn;
        this.outputFileColumn = outputFileColumn;
    }

    public List<BatchJob> createJobs() throws IOException {
        Path path = Path.of(csvFilePath);
        if (!Files.exists(path))
            throw new FileNotFoundException("CSV file not found: " + csvFilePath);

        List<String> lines = Files.readAllLines(path);
        if (lines.size() < 2) {
            logger.warning("CSV file has no data rows: " + csvFilePath);
            return List.of();
        }

        String[] headers = parseCsvLine(lines.get(0));
        List<BatchJob> jobs = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isBlank() || line.startsWith("#")) continue; // skip blanks and comments

            String[] values = parseCsvLine(line);
            Map<String, String> row = new LinkedHashMap<>();
            for (int col = 0; col < headers.length && col < values.length; col++) {
                row.put(headers[col].trim(), values[col].trim());
            }

            String refId      = row.getOrDefault(refIdColumn, "row-" + i);
            String outputFile = outputFileColumn != null
                ? row.getOrDefault(outputFileColumn, refId + ".pdf")
                : refId + ".pdf";
            String outputPath = outputDir + outputFile;

            DocumentData data = DocumentData.ofScalars(row);
            DataSource   ds   = new InlineDataSource(data, "CSV row " + refId);

            jobs.add(new BatchJob(refId, templateId, ds, outputPath));
        }

        logger.info("Created " + jobs.size() + " batch jobs from: " + csvFilePath);
        return jobs;
    }

    /** Splits a CSV line respecting quoted fields. */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    // -----------------------------------------------------------------------
    // Inline DataSource — wraps pre-built DocumentData (used for CSV rows)
    // -----------------------------------------------------------------------

    private static class InlineDataSource implements DataSource {
        private final DocumentData data;
        private final String       description;

        InlineDataSource(DocumentData data, String description) {
            this.data        = data;
            this.description = description;
        }

        @Override public DocumentData load() { return data; }
        @Override public String describe()   { return description; }
    }
}
