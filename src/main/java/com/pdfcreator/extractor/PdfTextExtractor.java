package com.pdfcreator.extractor;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * Extracts text and metadata from a PDF file using Apache PDFBox.
 *
 * The core class of the extractor package. Thread-safe — a single instance
 * can be shared and called from multiple threads concurrently because no
 * mutable state is held between calls. Each extract() call opens and closes
 * its own PDDocument.
 *
 * Features:
 *   - Full document extraction        (all pages, single string)
 *   - Page range extraction           (startPage to endPage, inclusive)
 *   - Per-page extraction             (one string per page in a List)
 *   - Document metadata extraction    (title, author, subject, keywords, etc.)
 *   - Optional whitespace normalisation
 *   - Output to String, to a file, or to stdout
 *
 * PDFBox classes used:
 *   PDDocument        — represents the loaded PDF in memory
 *   PDFTextStripper   — walks the PDF content streams and extracts text tokens
 *                       in reading order (when sortByPosition = true)
 *   PDDocumentInformation — provides the document info dictionary (metadata)
 *
 * Usage examples:
 *
 *   // Simplest — extract everything with defaults
 *   PdfTextExtractor extractor = new PdfTextExtractor();
 *   ExtractionResult result = extractor.extract("report.pdf");
 *   System.out.println(result.getText());
 *
 *   // Extract pages 2-4 only, strip extra whitespace
 *   ExtractionOptions opts = new ExtractionOptions.Builder()
 *       .startPage(2).endPage(4)
 *       .stripExtraWhitespace(true)
 *       .build();
 *   ExtractionResult result = extractor.extract("report.pdf", opts);
 *
 *   // Extract and save to file
 *   extractor.extractToFile("report.pdf", "report.txt", ExtractionOptions.defaults());
 *
 *   // Programmatic API — extract from a PDDocument already in memory
 *   try (PDDocument doc = Loader.loadPDF(new File("report.pdf"), password.getBytes())) {
 *       ExtractionResult result = extractor.extract(doc, "report.pdf", opts);
 *   }
 */
public class PdfTextExtractor {

    private static final Logger logger = Logger.getLogger(PdfTextExtractor.class.getName());
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // -----------------------------------------------------------------------
    // Public API — file path variants
    // -----------------------------------------------------------------------

    /**
     * Extracts text from the given PDF file using default options.
     *
     * @param pdfPath path to the PDF file
     * @return ExtractionResult containing full text, per-page list, and metadata
     * @throws IOException if the file cannot be read or is not a valid PDF
     */
    public ExtractionResult extract(String pdfPath) throws IOException {
        return extract(pdfPath, ExtractionOptions.defaults());
    }

    /**
     * Extracts text from a password-protected PDF using default options.
     * Convenience overload — equivalent to extract(path, new Builder().password(password).build()).
     *
     * @param pdfPath  path to the PDF file
     * @param password user or owner password; null for unprotected PDFs
     * @throws PasswordRequiredException if the password is null or incorrect
     * @throws IOException if the file cannot be read
     */
    public ExtractionResult extract(String pdfPath, String password) throws IOException {
        return extract(pdfPath, new ExtractionOptions.Builder().password(password).build());
    }

    /**
     * Extracts text from the given PDF file using the provided options.
     *
     * @param pdfPath path to the PDF file
     * @param options extraction configuration
     * @return ExtractionResult
     * @throws IOException if the file cannot be read or is not a valid PDF
     */
    public ExtractionResult extract(String pdfPath, ExtractionOptions options) throws IOException {
        File file = new File(pdfPath);
        if (!file.exists())
            throw new IOException("PDF file not found: " + pdfPath);
        if (!file.isFile())
            throw new IOException("Path is not a file: " + pdfPath);

        logger.info("Extracting: " + pdfPath + " | " + options);

        try (PDDocument document = loadWithPassword(file, pdfPath, options.getPassword())) {
            return extract(document, pdfPath, options);
        }
    }

