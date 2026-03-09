package com.pdfcreator.manipulator;

import com.pdfcreator.extractor.PasswordRequiredException;
import com.pdfcreator.pdfa.PdfACompliance;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Merges multiple PDF files into one, and splits a single PDF into multiple files.
 *
 * Thread-safe — a single instance can be shared across threads because no
 * mutable state is held between calls.
 *
 * ── MERGE ─────────────────────────────────────────────────────────────────
 *
 * Uses PDFBox's PDFMergerUtility which handles page content streams, resources,
 * fonts, images, form fields, and bookmarks correctly across documents.
 *
 * PDFBox classes used for merge:
 *   PDFMergerUtility          — high-level merge coordinator
 *   PDFMergerUtility.setDestinationFileName() — sets output path
 *   PDFMergerUtility.addSource(File)          — adds an input PDF (no password)
 *   Loader.loadPDF(File, String)              — loads password-protected inputs
 *                                               before passing PDDocument to merger
 *   PDFMergerUtility.mergeDocuments(IOUtils)  — executes the merge
 *
 * For password-protected inputs, we load the PDDocument manually with the
 * password, then pass the open document to the merger. The merger reads page
 * content from the open document and does not need the password itself.
 *
 * ── SPLIT ─────────────────────────────────────────────────────────────────
 *
 * Three strategies (see SplitOptions for details):
 *   BY_PAGE_RANGE    — explicit {start,end} pairs
 *   BY_EVERY_N_PAGES — fixed chunk size
 *   INTO_N_PARTS     — divide into N roughly equal parts
 *
 * PDFBox classes used for split:
 *   Loader.loadPDF(File, String)         — opens source (with optional password)
 *   PDDocument (new)                     — one new document created per output file
 *   PDDocument.importPage(PDPage)        — copies a page including all its resources
 *                                          (fonts, images, annotations) to the new doc
 *   PDDocument.save(String)              — writes the output file
 *
 * importPage() is the correct PDFBox 3.x API for page copying. It deep-copies
 * the page's content streams and resource dictionary into the target document,
 * so the output files are fully self-contained.
 *
 * Usage:
 *
 *   PdfManipulator manipulator = new PdfManipulator();
 *
 *   // Merge
 *   MergeOptions mergeOpts = new MergeOptions.Builder()
 *       .addInput("jan.pdf")
 *       .addInput("feb.pdf")
 *       .output("q1.pdf")
 *       .build();
 *   manipulator.merge(mergeOpts);
 *
 *   // Split every 5 pages
 *   SplitOptions splitOpts = new SplitOptions.Builder("report.pdf", "output/")
 *       .everyNPages(5)
 *       .build();
 *   SplitResult result = manipulator.split(splitOpts);
 */
public class PdfManipulator {

    private static final Logger logger = Logger.getLogger(PdfManipulator.class.getName());

    // -----------------------------------------------------------------------
    // Merge
    // -----------------------------------------------------------------------

    /**
     * Merges all input PDFs in MergeOptions into a single output PDF.
     *
     * Inputs are appended in the order they were added to MergeOptions.
     * The output directory is created if it does not exist.
     *
     * @param options merge configuration (inputs, output path, metadata flag)
     * @throws PasswordRequiredException if a password-protected input has a wrong or missing password
     * @throws IOException on any read or write failure
     */
    public void merge(MergeOptions options) throws IOException {
        logger.info("Starting merge: " + options);

        // Ensure output directory exists
        File outputFile = new File(options.getOutputPath());
        if (outputFile.getParentFile() != null)
            Files.createDirectories(outputFile.getParentFile().toPath());

        // PDFBox 3.x merge API:
        //   PDFMergerUtility.appendDocument(PDDocument destination, PDDocument source)
        //   copies all pages + resources from source into destination in-memory.
        //   addSource() / mergeDocuments() were removed in PDFBox 3.x.
        //
        // We open every input as a PDDocument (using password where needed),
        // append each into a single destination document, then save once.
        PDFMergerUtility merger = new PDFMergerUtility();

        // Keep all source docs open until after appendDocument() calls complete,
        // then close them in the finally block.
        List<PDDocument> sourceDocs = new ArrayList<>();

        try (PDDocument destination = new PDDocument()) {

            PDDocumentInformation firstInfo = null;

            for (MergeOptions.InputEntry entry : options.getInputs()) {
                File inputFile = new File(entry.getPath());
                if (!inputFile.exists())
                    throw new IOException("Input file not found: " + entry.getPath());

                PDDocument source = loadWithPassword(
                    inputFile, entry.getPath(), entry.getPassword());
                sourceDocs.add(source);

                if (firstInfo == null)
                    firstInfo = source.getDocumentInformation();

                // appendDocument copies all pages and their resources from source
                // into destination. destination and source must both be open.
                merger.appendDocument(destination, source);
            }

            // Apply metadata from first input to the merged output
            if (options.isCopyMetadataFromFirst() && firstInfo != null) {
                PDDocumentInformation info = destination.getDocumentInformation();
                if (firstInfo.getTitle()    != null) info.setTitle(firstInfo.getTitle());
                if (firstInfo.getAuthor()   != null) info.setAuthor(firstInfo.getAuthor());
                if (firstInfo.getSubject()  != null) info.setSubject(firstInfo.getSubject());
                if (firstInfo.getKeywords() != null) info.setKeywords(firstInfo.getKeywords());
                if (firstInfo.getCreator()  != null) info.setCreator(firstInfo.getCreator());
                info.setProducer("PdfCreator / Apache PDFBox 3");
            }

            // If any source document was PDF/A, transfer compliance markers to the merged output
            boolean anyPdfA = sourceDocs.stream().anyMatch(PdfACompliance::isPdfA);
            if (anyPdfA) {
                try {
                    PdfACompliance.attach(destination);
                    logger.info("PDF/A compliance markers transferred to merged output");
                } catch (IOException e) {
                    logger.warning("Could not attach PDF/A markers to merged output: " + e.getMessage());
                }
            }

            destination.save(options.getOutputPath());
        } finally {
            for (PDDocument src : sourceDocs) {
                try { src.close(); } catch (IOException ignored) {}
            }
        }

        logger.info("Merge complete: " + options.getOutputPath());
        System.out.printf("Merged %d file(s) -> %s%n",
            options.getInputs().size(), options.getOutputPath());
    }

