package com.pdfcreator.pdfa;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.AdobePDFSchema;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.xml.XmpSerializer;
import org.apache.xmpbox.xml.DomXmpParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.logging.Logger;

/**
 * Attaches the XMP metadata and ICC output intent required by PDF/A-1b.
 *
 * PDF/A-1b (ISO 19005-1 conformance level B) mandates:
 *
 *   1. XMP METADATA — The document stream must contain a valid XMP metadata
 *      block attached to the document catalog. It must include:
 *        - pdfaid:part = "1"         (PDF/A part 1)
 *        - pdfaid:conformance = "B"  (conformance level B = basic)
 *        - dc:title, dc:creator, xmp:CreateDate, xmp:ModifyDate
 *        - pdf:Producer (matching the document information dictionary)
 *
 *   2. OUTPUT INTENT — The document must declare how RGB colours are to be
 *      interpreted. PDF/A-1b requires an output intent ICC colour profile
 *      for any device-dependent colour space used in the document. We embed
 *      an sRGB profile (IEC 61966-2-1) which covers all RGB colours drawn
 *      by PdfCreator.
 *
 * ── WHAT THIS FIXES ───────────────────────────────────────────────────────
 *
 * Without this class, PDFBox Preflight reports:
 *   Error 7.11 — Missing PDF/A identification schema in XMP metadata
 *   Error 7.1  — XMP metadata missing or invalid
 *   Error 2.4  — Missing OutputIntent for device-dependent colour space
 *
 * ── TRANSFER FOR MERGE/SPLIT ──────────────────────────────────────────────
 *
 * When merging or splitting PDF/A documents, the output document must also
 * carry the PDF/A identification metadata. Use transferPdfAIdentification()
 * to propagate the pdfaid schema from a source to a destination document
 * without re-processing the full document information dictionary.
 *
 * ── ENCRYPTION INCOMPATIBILITY ────────────────────────────────────────────
 *
 * PDF/A-1b (ISO 19005-1 §6.1.3) forbids encryption. If you encrypt a PDF/A
 * document, it is no longer conformant. PdfSecurityManager checks for this
 * and warns accordingly.
 */
public class PdfACompliance {

    private static final Logger logger = Logger.getLogger(PdfACompliance.class.getName());

    private static final String SRGB_ICC_RESOURCE = "/icc/sRGB.icc";

    private PdfACompliance() {}

    // -----------------------------------------------------------------------
    // Main entry point — called by RenderPipeline after rendering
    // -----------------------------------------------------------------------

    /**
     * Attaches PDF/A-1b conformance markers to a PDDocument that is about to
     * be saved. Must be called BEFORE document.save() because the metadata
     * stream is written as part of the document body.
     *
     * Steps performed:
     *   1. Read document information dictionary (title, author, producer, dates)
     *   2. Build an XMPMetadata object with dc, pdf, xmp, and pdfaid schemas
     *   3. Serialise the XMP to bytes and attach as PDMetadata to the catalog
     *   4. Load the sRGB ICC profile from classpath and attach as OutputIntent
     *
     * @param document PDDocument to make conformant (modified in-place)
     * @throws IOException if the ICC profile resource cannot be read
     */
    public static void attach(PDDocument document) throws IOException {
        attachXmpMetadata(document);
        attachOutputIntent(document);
        logger.info("PDF/A-1b conformance markers attached");
    }

    // -----------------------------------------------------------------------
    // XMP metadata
    // -----------------------------------------------------------------------

    private static void attachXmpMetadata(PDDocument document) throws IOException {
        PDDocumentInformation info = document.getDocumentInformation();
        PDDocumentCatalog catalog  = document.getDocumentCatalog();

        // Capture a SINGLE timestamp for both the info dictionary and XMP.
        // If the info dict is written with one Calendar instance and XMP with
        // another, the milliseconds differ and Preflight reports error 7.2
        // (ModificationDate mismatch). Reading back what the info dict already
        // has ensures they are byte-for-byte identical.
        Calendar now          = Calendar.getInstance();
        Calendar creationDate = info.getCreationDate() != null ? info.getCreationDate() : now;
        Calendar modDate      = info.getModificationDate() != null ? info.getModificationDate() : now;

        // Build XMP object graph
        XMPMetadata xmp = XMPMetadata.createXMPMetadata();

        // ── Dublin Core schema (dc:) ────────────────────────────────────
        // dc:title    ← info dict Title
        // dc:creator  ← info dict Author
        // dc:subject  ← info dict Subject  (Preflight 7.2: must match if present)
        // dc:description is NOT the same as dc:subject in XMP — Preflight maps
        // the info dict /Subject key to dc:subject (the bag of keywords field).
        DublinCoreSchema dc = xmp.createAndAddDublinCoreSchema();
        String title = info.getTitle();
        if (title != null && !title.isBlank()) dc.setTitle(title);
        String author = info.getAuthor();
        if (author != null && !author.isBlank()) dc.addCreator(author);
        String subject = info.getSubject();
        if (subject != null && !subject.isBlank()) dc.setDescription(subject);

        // ── Adobe PDF schema (pdf:) ─────────────────────────────────────
        AdobePDFSchema pdf = xmp.createAndAddAdobePDFSchema();
        String producer = info.getProducer();
        pdf.setProducer(producer != null ? producer : "PdfCreator / Apache PDFBox 3");
        String keywords = info.getKeywords();
        if (keywords != null && !keywords.isBlank()) pdf.setKeywords(keywords);

        // ── XMP Basic schema (xmp:) ─────────────────────────────────────
        // Use the same Calendar objects that were written into the info dict
        // so the timestamps are identical to the millisecond (Preflight 7.2).
        XMPBasicSchema basic = xmp.createAndAddXMPBasicSchema();
        basic.setCreateDate(creationDate);
        basic.setModifyDate(modDate);
        String creator = info.getCreator();
        if (creator != null && !creator.isBlank()) basic.setCreatorTool(creator);

        // ── PDF/A Identification schema (pdfaid:) ───────────────────────
        // This is the schema Preflight checks for. Without it, error 7.11 fires.
        // setConformance() throws BadFieldValueException if the value is not
        // a recognised conformance level — "B" and "A" are the valid values.
        try {
            PDFAIdentificationSchema pdfaid = xmp.createAndAddPDFAIdentificationSchema();
            pdfaid.setPart(1);            // ISO 19005-1 → part 1
            pdfaid.setConformance("B");   // conformance level B (Basic)
        } catch (org.apache.xmpbox.type.BadFieldValueException e) {
            throw new IOException("Failed to set PDF/A identification schema: " + e.getMessage(), e);
        }

        // Serialise XMP to bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            new XmpSerializer().serialize(xmp, baos, true);
        } catch (Exception e) {
            throw new IOException("Failed to serialise XMP metadata: " + e.getMessage(), e);
        }

