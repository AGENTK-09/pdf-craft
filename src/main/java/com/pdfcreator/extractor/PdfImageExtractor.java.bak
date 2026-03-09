package com.pdfcreator.extractor;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Extracts images from a PDF file using Apache PDFBox 3.x.
 *
 * Thread-safe — a single instance can be shared across threads.
 * Each extract() call opens and closes its own PDDocument.
 *
 * How PDF image extraction works in PDFBox:
 *
 *   Each PDF page has a PDResources object which holds a dictionary of
 *   named XObject resources. An XObject can be either:
 *
 *     PDImageXObject  — a raster image (JPEG, PNG, TIFF, JBIG2, etc.)
 *     PDFormXObject   — a reusable group of drawing instructions which may
 *                       itself contain nested images in its own PDResources
 *
 *   This extractor walks both levels — direct images on a page AND images
 *   nested inside form XObjects — so no embedded image is missed.
 *
 *   Soft-mask images (alpha channel masks stored as separate XObjects by
 *   PDFBox's internal representation) are identified by
 *   PDImageXObject.isStencil() or by being referenced from the parent
 *   image's SMask entry. By default these are skipped because they are
 *   artefacts of PDFBox's internal model, not standalone images.
 *
 * Usage:
 *
 *   PdfImageExtractor extractor = new PdfImageExtractor();
 *
 *   // Extract all images from a PDF
 *   List<ExtractedImage> images = extractor.extract("report.pdf");
 *
 *   // Extract with options (page range, min size, format)
 *   ImageExtractionOptions opts = new ImageExtractionOptions.Builder()
 *       .startPage(1).endPage(2)
 *       .minWidth(100).minHeight(100)
 *       .preferredFormat("jpg")
 *       .build();
 *   List<ExtractedImage> images = extractor.extract("report.pdf", opts);
 *
 *   // Save all extracted images to a directory
 *   extractor.extractToDirectory("report.pdf", "output/images/", opts);
 *
 *   // Programmatic — extract from an already-open PDDocument
 *   try (PDDocument doc = Loader.loadPDF(new File("report.pdf"), password.getBytes())) {
 *       List<ExtractedImage> images = extractor.extract(doc, "report.pdf", opts);
 *   }
 */
public class PdfImageExtractor {

    private static final Logger logger = Logger.getLogger(PdfImageExtractor.class.getName());

    // -----------------------------------------------------------------------
    // Public API — file path variants
    // -----------------------------------------------------------------------

    /**
     * Extracts all images from the given PDF using default options.
     *
     * @param pdfPath path to the source PDF file
     * @return list of ExtractedImage objects, one per image found
     * @throws IOException if the file cannot be read or is not a valid PDF
     */
    public List<ExtractedImage> extract(String pdfPath) throws IOException {
        return extract(pdfPath, ImageExtractionOptions.defaults());
    }

    /**
     * Extracts images from a password-protected PDF using default options.
     * Convenience overload — equivalent to extract(path, new Builder().password(password).build()).
     *
     * @param pdfPath  path to the PDF file
     * @param password user or owner password; null for unprotected PDFs
     * @throws PasswordRequiredException if the password is null or incorrect
     * @throws IOException if the file cannot be read
     */
    public List<ExtractedImage> extract(String pdfPath, String password) throws IOException {
        return extract(pdfPath, new ImageExtractionOptions.Builder().password(password).build());
    }

    /**
     * Extracts images from the given PDF using the provided options.
     *
     * @param pdfPath path to the source PDF file
     * @param options controls page range, minimum dimensions, output format
     * @return list of ExtractedImage objects in page and index order
     * @throws IOException if the file cannot be read or is not a valid PDF
     */
    public List<ExtractedImage> extract(String pdfPath,
                                         ImageExtractionOptions options) throws IOException {
        File file = new File(pdfPath);
        if (!file.exists()) throw new IOException("PDF file not found: " + pdfPath);
        if (!file.isFile()) throw new IOException("Path is not a file: " + pdfPath);

        logger.info("Extracting images: " + pdfPath + " | " + options);

        try (PDDocument document = loadWithPassword(file, pdfPath, options.getPassword())) {
            return extract(document, pdfPath, options);
        }
    }

    /**
     * Extracts images from a PDDocument already loaded in memory.
     * The caller retains ownership of the document — it is NOT closed here.
     *
     * @param document   open PDDocument
     * @param sourcePath original file path (used for logging only)
     * @param options    extraction configuration
     * @return list of ExtractedImage objects
     * @throws IOException if image encoding fails
     */
    public List<ExtractedImage> extract(PDDocument document, String sourcePath,
                                         ImageExtractionOptions options) throws IOException {

        int totalPages = document.getNumberOfPages();
        int startPage  = options.getStartPage() < 1 ? 1
                         : Math.min(options.getStartPage(), totalPages);
        int endPage    = options.getEndPage()   < 1 ? totalPages
                         : Math.min(options.getEndPage(), totalPages);

        List<ExtractedImage> results = new ArrayList<>();

        for (int pageNo = startPage; pageNo <= endPage; pageNo++) {
            PDPage page = document.getPage(pageNo - 1);   // PDFBox is 0-based internally
            int imageIndexOnPage = 0;

            List<PDImageXObject> pageImages = collectImages(page.getResources(), options);

            for (PDImageXObject imageXObject : pageImages) {
                ExtractedImage extracted = encodeImage(
                    imageXObject, pageNo, imageIndexOnPage, options);

                if (extracted != null) {
                    results.add(extracted);
                    imageIndexOnPage++;
                }
            }
        }

        logger.info(String.format("Image extraction complete: %d image(s) from %s",
            results.size(), sourcePath));

        return results;
    }

    // -----------------------------------------------------------------------
    // Convenience — extract and save to directory
    // -----------------------------------------------------------------------

    /**
     * Extracts images from the PDF and saves each one to the given directory.
     *
     * Files are named using ExtractedImage.getSuggestedName():
     *   "page1-img0.png", "page1-img1.png", "page2-img0.jpg", etc.
     *
     * The output directory is created if it does not exist.
     *
     * @param pdfPath     source PDF
     * @param outputDir   directory to write image files into
     * @param options     extraction configuration
     * @return list of ExtractedImage objects (same as extract())
     * @throws IOException on read or write failure
     */
    public List<ExtractedImage> extractToDirectory(String pdfPath, String outputDir,
                                                    ImageExtractionOptions options)
            throws IOException {

        List<ExtractedImage> images = extract(pdfPath, options);

        Path dir = Path.of(outputDir);
        Files.createDirectories(dir);

        for (ExtractedImage img : images) {
            Path dest = dir.resolve(img.getSuggestedName());
            Files.write(dest, img.getData());
            logger.info("Saved: " + dest);
        }

        System.out.printf("Saved %d image(s) to: %s%n", images.size(), outputDir);
        return images;
    }

    // -----------------------------------------------------------------------
    // Password-aware PDF loader
    // -----------------------------------------------------------------------

    /**
     * Loads a PDDocument with optional password support.
     * Uses the same logic as PdfTextExtractor.loadWithPassword().
     *
     * @throws PasswordRequiredException if password is wrong or missing for an encrypted PDF
     */
    private static PDDocument loadWithPassword(File file, String pdfPath,
                                               String password) throws IOException {
        try {
            if (password != null && !password.isEmpty()) {
                return Loader.loadPDF(file, password);
            } else {
                PDDocument doc = Loader.loadPDF(file);
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

    // -----------------------------------------------------------------------
    // Core — walk PDResources and collect PDImageXObject instances
    // -----------------------------------------------------------------------

    /**
     * Walks a PDResources dictionary and collects all PDImageXObject instances.
     *
     * This is done recursively because images can be nested inside
     * PDFormXObject resources (a reusable drawing group). The recursion depth
     * in practice is almost always 1 level, but the implementation handles
     * arbitrary nesting correctly.
     *
     * PDFBox 3.x API used:
     *   PDResources.getXObjectNames()  — returns Iterable<COSName> of all XObject keys
     *   PDResources.getXObject(name)   — returns PDXObject (supertype)
     *   PDImageXObject                 — subtype for raster images
     *   PDFormXObject                  — subtype for form (group) XObjects
     *   PDImageXObject.isStencil()     — true for 1-bit mask images (decorative rules, etc.)
     */
    private List<PDImageXObject> collectImages(PDResources resources,
                                                ImageExtractionOptions options)
            throws IOException {

        List<PDImageXObject> found = new ArrayList<>();
        if (resources == null) return found;

        for (COSName name : resources.getXObjectNames()) {
            PDXObject xObject;
            try {
                xObject = resources.getXObject(name);
            } catch (IOException e) {
                // Corrupted or unsupported XObject — log and skip
                logger.warning("Skipping unreadable XObject '" + name.getName() + "': " + e.getMessage());
                continue;
            }

            if (xObject instanceof PDImageXObject image) {
                // Skip stencil (1-bit mask) images — these are decorative rule lines
                // or clipping masks, not standalone content images
                if (options.isSkipSoftMasks() && image.isStencil()) {
                    logger.fine("Skipping stencil image: " + name.getName());
                    continue;
                }

                // Apply minimum dimension filter
                if (image.getWidth()  < options.getMinWidth()  ||
                    image.getHeight() < options.getMinHeight()) {
                    logger.fine(String.format("Skipping small image %s (%dx%d < min %dx%d)",
                        name.getName(), image.getWidth(), image.getHeight(),
                        options.getMinWidth(), options.getMinHeight()));
                    continue;
                }

                found.add(image);

            } else if (xObject instanceof PDFormXObject form) {
                // Recurse into form XObjects to find nested images
                found.addAll(collectImages(form.getResources(), options));
            }
        }

        return found;
    }

    // -----------------------------------------------------------------------
    // Core — encode a PDImageXObject to bytes
    // -----------------------------------------------------------------------

    /**
     * Converts a PDImageXObject to an ExtractedImage by rendering it to a
     * BufferedImage and encoding it to bytes in the target format.
     *
     * PDFBox 3.x API:
     *   PDImageXObject.getImage()  — renders the XObject to a java.awt.image.BufferedImage
     *   ImageIO.write()            — encodes the BufferedImage to JPEG or PNG bytes
     *
     * JPEG note: BufferedImage with transparency (TYPE_4BYTE_ABGR or TYPE_INT_ARGB)
     * cannot be encoded as JPEG directly. We convert to TYPE_INT_RGB first,
     * painting the transparent areas white.
     *
     * @return ExtractedImage, or null if encoding fails (logged as warning)
     */
    private ExtractedImage encodeImage(PDImageXObject imageXObject,
                                        int pageNo, int imageIndex,
                                        ImageExtractionOptions options) {
        try {
            BufferedImage buffered = imageXObject.getImage();
            if (buffered == null) {
                logger.warning(String.format(
                    "Page %d image %d: getImage() returned null — skipping", pageNo, imageIndex));
                return null;
            }

            String fmt = options.getPreferredFormat().toLowerCase();

            // JPEG does not support transparency — convert to opaque RGB if needed
            if ("jpg".equals(fmt) || "jpeg".equals(fmt)) {
                fmt = "jpg";
                if (buffered.getColorModel().hasAlpha()) {
                    BufferedImage rgb = new BufferedImage(
                        buffered.getWidth(), buffered.getHeight(), BufferedImage.TYPE_INT_RGB);
                    rgb.createGraphics().drawImage(buffered, 0, 0,
                        java.awt.Color.WHITE, null);
                    buffered = rgb;
                }
            }

            // Encode to bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean written = ImageIO.write(buffered, fmt.equals("jpg") ? "jpeg" : "png", baos);
            if (!written) {
                // Fall back to PNG if the chosen format writer is unavailable
                logger.warning("ImageIO could not write format '" + fmt + "', falling back to PNG");
                fmt = "png";
                ImageIO.write(buffered, "png", baos);
            }

            byte[] imageBytes = baos.toByteArray();

            return new ExtractedImage.Builder()
                .pageNumber(pageNo)
                .imageIndex(imageIndex)
                .format(fmt)
                .width(buffered.getWidth())
                .height(buffered.getHeight())
                .data(imageBytes)
                .build();

        } catch (IOException e) {
            logger.warning(String.format(
                "Page %d image %d: encoding failed — %s", pageNo, imageIndex, e.getMessage()));
            return null;
        }
    }
}