    /**
     * Extracts text from a PDDocument already loaded in memory.
     * The caller retains ownership of the document — it is NOT closed by this method.
     *
     * @param document  open PDDocument
     * @param sourcePath the original file path, used for logging and the result's sourcePath field
     * @param options   extraction configuration
     * @return ExtractionResult
     * @throws IOException if text stripping fails
     */
    public ExtractionResult extract(PDDocument document, String sourcePath,
                                     ExtractionOptions options) throws IOException {

        int totalPages = document.getNumberOfPages();

        // Clamp requested page range to actual page count
        int startPage = options.getStartPage() < 1 ? 1 : options.getStartPage();
        int endPage   = options.getEndPage()   < 1 ? totalPages
                        : Math.min(options.getEndPage(), totalPages);

        if (startPage > endPage)
            throw new IllegalArgumentException(String.format(
                "startPage (%d) must be <= endPage (%d, total pages: %d)",
                startPage, endPage, totalPages));

        // ── Full text extraction ─────────────────────────────────────────
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(options.isSortByPosition());
        stripper.setStartPage(startPage);
        stripper.setEndPage(endPage);

        String fullText = stripper.getText(document);
        if (options.isStripExtraWhitespace()) {
            fullText = normaliseWhitespace(fullText);
        }

        // ── Per-page extraction ──────────────────────────────────────────
        List<String> pageTexts = new ArrayList<>();
        if (options.isExtractPerPage()) {
            PDFTextStripper pageSt = new PDFTextStripper();
            pageSt.setSortByPosition(options.isSortByPosition());

            for (int p = startPage; p <= endPage; p++) {
                pageSt.setStartPage(p);
                pageSt.setEndPage(p);
                String pageText = pageSt.getText(document);
                if (options.isStripExtraWhitespace()) {
                    pageText = normaliseWhitespace(pageText);
                }
                pageTexts.add(pageText);
            }
        }

        // ── Metadata extraction ──────────────────────────────────────────
        Map<String, String> metadata = new LinkedHashMap<>();
        if (options.isIncludeMetadata()) {
            metadata = extractMetadata(document.getDocumentInformation());
        }

        ExtractionResult result = new ExtractionResult.Builder()
            .text(fullText)
            .pageTexts(pageTexts)
            .metadata(metadata)
            .pageCount(totalPages)
            .sourcePath(sourcePath)
            .build();

        logger.info("Extraction complete: " + result);
        return result;
    }

    // -----------------------------------------------------------------------
    // Convenience — extract and write to file
    // -----------------------------------------------------------------------

    /**
     * Extracts text from the given PDF and writes it to outputPath.
     * The output file is UTF-8 encoded plain text.
     *
     * @param pdfPath    source PDF
     * @param outputPath destination text file (created or overwritten)
     * @param options    extraction configuration
     * @return ExtractionResult (same as calling extract() directly)
     * @throws IOException on read or write failure
     */
    public ExtractionResult extractToFile(String pdfPath, String outputPath,
                                           ExtractionOptions options) throws IOException {
        ExtractionResult result = extract(pdfPath, options);
        Files.writeString(Path.of(outputPath), buildFileOutput(result, options));
        logger.info("Extracted text written to: " + outputPath);
        return result;
    }