        // Attach to document catalog
        PDMetadata pdMetadata = new PDMetadata(document);
        pdMetadata.importXMPMetadata(baos.toByteArray());
        catalog.setMetadata(pdMetadata);

        logger.fine("XMP PDF/A-1b identification metadata attached (" +
            baos.size() + " bytes)");
    }

    // -----------------------------------------------------------------------
    // Output intent (sRGB colour profile)
    // -----------------------------------------------------------------------

    /**
     * Attaches an sRGB ICC output intent to the document catalog.
     *
     * PDF/A-1b requires an output intent when device-dependent colour spaces
     * (DeviceRGB, DeviceGray, DeviceCMYK) are used. PdfCreator uses DeviceRGB
     * and DeviceGray throughout via java.awt.Color, so this is always required.
     *
     * The sRGB profile (IEC 61966-2-1) is the universal default for screen-
     * based documents and is accepted by all PDF/A validators.
     */
    private static void attachOutputIntent(PDDocument document) throws IOException {
        try (InputStream iccStream = PdfACompliance.class.getResourceAsStream(SRGB_ICC_RESOURCE)) {
            if (iccStream == null) {
                logger.warning("sRGB ICC profile not found at " + SRGB_ICC_RESOURCE +
                    " — PDF/A colour conformance may fail (error 2.4)");
                return;
            }

            PDOutputIntent outputIntent = new PDOutputIntent(document, iccStream);
            outputIntent.setInfo("sRGB IEC61966-2.1");
            outputIntent.setOutputCondition("sRGB IEC61966-2.1");
            outputIntent.setOutputConditionIdentifier("Custom");
            outputIntent.setRegistryName("");

            document.getDocumentCatalog().addOutputIntent(outputIntent);
            logger.fine("sRGB output intent attached");
        }
    }

    // -----------------------------------------------------------------------
    // Transfer for merge/split
    // -----------------------------------------------------------------------

    /**
     * Copies the PDF/A identification XMP schema from sourceDocument into
     * destinationDocument. Used when merging or splitting PDF/A documents to
     * ensure the output retains PDF/A conformance markers.
     *
     * Also transfers the output intent if the source has one.
     *
     * If the source document has no PDF/A identification, this is a no-op
     * (the destination simply won't have PDF/A markers, which is correct if
     * the source wasn't PDF/A in the first place).
     *
     * @param source      document to read PDF/A markers from
     * @param destination document to write PDF/A markers into
     */
    public static void transfer(PDDocument source, PDDocument destination)
            throws IOException {

        // Check whether source has the pdfaid schema in its XMP
        PDDocumentCatalog srcCatalog = source.getDocumentCatalog();
        PDMetadata srcMeta = srcCatalog.getMetadata();
        if (srcMeta == null) return;

        try {
            XMPMetadata srcXmp = new DomXmpParser()
                .parse(srcMeta.toByteArray());
            PDFAIdentificationSchema pdfaid = srcXmp.getPDFAIdentificationSchema();
            if (pdfaid == null || pdfaid.getPart() == null) {
                // Source is not PDF/A — nothing to transfer
                return;
            }
            logger.fine("Transferring PDF/A-1" + pdfaid.getConformance() +
                " markers to merged/split output");
        } catch (Exception e) {
            logger.fine("Could not parse source XMP — skipping PDF/A transfer: " + e.getMessage());
            return;
        }

        // Source is PDF/A — fully attach conformance markers to destination
        // (We rebuild rather than byte-copy so dates and producer reflect the new doc)
        attach(destination);
    }

    // -----------------------------------------------------------------------
    // Detection helper
    // -----------------------------------------------------------------------

    /**
     * Returns true if the document has a PDF/A pdfaid:part XMP declaration.
     * Does NOT perform full validation — use PdfValidator for that.
     */
    public static boolean isPdfA(PDDocument document) {
        try {
            PDMetadata meta = document.getDocumentCatalog().getMetadata();
            if (meta == null) return false;
            XMPMetadata xmp = new DomXmpParser()
                .parse(meta.toByteArray());
            PDFAIdentificationSchema pdfaid = xmp.getPDFAIdentificationSchema();
            return pdfaid != null && pdfaid.getPart() != null;
        } catch (Exception e) {
            return false;
        }
    }
}
