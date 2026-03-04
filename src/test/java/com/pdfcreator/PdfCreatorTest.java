package com.pdfcreator;

import com.pdfcreator.batch.*;
import com.pdfcreator.extractor.*;
import com.pdfcreator.manipulator.*;
import com.pdfcreator.datasource.*;
import com.pdfcreator.pipeline.RenderPipeline;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Integration test suite for PdfCreator.
 *
 * Covers every major mode and scenario:
 *
 *   1.  Direct mode   — inline title + text
 *   2.  Direct mode   — text from file
 *   3.  Direct mode   — with a custom config preset
 *   4.  Template mode — bank statement (columns + table + summary + footer)
 *   5.  Template mode — credit card statement
 *   6.  Template mode — notification letter (simple correspondence)
 *   7.  Template mode — missing placeholder warning (non-fatal)
 *   8.  Batch mode    — directory source (bank statements, parallel)
 *   9.  Batch mode    — CSV source (notification letters, parallel)
 *   10. Batch mode    — single thread (ensures serial path works)
 *   11. DataSource    — InMemoryDataSource (programmatic data, no file)
 *   12. DataSource    — non-existent data file (expected IOException)
 *   13. Template      — non-existent template id (expected exception)
 *   14. Direct mode   — empty text (should produce a valid single-page PDF)
 *
 * Run:
 *   java -cp target/pdf-creator-1.0-SNAPSHOT.jar com.pdfcreator.PdfCreatorTest
 *
 * Or build and run in one step:
 *   mvn package -q && java -cp target/pdf-creator-1.0-SNAPSHOT.jar com.pdfcreator.PdfCreatorTest
 *
 * All output PDFs are written to test-output/ and verified to exist and be
 * non-empty. No assertion library is required — pass/fail is printed to stdout.
 */
public class PdfCreatorTest {

    // -----------------------------------------------------------------------
    // Paths — adjust if your working directory differs from project root
    // -----------------------------------------------------------------------

    private static final String TEMPLATE_FILE = "templates/pdf-templates.json";
    private static final String CONFIG_FILE   = "configs/pdf-configs.json";
    private static final String DATA_DIR      = "data";
    private static final String OUT_DIR       = "test-output";

    // -----------------------------------------------------------------------
    // Test registry
    // -----------------------------------------------------------------------

    private static int passed = 0;
    private static int failed = 0;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        System.out.println("═".repeat(65));
        System.out.println("  PdfCreator Integration Tests");
        System.out.println("═".repeat(65));

        // Prepare output directory
        Files.createDirectories(Path.of(OUT_DIR));
        Files.createDirectories(Path.of(OUT_DIR + "/batch-statements"));
        Files.createDirectories(Path.of(OUT_DIR + "/batch-notifications"));

        // ── Run all tests ──────────────────────────────────────────────────
        test01_directMode_inlineText();
        test02_directMode_textFromFile();
        test03_directMode_customConfig();
        test04_templateMode_bankStatement();
        test05_templateMode_creditCardStatement();
        test06_templateMode_notificationLetter();
        test07_templateMode_missingPlaceholderWarning();
        test08_batchMode_directorySource();
        test09_batchMode_csvSource();
        test10_batchMode_singleThread();
        test11_inMemoryDataSource();
        test12_missingDataFile_expectsException();
        test13_missingTemplateId_expectsException();
        test14_directMode_titleOnly();
        test15_extract_fullDocument();
        test16_extract_pageRange();
        test17_extract_metadataFields();
        test18_extract_toFile();
        test19_extractImages_fromGeneratedPdf();
        test20_extractImages_minSizeFilter();
        test21_extractImages_toDirectory();
        test22_extract_passwordProtected();
        test23_extract_wrongPassword();
        test24_merge_basicThreeFiles();
        test25_merge_withPasswordProtected();
        test26_split_everyNPages();
        test27_split_intoParts();

        // ── Summary ────────────────────────────────────────────────────────
        System.out.println();
        System.out.println("═".repeat(65));
        System.out.printf("  Results: %d passed, %d failed%n", passed, failed);
        if (!failures.isEmpty()) {
            System.out.println("  Failed tests:");
            failures.forEach(f -> System.out.println("    ✗ " + f));
        }
        System.out.println("═".repeat(65));