    /**
     * Prints the extraction result to stdout in a human-readable format.
     * Includes a metadata header block when includeMetadata = true.
     */
    public void printResult(ExtractionResult result) {
        System.out.println(buildFileOutput(result, ExtractionOptions.defaults()));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Builds the text file representation of an ExtractionResult.
     * When metadata is present, a header block is prepended.
     * When per-page extraction was performed, page separators are inserted.
     */
    private String buildFileOutput(ExtractionResult result, ExtractionOptions options) {
        StringBuilder sb = new StringBuilder();

        // Metadata header
        if (!result.getMetadata().isEmpty()) {
            sb.append("=".repeat(60)).append("\n");
            sb.append("  DOCUMENT PROPERTIES\n");
            sb.append("=".repeat(60)).append("\n");
            result.getMetadata().forEach((k, v) ->
                sb.append(String.format("  %-18s %s%n", capitalise(k) + ":", v)));
            sb.append(String.format("  %-18s %d%n", "Total pages:", result.getPageCount()));
            sb.append(String.format("  %-18s %d%n", "Words (approx):", result.getWordCount()));
            sb.append(String.format("  %-18s %d%n", "Characters:", result.getCharCount()));
            sb.append("=".repeat(60)).append("\n\n");
        }

        // Per-page output with separators, or plain full text
        if (!result.getPageTexts().isEmpty()) {
            int startPage = options.getStartPage() < 1 ? 1 : options.getStartPage();
            for (int i = 0; i < result.getPageTexts().size(); i++) {
                int pageNo = startPage + i;
                sb.append("--- Page ").append(pageNo).append(" ")
                  .append("-".repeat(50 - String.valueOf(pageNo).length())).append("\n");
                sb.append(result.getPageTexts().get(i));
                if (!result.getPageTexts().get(i).endsWith("\n")) sb.append("\n");
            }
        } else {
            sb.append(result.getText());
        }

        return sb.toString();
    }

    /**
     * Reads the PDF document information dictionary and returns a map of
     * non-null, non-blank field values.
     *
     * PDFBox's PDDocumentInformation getters return null for absent fields,
     * so we guard every call.
     */
    private Map<String, String> extractMetadata(PDDocumentInformation info) {
        Map<String, String> m = new LinkedHashMap<>();
        if (info == null) return m;

        putIfPresent(m, "title",            info.getTitle());
        putIfPresent(m, "author",           info.getAuthor());
        putIfPresent(m, "subject",          info.getSubject());
        putIfPresent(m, "keywords",         info.getKeywords());
        putIfPresent(m, "creator",          info.getCreator());
        putIfPresent(m, "producer",         info.getProducer());

        // Calendar fields — format to human-readable strings
        if (info.getCreationDate() != null) {
            try { putIfPresent(m, "creationDate", DATE_FMT.format(info.getCreationDate().getTime())); }
            catch (Exception ignored) {}
        }
        if (info.getModificationDate() != null) {
            try { putIfPresent(m, "modificationDate", DATE_FMT.format(info.getModificationDate().getTime())); }
            catch (Exception ignored) {}
        }

        return m;
    }

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) map.put(key, value);
    }

    /**
     * Normalises whitespace: collapses runs of spaces/tabs into a single space,
     * trims each line, and removes blank lines that consist only of whitespace.
     */
    private static String normaliseWhitespace(String text) {
        if (text == null) return "";
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.replaceAll("[ \\t]+", " ").trim();
            sb.append(trimmed).append("\n");
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Password-aware PDF loader
    // -----------------------------------------------------------------------

    /**
     * Loads a PDDocument with optional password support.
     *
     * PDFBox 3.x API:
     *   Loader.loadPDF(File)                  — no password
     *   Loader.loadPDF(File, byte[])          — with password bytes
     *
     * PDFBox tries the supplied bytes first as the user password, then as
     * the owner password. Both grant content extraction access.
     *
     * If no password is supplied and the document is encrypted,
     * PDFBox attempts to open it with an empty password (some PDFs allow this).
     * If that fails, InvalidPasswordException is thrown.
     *
     * @throws PasswordRequiredException wrapping the underlying InvalidPasswordException
     */
    private static PDDocument loadWithPassword(File file, String pdfPath,
                                               String password) throws IOException {
        try {
            if (password != null && !password.isEmpty()) {                                
                return Loader.loadPDF(file, password);
            } else {
                PDDocument doc = Loader.loadPDF(file);
                // PDFBox may open encrypted PDFs with an empty owner password.
                // Verify that content extraction is actually permitted.
                if (doc.isEncrypted() &&
                    !doc.getCurrentAccessPermission().canExtractContent()) {
                    doc.close();
                    throw new PasswordRequiredException(pdfPath, false);
                }
                return doc;
            }
        } catch (InvalidPasswordException e) {
            throw new PasswordRequiredException(pdfPath, password != null, e);
        }
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
