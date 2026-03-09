package com.pdfcreator.validator;

import org.apache.pdfbox.preflight.Format;
import org.apache.pdfbox.preflight.PreflightDocument;
import org.apache.pdfbox.preflight.ValidationResult;
import org.apache.pdfbox.preflight.ValidationResult.ValidationError;
import org.apache.pdfbox.preflight.exception.SyntaxValidationException;
import org.apache.pdfbox.preflight.parser.PreflightParser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Validates PDF files against the PDF/A-1 standard using PDFBox Preflight.
 *
 * ── PACKAGE LOCATIONS (PDFBox 3.x) ────────────────────────────────────────
 *
 *   org.apache.pdfbox.preflight.parser.PreflightParser   — the parser
 *   org.apache.pdfbox.preflight.PreflightDocument        — the parsed doc
 *   org.apache.pdfbox.preflight.ValidationResult         — result container
 *   org.apache.pdfbox.preflight.ValidationResult.ValidationError — one error
 *   org.apache.pdfbox.preflight.exception.SyntaxValidationException
 *   org.apache.pdfbox.preflight.Format                   — PDF_A1B / PDF_A1A
 *
 * ── HOW PREFLIGHT WORKS ───────────────────────────────────────────────────
 *
 *   1. SYNTAX VALIDATION — parser.parse(format) does stricter parsing than
 *      Loader.loadPDF(). Throws SyntaxValidationException if the file is too
 *      malformed to parse. That exception carries a ValidationResult with the
 *      syntax errors collected before the parser gave up.
 *
 *   2. SEMANTIC VALIDATION — preflightDoc.validate() checks all PDF/A rules:
 *      font embedding, XMP metadata, output intent, encryption, actions, etc.
 *
 * ── PDFBox 3.x SPECIFICS ──────────────────────────────────────────────────
 *
 *   - validate() RETURNS ValidationResult in 3.x (was void in 2.x).
 *     Capture the return value: `ValidationResult r = preflightDoc.validate()`
 *     Do NOT call getResult() after validate() — that method does not exist in 3.x.
 *   - PreflightDocument implements AutoCloseable — always use try-with-resources.
 *   - ValidationError.getPageNumber() returns Integer (nullable), not int.
 *     A null or -1 value means the error is not associated with a specific page.
 *   - PreflightParser accepts File, String, or RandomAccessRead. The DataSource
 *     constructor was removed in 3.0 (restored in 3.0.4, but File is preferred).
 *
 * ── THREAD SAFETY ─────────────────────────────────────────────────────────
 *
 *   Stateless — a single PdfValidator instance can be shared across threads.
 *   Each validate() call creates its own PreflightParser and PreflightDocument.
 */
public class PdfValidator {

    private static final Logger logger = Logger.getLogger(PdfValidator.class.getName());

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Validates the PDF file described by opts against the configured PDF/A standard.
     *
     * @param opts validation configuration
     * @return PdfValidationResult with full issue list and formatted summary
     * @throws IOException if the file cannot be read
     */
    public PdfValidationResult validate(ValidationOptions opts) throws IOException {
        logger.info("Validating: " + opts);
        long start = System.currentTimeMillis();

        File file = requireFile(opts.getInputPath());
        List<ValidationIssue> issues   = new ArrayList<>();
        boolean               valid    = false;
        boolean               truncated = false;
        int                   totalPages = 0;

        Format format = opts.getStandard() == PdfAStandard.PDF_A1A
            ? Format.PDF_A1A : Format.PDF_A1B;

        try {
            PreflightParser parser = new PreflightParser(file);

            // parse(Format) performs syntax validation and returns the PreflightDocument
            // as PDDocument (via createDocument() override internally).
            // preflightDocument is a private field — there is no public getter.
            // The only way to obtain it is to cast the return value of parse().
            // SyntaxValidationException is thrown here if the file is too malformed.
            try (PreflightDocument preflightDoc = (PreflightDocument) parser.parse(format)) {

                try {
                    totalPages = preflightDoc.getNumberOfPages();
                } catch (Exception ignored) {}

                // PDFBox 3.x: validate() RETURNS ValidationResult directly (was void in 2.x).
                // Do NOT call getResult() after validate() — that method does not exist in 3.x.
                ValidationResult result = preflightDoc.validate();
                valid = result.isValid();

                for (ValidationError err : result.getErrorsList()) {
                    issues.add(toIssue(err));
                    if (opts.getMaxErrors() > 0 && issues.size() >= opts.getMaxErrors()) {
                        truncated = true;
                        break;
                    }
                }
            }

        } catch (SyntaxValidationException e) {
            // PDF too malformed to parse — collect whatever errors Preflight recorded
            // before giving up. The document is definitively invalid.
            logger.warning("Syntax validation exception: " + e.getMessage());
            ValidationResult result = e.getResult();
            if (result != null) {
                for (ValidationError err : result.getErrorsList()) {
                    issues.add(toIssue(err));
                    if (opts.getMaxErrors() > 0 && issues.size() >= opts.getMaxErrors()) {
                        truncated = true;
                        break;
                    }
                }
            } else {
                issues.add(new ValidationIssue("1.0",
                    "PDF syntax error: " + e.getMessage(), null));
            }
            valid = false;
        }

        long durationMs = System.currentTimeMillis() - start;
        logger.info(String.format("Validation complete: valid=%b, issues=%d, %.2fs",
            valid, issues.size(), durationMs / 1000.0));

        return new PdfValidationResult.Builder()
            .inputPath(opts.getInputPath())
            .standard(opts.getStandard())
            .valid(valid)
            .issues(issues)
            .truncated(truncated)
            .durationMs(durationMs)
            .totalPages(totalPages)
            .build();
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static ValidationIssue toIssue(ValidationError err) {
        String  code   = err.getErrorCode();
        String  detail = err.getDetails();
        // getPageNumber() returns Integer (nullable) in PDFBox 3.x.
        // Preflight also uses -1 to mean "not page-specific", so treat both as null.
        Integer rawPage = err.getPageNumber();
        Integer page    = (rawPage != null && rawPage > 0) ? rawPage : null;
        return new ValidationIssue(code, detail, page);
    }

    private static File requireFile(String path) throws IOException {
        File f = new File(path);
        if (!f.exists() || !f.isFile())
            throw new IOException("PDF file not found: " + path);
        return f;
    }
}
