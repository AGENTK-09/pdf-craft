package com.pdfcreator.printer;

import com.pdfcreator.extractor.PasswordRequiredException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.printing.PDFPageable;
import org.apache.pdfbox.printing.PDFPrintable;
import org.apache.pdfbox.printing.Scaling;

import javax.print.DocFlavor;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.PageRanges;
import javax.print.attribute.standard.Sides;
import java.awt.print.PageFormat;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Prints a PDF file using the standard Java Printing API combined with
 * PDFBox's PDFPrintable + PDFPageable API.
 *
 * Thread-safe — a single instance can be shared across threads.
 *
 * ── HOW PDF PRINTING WORKS IN JAVA + PDFBOX ─────────────────────────────
 *
 * Java's printing API (java.awt.print) works with a Pageable/Printable interface:
 *
 *   PrinterJob            — the top-level print job controller. Selects the
 *                           printer, holds PrintRequestAttributes, and drives
 *                           the rendering loop.
 *
 *   Pageable              — an interface that tells PrinterJob how many pages
 *                           there are and how to render each one.
 *
 *   PDFPageable           — PDFBox's Pageable implementation. Delegates to
 *                           It renders each PDF page onto a java.awt.Graphics2D
 *                           surface provided by the printer driver, at the
 *                           resolution the printer requests (typically 600dpi).
 *
 *   Scaling               — PDFBox enum controlling how pages fit the paper:
 *                           SCALE_TO_FIT, SHRINK_TO_FIT, or ACTUAL_SIZE
 *
 * PrintRequestAttributeSet — carries job-level attributes submitted alongside
 *                           the job: Copies, PageRanges, Sides, JobName.
 *                           These are the javax.print.attribute.standard classes.
 *
 * PrintService              — represents a named printer. Discovered via
 *                           PrintServiceLookup.lookupPrintServices().
 *
 * ── SILENT VS DIALOG PRINTING ────────────────────────────────────────────
 *
 * PrinterJob.printDialog()  — shows the OS native print dialog. The user can
 *                             change settings and confirm or cancel. Returns
 *                             false if the user cancels.
 *
 * Silent printing           — skips the dialog entirely. Sends the job
 *                             directly to the printer. Required for server-
 *                             side / headless / batch scenarios.
 *
 * Usage:
 *
 *   PdfPrinter printer = new PdfPrinter();
 *
 *   // List available printers
 *   printer.listPrinters().forEach(System.out::println);
 *
 *   // Print silently to the default printer
 *   PrintOptions opts = new PrintOptions.Builder("report.pdf")
 *       .silent(true)
 *       .build();
 *   printer.print(opts);
 *
 *   // Print pages 2-4 to a named printer, duplex, 2 copies
 *   PrintOptions opts = new PrintOptions.Builder("report.pdf")
 *       .printerName("HP LaserJet 400")
 *       .startPage(2).endPage(4)
 *       .copies(2)
 *       .sides(PrintOptions.Sides.DUPLEX_LONG_EDGE)
 *       .scaling(PrintOptions.ScalingMode.SHRINK)
 *       .silent(true)
 *       .build();
 *   printer.print(opts);
 */
public class PdfPrinter {

    private static final Logger logger = Logger.getLogger(PdfPrinter.class.getName());

    // -----------------------------------------------------------------------
    // Printer discovery
    // -----------------------------------------------------------------------

    /**
     * Returns the names of all print services available to the JVM.
     *
     * Uses javax.print.PrintServiceLookup.lookupPrintServices() which queries
     * the OS print spooler. On Windows this returns all installed printers.
     * On Linux/macOS it returns CUPS-registered printers.
     *
     * @return list of printer display names, in OS-defined order
     */
    public List<String> listPrinters() {
        return Arrays.stream(
            PrintServiceLookup.lookupPrintServices(null, null))
            .map(PrintService::getName)
            .collect(Collectors.toList());
    }

    /**
     * Returns the name of the system default printer, or null if none is configured.
     */
    public String getDefaultPrinterName() {
        PrintService def = PrintServiceLookup.lookupDefaultPrintService();
        return def != null ? def.getName() : null;
    }

    // -----------------------------------------------------------------------
    // Print
    // -----------------------------------------------------------------------

