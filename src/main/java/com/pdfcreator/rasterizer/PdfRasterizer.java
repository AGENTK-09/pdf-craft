package com.pdfcreator.rasterizer;

import com.pdfcreator.extractor.PasswordRequiredException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Renders PDF pages into raster image files (PNG or JPEG).
 *
 * Uses PDFBox's PDFRenderer which internally drives the PDF graphics stack,
 * rasterising all vector graphics, fonts, and embedded images at the
 * requested DPI resolution.
 *
 * ── PDFBox RENDERING API ──────────────────────────────────────────────────
 *
 *   PDFRenderer renderer = new PDFRenderer(doc);
 *
 *   // Render page at given DPI, returning a BufferedImage
 *   BufferedImage image = renderer.renderImageWithDPI(
 *       pageIndex,          // 0-based
 *       dpi,                // e.g. 150, 300
 *       ImageType.RGB       // RGB for JPEG/PNG; ARGB for PNG with transparency
 *   );
 *
 *   ImageType.RGB   — 24-bit colour. Use for JPEG (JPEG does not support alpha).
 *   ImageType.ARGB  — 32-bit colour with alpha. Use for PNG when transparency matters.
 *   ImageType.GRAY  — 8-bit greyscale. Smaller files, suitable for text-only pages.
 *
 * ── IMAGE WRITING ─────────────────────────────────────────────────────────
 *
 *   PNG:  ImageIO.write(image, "PNG", file) — lossless, straightforward.
 *
 *   JPEG: Uses ImageWriter with ImageWriteParam to set compression quality.
 *         The default ImageIO.write() for JPEG uses a fixed quality; we need
 *         the writer API to pass jpegQuality from RasterOptions.
 *         JPEG requires ImageType.RGB (not ARGB — JPEG has no alpha channel).
 *
 * ── FILENAME PATTERN ──────────────────────────────────────────────────────
 *
 *   <outputDir>/<filePrefix>-<pageNumber padded to 3 digits>.<ext>
 *   e.g. output/page-001.png, output/page-002.png
 *
 *   Zero-padding to 3 digits ensures correct alphabetical sort order for
 *   up to 999 pages (sufficient for all practical PDF documents).
 *   For documents > 999 pages, padding auto-expands to fit.
 *
 * ── THREAD SAFETY ─────────────────────────────────────────────────────────
 *
 *   Stateless — a single instance can be shared across threads.
 *   Each rasterize() call creates its own PDDocument and PDFRenderer.
 *   PDFRenderer is NOT thread-safe and must not be shared between calls.
 */
public class PdfRasterizer {

    private static final Logger logger = Logger.getLogger(PdfRasterizer.class.getName());

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Renders the PDF pages described by opts and writes image files to outputDir.
     *
     * @param opts fully-configured RasterOptions
     * @return RasterResult containing the list of written files and stats
     * @throws IOException               on file read/write errors
     * @throws PasswordRequiredException if the PDF is encrypted and no/wrong password given
     */
    public RasterResult rasterize(RasterOptions opts) throws IOException {
        logger.info("Rasterizing: " + opts);
        long start = System.currentTimeMillis();

        File inputFile = requireFile(opts.getInputPath());

        // Create output directory if it doesn't exist
        Path outDir = Path.of(opts.getOutputDir());
        Files.createDirectories(outDir);

        List<String> outputFiles  = new ArrayList<>();
        long         totalBytes   = 0;
        int          totalPages;
        int          pagesRendered = 0;

        // Load PDF (with optional password)
        try (PDDocument doc = loadDocument(inputFile, opts.getPassword(), opts.getInputPath())) {

            totalPages = doc.getNumberOfPages();

            // Resolve page range (convert 1-based inclusive to 0-based)
            int fromPage = opts.getStartPage() < 1 ? 0 : opts.getStartPage() - 1;
            int toPage   = opts.getEndPage()   < 1 ? totalPages - 1
                                                    : Math.min(opts.getEndPage() - 1, totalPages - 1);

            if (fromPage > toPage)
                throw new IOException(String.format(
                    "Invalid page range: startPage=%d endPage=%d (PDF has %d pages)",
                    opts.getStartPage(), opts.getEndPage(), totalPages));

            logger.info(String.format("Rendering pages %d–%d of %d at %.0f DPI as %s",
                fromPage + 1, toPage + 1, totalPages, opts.getDpi(), opts.getFormat()));

            // Create renderer — one instance per PDDocument, not shared across threads
            PDFRenderer renderer = new PDFRenderer(doc);

            // Determine zero-pad width from total page count for nice filenames
            int padWidth = String.valueOf(totalPages).length();
            padWidth = Math.max(padWidth, 3);  // minimum 3 digits (001, 002 ...)

            for (int pageIdx = fromPage; pageIdx <= toPage; pageIdx++) {

                // Render page to BufferedImage
                // RGB for JPEG (no alpha); ARGB for PNG (supports transparency)
                ImageType imageType = opts.getFormat() == RasterOptions.Format.JPEG
                    ? ImageType.RGB : ImageType.ARGB;

                BufferedImage image = renderer.renderImageWithDPI(
                    pageIdx, opts.getDpi(), imageType);

                // Build output file path
                String pageLabel = String.format("%0" + padWidth + "d", pageIdx + 1);
                String filename  = opts.getFilePrefix() + "-" + pageLabel
                    + "." + opts.getFormat().getExtension();
                File outFile = outDir.resolve(filename).toFile();

                // Write image to disk
                writeImage(image, opts.getFormat(), opts.getJpegQuality(), outFile);

                outputFiles.add(outFile.getAbsolutePath());
                totalBytes += outFile.length();
                pagesRendered++;

                logger.fine("Rendered page " + (pageIdx + 1) + " → " + outFile.getName()
                    + " (" + outFile.length() + " bytes)");
            }
        }

        long durationMs = System.currentTimeMillis() - start;
        logger.info(String.format("Rasterization complete: %d page(s), %.2fs, %d files",
            pagesRendered, durationMs / 1000.0, outputFiles.size()));

        return new RasterResult.Builder()
            .inputPath(opts.getInputPath())
            .outputDir(opts.getOutputDir())
            .format(opts.getFormat())
            .dpi(opts.getDpi())
            .totalPages(totalPages)
            .pagesRendered(pagesRendered)
            .outputFiles(outputFiles)
            .durationMs(durationMs)
            .totalSizeBytes(totalBytes)
            .build();
    }