    // -----------------------------------------------------------------------
    // Split
    // -----------------------------------------------------------------------

    /**
     * Splits the source PDF into multiple output files according to the chosen strategy.
     *
     * The output directory is created if it does not exist.
     *
     * @param options split configuration
     * @return SplitResult containing paths and page counts of all output files
     * @throws PasswordRequiredException if the source PDF is password-protected and the
     *                                   password is wrong or missing
     * @throws IOException on read or write failure
     */
    public SplitResult split(SplitOptions options) throws IOException {
        logger.info("Starting split: " + options);

        File sourceFile = new File(options.getInputPath());
        if (!sourceFile.exists())
            throw new IOException("Input file not found: " + options.getInputPath());

        Files.createDirectories(Path.of(options.getOutputDir()));

        try (PDDocument source = loadWithPassword(
                sourceFile, options.getInputPath(), options.getPassword())) {

            int totalPages = source.getNumberOfPages();
            logger.info("Source has " + totalPages + " pages, strategy: " + options.getStrategy());

            // Resolve the page ranges from the chosen strategy
            List<int[]> ranges = resolveRanges(options, totalPages);

            List<String>  outputPaths = new ArrayList<>();
            List<Integer> pageCounts  = new ArrayList<>();
            int partNum = 1;

            for (int[] range : ranges) {
                int startPage = range[0]; // 1-based inclusive
                int endPage   = range[1]; // 1-based inclusive

                String outputPath = buildOutputPath(options, partNum, ranges.size());

                writePart(source, startPage, endPage, outputPath);

                outputPaths.add(outputPath);
                pageCounts.add(endPage - startPage + 1);

                System.out.printf("  Part %d/%d: pages %d–%d → %s%n",
                    partNum, ranges.size(), startPage, endPage, outputPath);
                partNum++;
            }

            SplitResult result = new SplitResult.Builder()
                .outputPaths(outputPaths)
                .pageCounts(pageCounts)
                .totalPagesProcessed(totalPages)
                .sourcePath(options.getInputPath())
                .build();

            logger.info("Split complete: " + result);
            return result;
        }
    }

    // -----------------------------------------------------------------------
    // Private — range resolution
    // -----------------------------------------------------------------------

    /**
     * Converts the chosen SplitOptions strategy into a concrete list of
     * {startPage, endPage} pairs (both 1-based, inclusive).
     */
    private List<int[]> resolveRanges(SplitOptions options, int totalPages) {
        return switch (options.getStrategy()) {

            case BY_PAGE_RANGE -> {
                // Caller supplied explicit ranges — validate and use them directly
                List<int[]> ranges = new ArrayList<>();
                for (int[] r : options.getPageRanges()) {
                    int s = r[0], e = r[1];
                    if (s < 1 || e > totalPages || s > e)
                        throw new IllegalArgumentException(String.format(
                            "Invalid page range [%d,%d] for a %d-page document", s, e, totalPages));
                    ranges.add(new int[]{s, e});
                }
                yield ranges;
            }

            case BY_EVERY_N_PAGES -> {
                // Chunk: [1..n], [n+1..2n], …
                int n = options.getChunkSize();
                List<int[]> ranges = new ArrayList<>();
                for (int s = 1; s <= totalPages; s += n) {
                    ranges.add(new int[]{s, Math.min(s + n - 1, totalPages)});
                }
                yield ranges;
            }

            case INTO_N_PARTS -> {
                // Distribute totalPages across partCount parts as evenly as possible.
                // Earlier parts get one extra page when there is a remainder.
                int n      = Math.min(options.getPartCount(), totalPages);
                int base   = totalPages / n;
                int extras = totalPages % n;   // first 'extras' parts get base+1 pages
                List<int[]> ranges = new ArrayList<>();
                int cursor = 1;
                for (int i = 0; i < n; i++) {
                    int size = base + (i < extras ? 1 : 0);
                    ranges.add(new int[]{cursor, cursor + size - 1});
                    cursor += size;
                }
                yield ranges;
            }
        };
    }