    /**
     * Prints the PDF described by PrintOptions.
     *
     * Steps performed:
     *   1. Load the PDF (with password if provided)
     *   2. Resolve the target PrintService (named or default)
     *   3. Build a PrintRequestAttributeSet with copies, page range, sides, job name
     *   4. Map PrintOptions.ScalingMode to PDFBox Scaling enum
     *   5. Create a PDFPageable (PDFBox Pageable wrapping PDFPrintable) over the open document
     *   6. Assign the Pageable to a PrinterJob
     *   7. Either show the print dialog (silent=false) or print directly (silent=true)
     *
     * @param options print configuration
     * @throws PrinterException      if the printer rejects or fails the job
     * @throws PasswordRequiredException if the PDF is encrypted and the password is wrong/missing
     * @throws IOException           if the PDF file cannot be read
     */
    public void print(PrintOptions options)
            throws PrinterException, IOException {

        logger.info("Starting print job: " + options);

        File pdfFile = new File(options.getPdfPath());
        if (!pdfFile.exists())
            throw new IOException("PDF file not found: " + options.getPdfPath());

        try (PDDocument document = loadWithPassword(
                pdfFile, options.getPdfPath(), options.getPassword())) {

            // ── 1. Resolve print service ─────────────────────────────────
            PrinterJob job = PrinterJob.getPrinterJob();
            PrintService service = resolvePrintService(options.getPrinterName());
            if (service == null)
                throw new PrinterException(
                    options.getPrinterName() != null
                    ? "Printer not found: " + options.getPrinterName()
                    : "No default printer configured on this system.");
            job.setPrintService(service);

            // ── 2. Build PrintRequestAttributeSet ────────────────────────
            PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();

            // Copies
            attrs.add(new Copies(options.getCopies()));

            // Job name (shown in OS print queue)
            attrs.add(new JobName(options.getJobName(), null));

            // Page range — convert from 1-based PrintOptions to javax.print PageRanges
            int totalPages = document.getNumberOfPages();
            int startPage  = options.getStartPage() < 1 ? 1
                             : Math.min(options.getStartPage(), totalPages);
            int endPage    = options.getEndPage()   < 1 ? totalPages
                             : Math.min(options.getEndPage(), totalPages);
            attrs.add(new PageRanges(startPage, endPage));

            // Sides (simplex / duplex)
            attrs.add(mapSides(options.getSides()));

            // ── 3. Build Pageable using PDFBox printing classes ──────────
            // PDFPageable  — PDFBox Pageable (multi-page wrapper around PDDocument).
            //                Its constructor takes (PDDocument, Orientation, border, dpi).
            //                It does NOT accept a Scaling parameter.
            //
            // PDFPrintable — PDFBox Printable for a single page.
            //                Constructor: (PDDocument, Scaling) — scaling lives here.
            //
            // To honour the Scaling choice we use a java.awt.print.Book and
            // append one PDFPrintable (covering all pages) with the chosen Scaling.
            Scaling pdfBoxScaling = mapScaling(options.getScaling());
            PDFPrintable printable = new PDFPrintable(document, pdfBoxScaling);

            java.awt.print.Book book = new java.awt.print.Book();
            java.awt.print.PageFormat pageFormat = job.defaultPage();
            book.append(printable, pageFormat, totalPages);

            // ── 4. Set the Pageable on the PrinterJob ─────────────────────
            job.setPageable(book);
            job.setJobName(options.getJobName());

            // ── 5. Print ──────────────────────────────────────────────────
            if (options.isSilent()) {
                // Silent (headless) — send directly without dialog
                job.print(attrs);
                logger.info(String.format(
                    "Job '%s' sent to '%s' (pages %d-%d, %d cop%s)",
                    options.getJobName(), service.getName(),
                    startPage, endPage,
                    options.getCopies(), options.getCopies() == 1 ? "y" : "ies"));
            } else {
                // Interactive — show OS print dialog first
                boolean confirmed = job.printDialog(attrs);
                if (confirmed) {
                    job.print(attrs);
                    logger.info("Job confirmed and sent: " + options.getJobName());
                } else {
                    logger.info("Print job cancelled by user: " + options.getJobName());
                    System.out.println("Print job cancelled.");
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Convenience — print to default printer, silent
    // -----------------------------------------------------------------------

    /**
     * Prints the given PDF to the default printer using all default options.
     * Silent (no dialog).
     *
     * @param pdfPath path to the PDF to print
     * @throws PrinterException if printing fails
     * @throws IOException if the file cannot be read
     */
    public void printDefault(String pdfPath) throws PrinterException, IOException {
        print(new PrintOptions.Builder(pdfPath).silent(true).build());
    }

    /**
     * Prints the given password-protected PDF to the default printer.
     * Silent (no dialog).
     *
     * @param pdfPath  path to the PDF to print
     * @param password user or owner password
     * @throws PrinterException if printing fails
     * @throws IOException if the file cannot be read
     */
    public void printDefault(String pdfPath, String password)
            throws PrinterException, IOException {
        print(new PrintOptions.Builder(pdfPath).password(password).silent(true).build());
    }

    // -----------------------------------------------------------------------
    // Private — service resolution
    // -----------------------------------------------------------------------

    /**
     * Finds a PrintService by name, or returns the system default when name is null.
     *
     * Matching is case-insensitive and trims surrounding whitespace to be
     * resilient to minor naming inconsistencies across OS printer registrations.
     *
     * @param name printer display name, or null to request the default printer
     * @return matching PrintService, or null if not found
     */
    private static PrintService resolvePrintService(String name) {
        if (name == null || name.isBlank()) {
            return PrintServiceLookup.lookupDefaultPrintService();
        }
        String target = name.trim().toLowerCase();
        for (PrintService svc :
                PrintServiceLookup.lookupPrintServices(null, null)) {
            if (svc.getName().trim().toLowerCase().equals(target))
                return svc;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Private — enum mapping
    // -----------------------------------------------------------------------

    /**
     * Maps PrintOptions.ScalingMode to PDFBox's Scaling enum.
     *
     * PDFBox Scaling values:
     *   SCALE_TO_FIT  — scale up or down to fill the printable area
     *   SHRINK_TO_FIT — scale down only (never enlarge)
     *   ACTUAL_SIZE   — 1 PDF point = 1/72 inch, no scaling
     */
    private static Scaling mapScaling(PrintOptions.ScalingMode mode) {
        return switch (mode) {
            case FIT         -> Scaling.SCALE_TO_FIT;
            case SHRINK      -> Scaling.SHRINK_TO_FIT;
            case ACTUAL_SIZE -> Scaling.ACTUAL_SIZE;
        };
    }

    /**
     * Maps PrintOptions.Sides to the javax.print.attribute.standard.Sides constant.
     *
     * javax.print Sides values:
     *   ONE_SIDED       — simplex
     *   DUPLEX          — flip on long edge (portrait binding)
     *   TUMBLE          — flip on short edge (landscape binding / "tumble duplex")
     */
    private static Sides mapSides(PrintOptions.Sides sides) {
        return switch (sides) {
            case SIMPLEX           -> Sides.ONE_SIDED;
            case DUPLEX_LONG_EDGE  -> Sides.DUPLEX;
            case DUPLEX_SHORT_EDGE -> Sides.TUMBLE;
        };
    }

    // -----------------------------------------------------------------------
    // Private — password-aware PDF loader
    // -----------------------------------------------------------------------

    /**
     * Loads a PDDocument with optional password.
     * Consistent with the pattern used in PdfTextExtractor and PdfManipulator.
     *
     * PDFBox 3.x:
     *   Loader.loadPDF(File)          — no password
     *   Loader.loadPDF(File, String)  — with password (tried as user then owner)
     *
     * @throws PasswordRequiredException if the PDF requires a password and
     *         none was given, or the supplied password is wrong
     */
    private static PDDocument loadWithPassword(File file, String path,
                                                String password) throws IOException {
        try {
            if (password != null && !password.isEmpty()) {
                return Loader.loadPDF(file, password);
            } else {
                PDDocument doc = Loader.loadPDF(file);
                if (doc.isEncrypted()
                        && !doc.getCurrentAccessPermission().canPrint()) {
                    doc.close();
                    throw new PasswordRequiredException(path, false);
                }
                return doc;
            }
        } catch (InvalidPasswordException e) {
            throw new PasswordRequiredException(path, password != null, e);
        }
    }
}