        System.exit(failed > 0 ? 1 : 0);
    }

    // -----------------------------------------------------------------------
    // TEST 1 — Direct mode: inline title + text
    // -----------------------------------------------------------------------
    static void test01_directMode_inlineText() {
        run("01 Direct mode — inline title + text", () ->
            PdfCreator.main(new String[]{
                "--title",  "Meeting Notes — Q1 2026",
                "--author", "Jane Smith",
                "--text",   "Attendees: Jane Smith, Robert Jones, Priya Patel.\n\n" +
                            "Key decisions: approved Q1 budget, deferred platform migration to Q2, " +
                            "agreed fortnightly standups.\n\n" +
                            "Actions: Jane to circulate revised budget by Friday. " +
                            "Robert to produce migration risk assessment by end of month.",
                "--output", OUT_DIR + "/01-direct-inline.pdf"
            })
        );
    }

    // -----------------------------------------------------------------------
    // TEST 2 — Direct mode: text from file
    // -----------------------------------------------------------------------
    static void test02_directMode_textFromFile() throws Exception {
        // Write a temp text file
        Path textFile = Path.of(OUT_DIR + "/test-body.txt");
        Files.writeString(textFile,
            "This is paragraph one of a longer body text. It contains enough content to " +
            "demonstrate proper word wrapping behaviour across the full usable page width.\n\n" +
            "This is paragraph two. The body renderer should insert a blank line between " +
            "paragraphs wherever a newline character appears in the source text.\n\n" +
            "This is paragraph three. If the document is long enough, a new page should be " +
            "started automatically when the cursor reaches the bottom margin.");

        run("02 Direct mode — text from file", () ->
            PdfCreator.main(new String[]{
                "--title",     "Report from File",
                "--text-file", textFile.toString(),
                "--output",    OUT_DIR + "/02-direct-from-file.pdf"
            })
        );
    }

    // -----------------------------------------------------------------------
    // TEST 3 — Direct mode: custom config preset
    // -----------------------------------------------------------------------
    static void test03_directMode_customConfig() {
        run("03 Direct mode — custom config (report preset)", () ->
            PdfCreator.main(new String[]{
                "--config-id", "report",
                "--title",     "Q4 Executive Summary",
                "--text",      "Revenue exceeded targets by 12% in Q4. Operating costs remained " +
                               "within budget. The board approved the proposed expansion into " +
                               "three new markets for FY2027.",
                "--output",    OUT_DIR + "/03-direct-report-config.pdf"
            })
        );
    }

    // -----------------------------------------------------------------------
    // TEST 4 — Template mode: bank statement
    //          Exercises: columns layout, summary, table with 19 rows,
    //                     header band, footer with page numbers, page break,
    //                     notice section
    // -----------------------------------------------------------------------
    static void test04_templateMode_bankStatement() {
        run("04 Template mode — bank statement (full feature set)", () ->
            PdfCreator.main(new String[]{
                "--template-id", "bank-statement",
                "--data-file",   DATA_DIR + "/statements/CUST-001.json",
                "--output",      OUT_DIR + "/04-bank-statement-cust001.pdf"
            })
        );
    }

    // -----------------------------------------------------------------------
    // TEST 5 — Template mode: credit card statement (different color scheme)
    // -----------------------------------------------------------------------
    static void test05_templateMode_creditCardStatement() {
        // Build an inline data file for the credit card template
        String dataPath = OUT_DIR + "/cc-data-temp.json";
        run("05 Template mode — credit card statement", () -> {
            Files.writeString(Path.of(dataPath), "{\n" +
                "  \"bank_name\":          \"National Bank plc\",\n" +
                "  \"fca_no\":             \"123456\",\n" +
                "  \"customer_name\":      \"Robert Jones\",\n" +
                "  \"customer_address_line1\": \"17 Oak Street, Shoreditch\",\n" +
                "  \"customer_postcode\":  \"London E1 6RF\",\n" +
                "  \"card_last4\":         \"4242\",\n" +
                "  \"statement_date\":     \"28 February 2026\",\n" +
                "  \"payment_due_date\":   \"22 March 2026\",\n" +
                "  \"previous_balance\":   \"£1,234.56\",\n" +
                "  \"payments_received\":  \"£1,234.56\",\n" +
                "  \"new_charges\":        \"£892.34\",\n" +
                "  \"interest_charged\":   \"£0.00\",\n" +
                "  \"minimum_payment\":    \"£25.00\",\n" +
                "  \"apr\":               \"22.9% APR\",\n" +
                "  \"transactions\": [\n" +
                "    { \"date\": \"03 Feb\", \"merchant\": \"TESCO STORES SHOREDITCH\",   \"category\": \"Groceries\",    \"amount\": \"67.23\" },\n" +
                "    { \"date\": \"05 Feb\", \"merchant\": \"SHELL GARAGE HACKNEY\",      \"category\": \"Fuel\",         \"amount\": \"58.40\" },\n" +
                "    { \"date\": \"07 Feb\", \"merchant\": \"AMAZON MARKETPLACE\",        \"category\": \"Shopping\",     \"amount\": \"34.99\" },\n" +
                "    { \"date\": \"10 Feb\", \"merchant\": \"DELIVEROO\",                 \"category\": \"Dining\",       \"amount\": \"28.50\" },\n" +
                "    { \"date\": \"12 Feb\", \"merchant\": \"ODEON CINEMA WESTFIELD\",    \"category\": \"Entertainment\",\"amount\": \"22.50\" },\n" +
                "    { \"date\": \"14 Feb\", \"merchant\": \"GORDON RAMSAY RESTAURANT\",  \"category\": \"Dining\",       \"amount\": \"134.55\" },\n" +
                "    { \"date\": \"17 Feb\", \"merchant\": \"ZARA WESTFIELD\",            \"category\": \"Clothing\",     \"amount\": \"89.00\" },\n" +
                "    { \"date\": \"20 Feb\", \"merchant\": \"TRAINLINE\",                 \"category\": \"Travel\",       \"amount\": \"54.00\" },\n" +
                "    { \"date\": \"24 Feb\", \"merchant\": \"BOOTS PHARMACY\",            \"category\": \"Health\",       \"amount\": \"15.40\" },\n" +
                "    { \"date\": \"26 Feb\", \"merchant\": \"NETFLIX\",                   \"category\": \"Subscriptions\",\"amount\": \"17.99\" },\n" +
                "    { \"date\": \"28 Feb\", \"merchant\": \"SPOTIFY\",                   \"category\": \"Subscriptions\",\"amount\": \"10.99\" },\n" +
                "    { \"date\": \"28 Feb\", \"merchant\": \"SKY DIGITAL\",               \"category\": \"Subscriptions\",\"amount\": \"49.99\" },\n" +
                "    { \"date\": \"28 Feb\", \"merchant\": \"COUNCIL TAX HACKNEY\",       \"category\": \"Bills\",        \"amount\": \"156.00\" },\n" +
                "    { \"date\": \"28 Feb\", \"merchant\": \"THAMES WATER\",              \"category\": \"Bills\",        \"amount\": \"42.50\" },\n" +
                "    { \"date\": \"28 Feb\", \"merchant\": \"ANNUAL FEE REFUND\",         \"category\": \"Refund\",       \"amount\": \"-60.71\" }\n" +
                "  ]\n" +
                "}");
            PdfCreator.main(new String[]{
                "--template-id", "credit-card-statement",
                "--data-file",   dataPath,
                "--output",      OUT_DIR + "/05-credit-card-statement.pdf"
            });
        });
    }

    // -----------------------------------------------------------------------
    // TEST 6 — Template mode: notification letter (simple correspondence)
    //          Exercises: no table, no header logo (no logo file on disk),
    //                     placeholder substitution, notice box, footer
    // -----------------------------------------------------------------------
    static void test06_templateMode_notificationLetter() {
        run("06 Template mode — notification letter", () ->
            PdfCreator.main(new String[]{
                "--template-id", "notification-letter",
                "--data-file",   DATA_DIR + "/notification-test.json",
                "--output",      OUT_DIR + "/06-notification-letter.pdf"
            })
        );
    }

    // -----------------------------------------------------------------------
    // TEST 7 — Template mode: missing placeholder produces warning, not crash
    //          Uses CUST-002 which has all required fields — we deliberately
    //          pass a data file with one key removed via InMemoryDataSource
    // -----------------------------------------------------------------------
    static void test07_templateMode_missingPlaceholderWarning() {
        run("07 Template mode — missing placeholder (warning, not crash)", () -> {
            // Load CUST-002 data, remove one scalar key to trigger warning
            DocumentData fullData = new JsonFileDataSource(
                DATA_DIR + "/statements/CUST-002.json").load();

            // Rebuild without closing_balance
            DocumentData.Builder builder = new DocumentData.Builder();
            fullData.getScalars().forEach((k, v) -> {
                if (!k.equals("closing_balance")) builder.scalar(k, v);
            });
            fullData.getLists().forEach(builder::list);
            DocumentData trimmedData = builder.build();

            RenderPipeline pipeline = new RenderPipeline(TEMPLATE_FILE, CONFIG_FILE);
            // Expect a "Warning: Unresolved placeholders" line on stderr — not a crash
            pipeline.render("bank-statement",
                new InMemoryDataSource(trimmedData, "CUST-002 trimmed"),
                OUT_DIR + "/07-missing-placeholder.pdf");
        });
    }

    // -----------------------------------------------------------------------
    // TEST 8 — Batch mode: directory source
    //          All JSON files in data/statements/ → one PDF each
    //          4 threads (more than files, verifies pool handles that cleanly)
    // -----------------------------------------------------------------------
    static void test08_batchMode_directorySource() {
        run("08 Batch mode — directory source (bank statements)", () -> {
            RenderPipeline pipeline = new RenderPipeline(TEMPLATE_FILE, CONFIG_FILE);
            List<BatchJob> jobs = new DirectoryBatchJobFactory(
                "bank-statement",
                DATA_DIR + "/statements",
                OUT_DIR + "/batch-statements"
            ).createJobs();

            assertFalse("No batch jobs found in data/statements/", jobs.isEmpty());

            List<BatchResult> results = new BatchRunner(pipeline, 4).run(jobs);

            long failed = results.stream().filter(r -> !r.isSuccess()).count();
            assertEquals("All batch jobs should succeed — failures: " +
                results.stream().filter(r -> !r.isSuccess())
                       .map(r -> r.getJob().getReferenceId() + ": " + r.getError().getMessage())
                       .collect(Collectors.joining(", ")),
                0L, failed);

            // Verify every expected output file exists and is non-empty
            for (BatchResult r : results) {
                assertFileValid(r.getJob().getOutputPath());
            }
        });
    }

    // -----------------------------------------------------------------------
    // TEST 9 — Batch mode: CSV source
    //          3 rows in notifications.csv → 3 notification letter PDFs
    // -----------------------------------------------------------------------
    static void test09_batchMode_csvSource() {
        run("09 Batch mode — CSV source (notification letters)", () -> {
            RenderPipeline pipeline = new RenderPipeline(TEMPLATE_FILE, CONFIG_FILE);
            List<BatchJob> jobs = new CsvBatchJobFactory(
                "notification-letter",
                DATA_DIR + "/notifications.csv",
                OUT_DIR + "/batch-notifications",
                "ref_id",
                "output_file"
            ).createJobs();

            assertEquals("CSV should produce 3 jobs", 3, jobs.size());

            List<BatchResult> results = new BatchRunner(pipeline, 3).run(jobs);

            long failCount = results.stream().filter(r -> !r.isSuccess()).count();
            assertEquals("All CSV batch jobs should succeed", 0L, failCount);

            for (BatchResult r : results) {
                assertFileValid(r.getJob().getOutputPath());
            }
        });
    }

    // -----------------------------------------------------------------------
    // TEST 10 — Batch mode: single thread (serial execution path)
    // -----------------------------------------------------------------------
    static void test10_batchMode_singleThread() {
        run("10 Batch mode — single thread (serial execution)", () -> {
            RenderPipeline pipeline = new RenderPipeline(TEMPLATE_FILE, CONFIG_FILE);
            List<BatchJob> jobs = new DirectoryBatchJobFactory(
                "bank-statement",
                DATA_DIR + "/statements",
                OUT_DIR + "/batch-statements/serial"
            ).createJobs();

            List<BatchResult> results = new BatchRunner(pipeline, 1).run(jobs);

            long failCount = results.stream().filter(r -> !r.isSuccess()).count();
            assertEquals("All serial batch jobs should succeed", 0L, failCount);
        });
    }

    // -----------------------------------------------------------------------
    // TEST 11 — InMemoryDataSource: programmatic data, no file I/O
    // -----------------------------------------------------------------------
    static void test11_inMemoryDataSource() {
        run("11 InMemoryDataSource — programmatic data", () -> {
            List<Map<String, String>> txns = List.of(
                Map.of("date","01 Mar","type","SO","description","RENT PAYMENT","debit","950.00","credit","","balance","2050.00"),
                Map.of("date","03 Mar","type","BACS","description","SALARY ACME LTD","debit","","credit","3200.00","balance","5250.00"),
                Map.of("date","05 Mar","type","POS","description","WAITROSE","debit","87.34","credit","","balance","5162.66"),
                Map.of("date","07 Mar","type","ATM","description","CASH WITHDRAWAL","debit","200.00","credit","","balance","4962.66"),
                Map.of("date","10 Mar","type","DD","description","COUNCIL TAX","debit","178.00","credit","","balance","4784.66")
            );

            DocumentData data = new DocumentData.Builder()
                .scalar("bank_name",             "Test Bank plc")
                .scalar("reg_no",                "99999999")
                .scalar("fca_no",                "654321")
                .scalar("customer_name",         "Test Customer")
                .scalar("customer_address_line1","1 Test Street")
                .scalar("customer_address_line2","Testville")
                .scalar("customer_postcode",     "TE1 1ST")
                .scalar("account_no",            "00000001")
                .scalar("sort_code",             "00-00-00")
                .scalar("statement_date",        "31 March 2026")
                .scalar("period_from",           "01 March 2026")
                .scalar("period_to",             "31 March 2026")
                .scalar("opening_balance",       "£2,000.00")
                .scalar("total_credits",         "£3,200.00")
                .scalar("total_debits",          "£1,415.34")
                .scalar("closing_balance",       "£3,784.66")
                .list("transactions", txns)
                .build();

            RenderPipeline pipeline = new RenderPipeline(TEMPLATE_FILE, CONFIG_FILE);
            pipeline.render("bank-statement",
                new InMemoryDataSource(data, "programmatic test customer"),
                OUT_DIR + "/11-inmemory-datasource.pdf");
        });
    }

    // -----------------------------------------------------------------------
    // TEST 12 — Non-existent data file → IOException expected
    // -----------------------------------------------------------------------
    static void test12_missingDataFile_expectsException() {
        runExpectingException(
            "12 Missing data file — expects IOException",
            IOException.class,
            () -> {
                RenderPipeline pipeline = new RenderPipeline(TEMPLATE_FILE, CONFIG_FILE);
                pipeline.render("bank-statement",
                    new JsonFileDataSource("data/does-not-exist.json"),
                    OUT_DIR + "/12-should-not-be-created.pdf");
            }
        );
    }

    // -----------------------------------------------------------------------
    // TEST 13 — Non-existent template ID → RuntimeException expected
    // -----------------------------------------------------------------------
    static void test13_missingTemplateId_expectsException() {
        runExpectingException(
            "13 Non-existent template ID — expects RuntimeException",
            RuntimeException.class,
            () -> {
                RenderPipeline pipeline = new RenderPipeline(TEMPLATE_FILE, CONFIG_FILE);
                pipeline.render("no-such-template",
                    new JsonFileDataSource(DATA_DIR + "/statements/CUST-001.json"),
                    OUT_DIR + "/13-should-not-be-created.pdf");
            }
        );
    }

    // -----------------------------------------------------------------------
    // TEST 14 — Direct mode: title only (no body text)
    // -----------------------------------------------------------------------
    static void test14_directMode_titleOnly() {
        run("14 Direct mode — title only (minimal document)", () ->
            PdfCreator.main(new String[]{
                "--title",  "Cover Page",
                "--output", OUT_DIR + "/14-title-only.pdf"
            })
        );
    }


    // -----------------------------------------------------------------------
    // TEST 15 — Extract: full document text extraction
    //           Uses the bank statement generated in test 04 as input.
    //           Verifies: text is non-empty, page count matches, word count > 0
    // -----------------------------------------------------------------------
    static void test15_extract_fullDocument() {
        run("15 Extract — full document text", () -> {
            String pdfPath = OUT_DIR + "/04-bank-statement-cust001.pdf";
            File pdfFile = new File(pdfPath);
            if (!pdfFile.exists()) {
                // Generate source PDF first if test04 didn't run
                PdfCreator.main(new String[]{
                    "--template-id", "bank-statement",
                    "--data-file",   DATA_DIR + "/statements/CUST-001.json",
                    "--output",      pdfPath
                });
            }

            PdfTextExtractor extractor = new PdfTextExtractor();
            ExtractionResult result = extractor.extract(pdfPath);

            // Must have extracted text
            if (result.getText() == null || result.getText().isBlank())
                throw new AssertionError("Extracted text is empty");

            // Page count must match what we rendered (2 pages)
            assertEquals("Bank statement should have 2 pages", 2, result.getPageCount());

            // Word count should be substantial (statement has lots of text)
            if (result.getWordCount() < 50)
                throw new AssertionError("Word count suspiciously low: " + result.getWordCount());

            // Per-page list must have one entry per page
            assertEquals("Per-page list size should match page count",
                result.getPageCount(), result.getPageTexts().size());

            System.out.printf("      words=%d, chars=%d, pages=%d%n",
                result.getWordCount(), result.getCharCount(), result.getPageCount());
        });
    }

    // -----------------------------------------------------------------------
    // TEST 16 — Extract: page range (first page only)
    //           Verifies that startPage/endPage correctly limits extraction.
    // -----------------------------------------------------------------------
    static void test16_extract_pageRange() {
        run("16 Extract — page range (page 1 only)", () -> {
            String pdfPath = OUT_DIR + "/04-bank-statement-cust001.pdf";

            ExtractionOptions opts = new ExtractionOptions.Builder()
                .startPage(1)
                .endPage(1)
                .sortByPosition(true)
                .stripExtraWhitespace(false)
                .extractPerPage(true)
                .build();

            PdfTextExtractor extractor = new PdfTextExtractor();
            ExtractionResult result = extractor.extract(pdfPath, opts);

            // Total page count should still reflect the full document
            assertEquals("Page count should be 2 (full doc)", 2, result.getPageCount());

            // Only 1 page extracted
            assertEquals("pageTexts should have 1 entry", 1, result.getPageTexts().size());

            // Extracted text should be non-empty
            if (result.getText().isBlank())
                throw new AssertionError("Page 1 text is empty");

            System.out.printf("      page 1 words=%d%n", result.getWordCount());
        });
    }

    // -----------------------------------------------------------------------
    // TEST 17 — Extract: metadata fields
    //           Generates a PDF with explicit metadata then reads it back.
    //           Verifies that all 6 metadata fields survive the round-trip.
    // -----------------------------------------------------------------------
    static void test17_extract_metadataFields() {
        run("17 Extract — metadata round-trip", () -> {
            // Generate a PDF with known metadata via direct mode
            String metaPdf = OUT_DIR + "/17-metadata-roundtrip.pdf";
            PdfCreator.main(new String[]{
                "--title",    "Test Document Title",
                "--author",   "Test Author",
                "--subject",  "Test Subject",
                "--keywords", "test extraction metadata pdfbox",
                "--creator",  "PdfCreator Test Suite",
                "--text",     "Body text for metadata round-trip test.",
                "--output",   metaPdf
            });
            assertFileValid(metaPdf);

            // Now extract metadata back out
            ExtractionOptions opts = new ExtractionOptions.Builder()
                .includeMetadata(true)
                .extractPerPage(false)   // metadata only
                .build();

            PdfTextExtractor extractor = new PdfTextExtractor();
            ExtractionResult result = extractor.extract(metaPdf, opts);

            java.util.Map<String, String> meta = result.getMetadata();

            // Each field we set must be present and correct
            assertMetadataField(meta, "title",    "Test Document Title");
            assertMetadataField(meta, "author",   "Test Author");
            assertMetadataField(meta, "subject",  "Test Subject");
            assertMetadataField(meta, "keywords", "test extraction metadata pdfbox");
            assertMetadataField(meta, "creator",  "PdfCreator Test Suite");
            assertMetadataField(meta, "producer", "PdfCreator / Apache PDFBox 3");

            System.out.printf("      metadata fields extracted: %d%n", meta.size());
        });
    }

    // -----------------------------------------------------------------------
    // TEST 18 — Extract: write to output file
    //           Verifies that extractToFile() produces a non-empty .txt file
    //           containing the expected metadata header and page sections.
    // -----------------------------------------------------------------------
    static void test18_extract_toFile() {
        run("18 Extract — write to .txt file", () -> {
            String pdfPath  = OUT_DIR + "/04-bank-statement-cust001.pdf";
            String textPath = OUT_DIR + "/18-extracted-text.txt";

            ExtractionOptions opts = new ExtractionOptions.Builder()
                .sortByPosition(true)
                .stripExtraWhitespace(true)
                .includeMetadata(true)
                .extractPerPage(true)
                .build();

            PdfTextExtractor extractor = new PdfTextExtractor();
            ExtractionResult result = extractor.extractToFile(pdfPath, textPath, opts);

            // Output file must exist and have content
            assertFileValid(textPath);

            // Read it back and sanity-check contents
            String fileContent = java.nio.file.Files.readString(java.nio.file.Path.of(textPath));

            if (fileContent.isBlank())
                throw new AssertionError("Extracted text file is blank");

            // Should contain the metadata header section marker
            if (!fileContent.contains("DOCUMENT PROPERTIES"))
                throw new AssertionError("Output file missing DOCUMENT PROPERTIES header");

            // Should contain page separator markers
            if (!fileContent.contains("--- Page "))
                throw new AssertionError("Output file missing page separator markers");

            System.out.printf("      output file size: %d bytes%n",
                new java.io.File(textPath).length());
        });
    }


    // -----------------------------------------------------------------------
    // TEST 19 — Image extraction: extract images from a generated PDF
    //           Uses the bank statement from test 04 (contains a logo image).
    //           Verifies: list is non-null, each image has valid dimensions and data.
    // -----------------------------------------------------------------------
    static void test19_extractImages_fromGeneratedPdf() {
        run("19 Extract Images — from generated PDF", () -> {
            String pdfPath = OUT_DIR + "/04-bank-statement-cust001.pdf";
            java.io.File pdfFile = new java.io.File(pdfPath);
            if (!pdfFile.exists()) {
                PdfCreator.main(new String[]{
                    "--template-id", "bank-statement",
                    "--data-file",   DATA_DIR + "/statements/CUST-001.json",
                    "--output",      pdfPath
                });
            }

            com.pdfcreator.extractor.PdfImageExtractor extractor =
                new com.pdfcreator.extractor.PdfImageExtractor();
            java.util.List<com.pdfcreator.extractor.ExtractedImage> images =
                extractor.extract(pdfPath);

            // Result must be a non-null list (may be empty if no images above threshold)
            if (images == null)
                throw new AssertionError("extract() returned null");

            System.out.printf("      images found: %d%n", images.size());

            // Validate each extracted image
            for (com.pdfcreator.extractor.ExtractedImage img : images) {
                if (img.getWidth()  <= 0) throw new AssertionError("Image width <= 0: " + img);
                if (img.getHeight() <= 0) throw new AssertionError("Image height <= 0: " + img);
                if (img.getData()   == null || img.getData().length == 0)
                    throw new AssertionError("Image data is empty: " + img);
                if (img.getSuggestedName() == null || img.getSuggestedName().isBlank())
                    throw new AssertionError("Image has no suggested name: " + img);
                if (img.getPageNumber() < 1)
                    throw new AssertionError("Invalid page number: " + img);
                System.out.printf("        %s%n", img);
            }
        });
    }

    // -----------------------------------------------------------------------
    // TEST 20 — Image extraction: minimum size filter
    //           Sets a large minimum size so no images pass the filter.
    //           Verifies: empty list returned, no exception thrown.
    // -----------------------------------------------------------------------
    static void test20_extractImages_minSizeFilter() {
        run("20 Extract Images — min size filter (expect 0 results)", () -> {
            String pdfPath = OUT_DIR + "/04-bank-statement-cust001.pdf";

            com.pdfcreator.extractor.ImageExtractionOptions opts =
                new com.pdfcreator.extractor.ImageExtractionOptions.Builder()
                    .minWidth(99999)    // unreachably large — all images filtered out
                    .minHeight(99999)
                    .build();

            com.pdfcreator.extractor.PdfImageExtractor extractor =
                new com.pdfcreator.extractor.PdfImageExtractor();
            java.util.List<com.pdfcreator.extractor.ExtractedImage> images =
                extractor.extract(pdfPath, opts);

            assertEquals("All images should be filtered by min size", 0, images.size());
            System.out.printf("      correctly returned 0 images with 99999px filter%n");
        });
    }

    // -----------------------------------------------------------------------
    // TEST 21 — Image extraction: extractToDirectory saves files to disk
    //           Verifies: output directory created, files written,
    //           file sizes match ExtractedImage.getSizeBytes().
    // -----------------------------------------------------------------------
    static void test21_extractImages_toDirectory() {
        run("21 Extract Images — save to directory", () -> {
            String pdfPath  = OUT_DIR + "/04-bank-statement-cust001.pdf";
            String imageDir = OUT_DIR + "/21-extracted-images/";

            com.pdfcreator.extractor.ImageExtractionOptions opts =
                new com.pdfcreator.extractor.ImageExtractionOptions.Builder()
                    .minWidth(1).minHeight(1)   // accept everything
                    .preferredFormat("png")
                    .build();

            com.pdfcreator.extractor.PdfImageExtractor extractor =
                new com.pdfcreator.extractor.PdfImageExtractor();
            java.util.List<com.pdfcreator.extractor.ExtractedImage> images =
                extractor.extractToDirectory(pdfPath, imageDir, opts);

            // Directory must exist
            java.io.File dir = new java.io.File(imageDir);
            if (!dir.exists() || !dir.isDirectory())
                throw new AssertionError("Output directory was not created: " + imageDir);

            // Each image must have a corresponding file on disk with correct size
            for (com.pdfcreator.extractor.ExtractedImage img : images) {
                java.io.File f = new java.io.File(imageDir + img.getSuggestedName());
                if (!f.exists())
                    throw new AssertionError("Expected file not found: " + f.getPath());
                if (f.length() != img.getSizeBytes())
                    throw new AssertionError(String.format(
                        "File size mismatch for %s: expected %d, got %d",
                        f.getName(), img.getSizeBytes(), f.length()));
            }

            System.out.printf("      %d image(s) saved to %s%n", images.size(), imageDir);
        });
    }


    // -----------------------------------------------------------------------
    // TEST 22 — Password-protected PDF: successful extraction with correct password
    //
    //           Generates a password-protected PDF using PDFBox encryption API,
    //           then extracts text from it using the correct password.
    //           Verifies: extracted text is non-empty and contains expected content.
    // -----------------------------------------------------------------------
    static void test22_extract_passwordProtected() {
        run("22 Extract — password-protected PDF (correct password)", () -> {
            String protectedPdf = OUT_DIR + "/22-password-protected.pdf";
            String testPassword = "test1234";
            String secretText   = "This is secret content inside a protected PDF.";

            // ---- Step 1: Generate a password-protected PDF ----
            // We use PDFBox directly to create and encrypt a PDF with a known password.
            org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument();
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);

            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                    new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                    org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(secretText);
                cs.endText();
            }

            // Apply Standard 128-bit encryption
            org.apache.pdfbox.pdmodel.encryption.AccessPermission perms =
                new org.apache.pdfbox.pdmodel.encryption.AccessPermission();
            org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy policy =
                new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy(
                    testPassword,   // owner password
                    testPassword,   // user password  (same for simplicity)
                    perms);
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            doc.save(protectedPdf);
            doc.close();

            assertFileValid(protectedPdf);

            // ---- Step 2: Extract text with correct password ----
            com.pdfcreator.extractor.ExtractionOptions opts =
                new com.pdfcreator.extractor.ExtractionOptions.Builder()
                    .password(testPassword)
                    .build();

            com.pdfcreator.extractor.PdfTextExtractor extractor =
                new com.pdfcreator.extractor.PdfTextExtractor();
            com.pdfcreator.extractor.ExtractionResult result =
                extractor.extract(protectedPdf, opts);

            // Extracted text must contain the secret content
            if (!result.getText().contains("secret content"))
                throw new AssertionError(
                    "Expected secret content in extracted text, got: " + result.getText());

            System.out.printf("      extracted %d words from password-protected PDF%n",
                result.getWordCount());
        });
    }

    // -----------------------------------------------------------------------
    // TEST 23 — Password-protected PDF: PasswordRequiredException on wrong/no password
    //
    //           Uses the protected PDF from test 22. Attempts extraction with:
    //           (a) no password — expects PasswordRequiredException, passwordProvided=false
    //           (b) wrong password — expects PasswordRequiredException, passwordProvided=true
    // -----------------------------------------------------------------------
    static void test23_extract_wrongPassword() {
        run("23 Extract — password-protected PDF (wrong/no password)", () -> {
            String protectedPdf = OUT_DIR + "/22-password-protected.pdf";

            // Ensure the protected PDF exists (run test22 first if needed)
            if (!new java.io.File(protectedPdf).exists()) {
                test22_extract_passwordProtected();
            }

            com.pdfcreator.extractor.PdfTextExtractor extractor =
                new com.pdfcreator.extractor.PdfTextExtractor();

            // ---- Part A: No password supplied ----
            boolean caughtNoPassword = false;
            try {
                extractor.extract(protectedPdf);   // no password
            } catch (com.pdfcreator.extractor.PasswordRequiredException e) {
                caughtNoPassword = true;
                if (e.wasPasswordProvided())
                    throw new AssertionError(
                        "wasPasswordProvided() should be false when no password given");
                System.out.printf("      Part A OK — no password: %s%n", e.getMessage());
            }
            if (!caughtNoPassword)
                throw new AssertionError(
                    "Expected PasswordRequiredException when no password supplied");

            // ---- Part B: Wrong password supplied ----
            boolean caughtWrongPassword = false;
            try {
                extractor.extract(protectedPdf, "wrongpassword");
            } catch (com.pdfcreator.extractor.PasswordRequiredException e) {
                caughtWrongPassword = true;
                if (!e.wasPasswordProvided())
                    throw new AssertionError(
                        "wasPasswordProvided() should be true when wrong password given");
                System.out.printf("      Part B OK — wrong password: %s%n", e.getMessage());
            }
            if (!caughtWrongPassword)
                throw new AssertionError(
                    "Expected PasswordRequiredException when wrong password supplied");
        });
    }


    // -----------------------------------------------------------------------
    // TEST 24 — Merge: combine three generated PDFs into one
    //           Generates 3 single-page PDFs, merges them, verifies the output
    //           has 3 pages and the file exists.
    // -----------------------------------------------------------------------
    static void test24_merge_basicThreeFiles() {
        run("24 Merge — three PDFs into one", () -> {
            // Generate 3 source PDFs using direct mode
            String[] sources = {
                OUT_DIR + "/24-source-a.pdf",
                OUT_DIR + "/24-source-b.pdf",
                OUT_DIR + "/24-source-c.pdf"
            };
            String[] texts = { "Document A content.", "Document B content.", "Document C content." };

            for (int i = 0; i < sources.length; i++) {
                PdfCreator.main(new String[]{
                    "--title",  "Source " + (char)('A' + i),
                    "--text",   texts[i],
                    "--output", sources[i]
                });
                assertFileValid(sources[i]);
            }

            String mergedPath = OUT_DIR + "/24-merged.pdf";
            MergeOptions opts = new MergeOptions.Builder()
                .addInput(sources[0])
                .addInput(sources[1])
                .addInput(sources[2])
                .output(mergedPath)
                .copyMetadataFromFirst(true)
                .build();

            new PdfManipulator().merge(opts);
            assertFileValid(mergedPath);

            // Verify page count = 3
            try (org.apache.pdfbox.pdmodel.PDDocument doc =
                    org.apache.pdfbox.Loader.loadPDF(new java.io.File(mergedPath))) {
                assertEquals("Merged PDF should have 3 pages", 3, doc.getNumberOfPages());
            }

            System.out.printf("      merged 3 files -> %s%n", mergedPath);
        });
    }

    // -----------------------------------------------------------------------
    // TEST 25 — Merge: one plain + one password-protected input
    //           Uses the protected PDF from test 22. Merges it with a plain PDF.
    //           Verifies: output exists, page count = 2, no exception thrown.
    // -----------------------------------------------------------------------
    static void test25_merge_withPasswordProtected() {
        run("25 Merge — plain + password-protected input", () -> {
            String plainPdf     = OUT_DIR + "/24-source-a.pdf";
            String protectedPdf = OUT_DIR + "/22-password-protected.pdf";

            // Ensure both source files exist
            if (!new java.io.File(plainPdf).exists())
                test24_merge_basicThreeFiles();
            if (!new java.io.File(protectedPdf).exists())
                test22_extract_passwordProtected();

            String mergedPath = OUT_DIR + "/25-merged-with-protected.pdf";
            MergeOptions opts = new MergeOptions.Builder()
                .addInput(plainPdf)
                .addInput(protectedPdf, "test1234")   // correct password
                .output(mergedPath)
                .build();

            new PdfManipulator().merge(opts);
            assertFileValid(mergedPath);

            try (org.apache.pdfbox.pdmodel.PDDocument doc =
                    org.apache.pdfbox.Loader.loadPDF(new java.io.File(mergedPath))) {
                assertEquals("Merged PDF should have 2 pages", 2, doc.getNumberOfPages());
            }

            System.out.printf("      merged plain + protected -> %s%n", mergedPath);
        });
    }

    // -----------------------------------------------------------------------
    // TEST 26 — Split: every N pages
    //           Generates a 6-page PDF, splits every 2 pages, verifies 3 output
    //           files each containing exactly 2 pages.
    // -----------------------------------------------------------------------
    static void test26_split_everyNPages() {
        run("26 Split — every 2 pages (6-page source)", () -> {
            // Build a 6-page source by merging 6 single-page PDFs
            String[] pages = new String[6];
            for (int i = 0; i < 6; i++) {
                pages[i] = OUT_DIR + "/26-page" + (i + 1) + ".pdf";
                PdfCreator.main(new String[]{
                    "--text",   "Page " + (i + 1) + " of the split test document.",
                    "--output", pages[i]
                });
            }

            MergeOptions mergeOpts = new MergeOptions.Builder()
                .addInput(pages[0]).addInput(pages[1]).addInput(pages[2])
                .addInput(pages[3]).addInput(pages[4]).addInput(pages[5])
                .output(OUT_DIR + "/26-source-6pages.pdf")
                .build();
            new PdfManipulator().merge(mergeOpts);

            String splitDir = OUT_DIR + "/26-split-output/";
            SplitOptions splitOpts = new SplitOptions.Builder(
                    OUT_DIR + "/26-source-6pages.pdf", splitDir)
                .everyNPages(2)
                .build();

            SplitResult result = new PdfManipulator().split(splitOpts);

            assertEquals("Should produce 3 output files", 3, result.getOutputCount());
            for (int i = 0; i < result.getOutputCount(); i++) {
                assertFileValid(result.getOutputPaths().get(i));
                assertEquals("Each part should have 2 pages", 2,
                    (int) result.getPageCounts().get(i));
            }

            System.out.printf("      split into %d files%n", result.getOutputCount());
        });
    }

    // -----------------------------------------------------------------------
    // TEST 27 — Split: into N parts + page-range strategy
    //           Uses the 6-page source from test 26. Tests both INTO_N_PARTS
    //           (3 parts → 2 pages each) and BY_PAGE_RANGE (explicit boundaries).
    // -----------------------------------------------------------------------
    static void test27_split_intoParts() {
        run("27 Split — into 3 parts + explicit page ranges", () -> {
            String sourcePdf = OUT_DIR + "/26-source-6pages.pdf";
            if (!new java.io.File(sourcePdf).exists())
                test26_split_everyNPages();

            // ---- Part A: INTO_N_PARTS ----
            SplitOptions partsOpts = new SplitOptions.Builder(sourcePdf, OUT_DIR + "/27-parts/")
                .intoParts(3)
                .build();

            SplitResult partsResult = new PdfManipulator().split(partsOpts);
            assertEquals("Should produce 3 parts", 3, partsResult.getOutputCount());
            assertEquals("Total pages should be 6", 6, partsResult.getTotalPagesProcessed());
            for (String outPath : partsResult.getOutputPaths())
                assertFileValid(outPath);
            System.out.printf("      intoParts(3): %d files%n", partsResult.getOutputCount());

            // ---- Part B: BY_PAGE_RANGE (explicit) ----
            SplitOptions rangeOpts = new SplitOptions.Builder(sourcePdf, OUT_DIR + "/27-ranges/")
                .byPageRanges(new int[][]{ {1, 2}, {3, 4}, {5, 6} })
                .build();

            SplitResult rangeResult = new PdfManipulator().split(rangeOpts);
            assertEquals("Should produce 3 range files", 3, rangeResult.getOutputCount());
            for (int i = 0; i < rangeResult.getOutputCount(); i++) {
                assertFileValid(rangeResult.getOutputPaths().get(i));
                assertEquals("Each range should have 2 pages", 2,
                    (int) rangeResult.getPageCounts().get(i));
            }
            System.out.printf("      byPageRanges: %d files%n", rangeResult.getOutputCount());
        });
    }

    // -----------------------------------------------------------------------
    // Test runner helpers
    // -----------------------------------------------------------------------

    @FunctionalInterface
    interface TestBlock { void run() throws Exception; }

    static void run(String name, TestBlock block) {
        System.out.printf("%n── %s%n", name);
        try {
            block.run();

            // Verify the expected output file (if the test name implies one)
            String outFile = OUT_DIR + "/" + name.split(" ")[0].replace("──","").trim()
                .replaceAll("[^0-9]", "")  // extract the number
                ;
            // File existence check is done explicitly inside tests where needed.
            // For simple single-output tests we check the numbered output file.
            String number = name.split(" ")[0].trim();
            if (number.matches("\\d+")) {
                String pattern = OUT_DIR + "/" + String.format("%02d", Integer.parseInt(number));
                // Find the file starting with this prefix
                File dir = new File(OUT_DIR);
                File[] matches = dir.listFiles(f ->
                    f.getName().startsWith(String.format("%02d", Integer.parseInt(number))) &&
                    f.getName().endsWith(".pdf"));
                if (matches != null && matches.length > 0) {
                    assertFileValid(matches[0].getPath());
                }
            }

            System.out.printf("   ✓ PASSED%n");
            passed++;
        } catch (AssertionError | Exception e) {
            System.out.printf("   ✗ FAILED: %s%n", e.getMessage());
            failures.add(name + ": " + e.getMessage());
            failed++;
        }
    }

    static void runExpectingException(String name, Class<? extends Exception> expectedType,
                                       TestBlock block) {
        System.out.printf("%n── %s%n", name);
        try {
            block.run();
            // If we reach here, no exception was thrown — test fails
            System.out.printf("   ✗ FAILED: Expected %s but no exception was thrown%n",
                expectedType.getSimpleName());
            failures.add(name + ": expected " + expectedType.getSimpleName() + " not thrown");
            failed++;
        } catch (Exception e) {
            if (expectedType.isInstance(e) || (e.getCause() != null && expectedType.isInstance(e.getCause()))) {
                System.out.printf("   ✓ PASSED (caught expected %s: %s)%n",
                    e.getClass().getSimpleName(), e.getMessage());
                passed++;
            } else {
                System.out.printf("   ✗ FAILED: Expected %s but got %s: %s%n",
                    expectedType.getSimpleName(), e.getClass().getSimpleName(), e.getMessage());
                failures.add(name + ": wrong exception type " + e.getClass().getSimpleName());
                failed++;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Assertion helpers
    // -----------------------------------------------------------------------

    static void assertFileValid(String path) {
        File f = new File(path);
        if (!f.exists())
            throw new AssertionError("Output file does not exist: " + path);
        if (f.length() < 100)
            throw new AssertionError("Output file suspiciously small (" + f.length() + " bytes): " + path);
    }

    static void assertEquals(String message, Object expected, Object actual) {
        if (!Objects.equals(expected, actual))
            throw new AssertionError(message + " — expected: " + expected + ", got: " + actual);
    }

    static void assertFalse(String message, boolean condition) {
        if (condition) throw new AssertionError(message);
    }

    static void assertMetadataField(java.util.Map<String, String> meta,
                                    String key, String expected) {
        String actual = meta.get(key);
        if (actual == null)
            throw new AssertionError("Metadata field missing: " + key);
        if (!actual.equals(expected))
            throw new AssertionError("Metadata field '" + key + "' expected: "
                + expected + " but got: " + actual);
    }
}