    // -----------------------------------------------------------------------
    // Private — page writing
    // -----------------------------------------------------------------------

    /**
     * Creates a new PDDocument, imports the specified page range from the source,
     * and saves it to outputPath.
     *
     * PDFBox 3.x: PDDocument.importPage(PDPage) performs a deep copy of the page
     * and all its referenced resources (fonts, images, colour spaces, patterns)
     * into the target document, making each output file fully self-contained.
     *
     * @param source    open source PDDocument (not closed here — caller owns it)
     * @param startPage 1-based first page to include
     * @param endPage   1-based last page to include (inclusive)
     * @param outputPath file path to write the output PDF to
     */
    private void writePart(PDDocument source, int startPage, int endPage,
                            String outputPath) throws IOException {
        try (PDDocument part = new PDDocument()) {
            for (int p = startPage; p <= endPage; p++) {
                PDPage sourcePage = source.getPage(p - 1);  // PDFBox is 0-based internally
                part.importPage(sourcePage);
            }
            // Propagate PDF/A compliance markers from source to each split part
            if (PdfACompliance.isPdfA(source)) {
                PdfACompliance.transfer(source, part);
            }
            part.save(outputPath);
        }
    }

    /**
     * Generates the output file path for a given part number.
     * Pattern: "{outputDir}/{prefix}-part{N}.pdf"
     * Zero-pads the part number when there are >= 10 parts (e.g. "part01", "part10").
     */
    private String buildOutputPath(SplitOptions options, int partNum, int totalParts) {
        String dir    = options.getOutputDir();
        if (!dir.endsWith("/") && !dir.endsWith("\\")) dir += "/";
        String prefix = options.getFilenamePrefix();
        String numStr = totalParts >= 10
            ? String.format("%0" + String.valueOf(totalParts).length() + "d", partNum)
            : String.valueOf(partNum);
        return dir + prefix + "-part" + numStr + ".pdf";
    }

    // -----------------------------------------------------------------------
    // Private — password-aware loader (same pattern as PdfTextExtractor)
    // -----------------------------------------------------------------------

    /**
     * Opens a PDDocument with optional password.
     *
     * PDFBox 3.x API:
     *   Loader.loadPDF(File)           — no password
     *   Loader.loadPDF(File, String)   — with password (tries as user then owner)
     *
     * @throws PasswordRequiredException if the document is encrypted and the
     *         password is missing or wrong
     */
    private static PDDocument loadWithPassword(File file, String path,
                                                String password) throws IOException {
        try {
            if (password != null && !password.isEmpty()) {
                return Loader.loadPDF(file, password);
            } else {
                PDDocument doc = Loader.loadPDF(file);
                if (doc.isEncrypted()
                        && !doc.getCurrentAccessPermission().canExtractContent()) {
                    doc.close();
                    throw new PasswordRequiredException(path, false);
                }
                return doc;
            }
        } catch (InvalidPasswordException e) {
            throw new PasswordRequiredException(path, password != null, e);
        }
    }

    // -----------------------------------------------------------------------
    // PDF/A propagation
    // -----------------------------------------------------------------------

    /**
     * After a merge, checks whether any source was PDF/A and, if so, re-attaches
     * the PDF/A identification to the merged output. We load the output, attach,
     * and re-save in-place.
     */
    private static void transferPdfAIfNeeded(java.util.List<String> inputPaths,
                                              String outputPath) {
        // Check if any input was PDF/A
        boolean anyPdfA = false;
        for (String ip : inputPaths) {
            try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(new java.io.File(ip))) {
                if (PdfACompliance.isPdfA(doc)) { anyPdfA = true; break; }
            } catch (Exception ignored) {}
        }
        if (!anyPdfA) return;

        try (PDDocument merged = org.apache.pdfbox.Loader.loadPDF(new java.io.File(outputPath))) {
            PdfACompliance.attach(merged);
            merged.save(outputPath);
            logger.info("PDF/A compliance markers transferred to merged output");
        } catch (Exception e) {
            logger.warning("Could not transfer PDF/A markers to merged output: " + e.getMessage());
        }
    }

}