    // -----------------------------------------------------------------------
    // Image writing
    // -----------------------------------------------------------------------

    /**
     * Writes a BufferedImage to a file in the specified format.
     *
     * PNG: straightforward ImageIO.write().
     *
     * JPEG: Uses the ImageWriter API to pass jpegQuality to the encoder.
     *       ImageIO.write() for JPEG ignores quality — the writer API is required.
     *       Also converts ARGB to RGB before writing because JPEG has no alpha channel;
     *       passing an ARGB image to JPEG ImageWriter causes a runtime exception.
     */
    private static void writeImage(BufferedImage image, RasterOptions.Format format,
                                    float jpegQuality, File outFile) throws IOException {
        switch (format) {
            case PNG -> {
                // PNG is lossless — no quality setting needed
                boolean written = ImageIO.write(image, "PNG", outFile);
                if (!written)
                    throw new IOException("No PNG ImageWriter available in this JVM");
            }
            case JPEG -> {
                // JPEG does not support alpha — convert ARGB to RGB if needed
                BufferedImage rgbImage = ensureRgb(image);

                // Get the JPEG writer
                Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("JPEG");
                if (!writers.hasNext())
                    throw new IOException("No JPEG ImageWriter available in this JVM");

                ImageWriter writer = writers.next();
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(jpegQuality);  // 0.0 worst → 1.0 best

                try (ImageOutputStream ios = ImageIO.createImageOutputStream(outFile)) {
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(rgbImage, null, null), param);
                } finally {
                    writer.dispose();
                }
            }
        }
    }

    /**
     * Converts a BufferedImage to TYPE_INT_RGB if it has an alpha channel.
     *
     * JPEG does not support transparency. Passing a TYPE_INT_ARGB image to the
     * JPEG writer produces a corrupt green-tinted output or throws an exception
     * depending on the JVM. Converting to RGB first ensures consistent output.
     *
     * The conversion uses a white background (standard for PDF pages).
     */
    private static BufferedImage ensureRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;

        BufferedImage rgb = new BufferedImage(
            src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = rgb.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }

    // -----------------------------------------------------------------------
    // Document loading
    // -----------------------------------------------------------------------

    private static PDDocument loadDocument(File file, String password,
                                            String pathForError) throws IOException {
        try {
            if (password != null && !password.isBlank()) {
                return Loader.loadPDF(file, password);
            } else {
                PDDocument doc = Loader.loadPDF(file);
                if (doc.isEncrypted()) {
                    doc.close();
                    throw new PasswordRequiredException(pathForError, false);
                }
                return doc;
            }
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            throw new PasswordRequiredException(pathForError,
                password != null && !password.isBlank(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    private static File requireFile(String path) throws IOException {
        File f = new File(path);
        if (!f.exists() || !f.isFile())
            throw new IOException("PDF file not found: " + path);
        return f;
    }
}
