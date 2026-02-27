package com.pdfcreator.generator;

import com.pdfcreator.config.PdfConfig;
import com.pdfcreator.template.PageHeader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Tracks the rendering cursor and manages page creation for multi-page documents.
 *
 * Responsibilities:
 *   - Holds the current Y position (cursor) as content is written
 *   - Detects when the cursor drops below the bottom margin
 *   - Opens a new page and resets the cursor automatically
 *   - Applies the background color to each new page
 *
 * Usage:
 *   PageContext ctx = new PageContext(document, config);
 *   ctx.open();
 *   // ... write content using ctx.getContentStream() and ctx.getYPos() ...
 *   ctx.advanceY(lineHeight);   // moves cursor down; triggers new page if needed
 *   ctx.close();
 */

/**
 * Tracks the rendering cursor and manages page creation for multi-page documents.
 *
 * New vs previous version:
 *   - Accepts an optional PageHeader drawn automatically on each new page.
 *   - Header band is drawn before background fill so the band color shows.
 *   - Cursor starts below the header band height on each page.
 */
public class PageContext {

    private static final Logger logger = Logger.getLogger(PageContext.class.getName());

    private final PDDocument    document;
    private final PdfConfig     config;
    private final PDRectangle   pageSize;
    private final Color         backgroundColor;
    private final PageHeader    header;          // may be null

    private PDPage              currentPage;
    private PDPageContentStream contentStream;
    private float               yPos;
    private int                 pageNumber = 0;

    /** Constructor without header — backwards compatible. */
    public PageContext(PDDocument document, PdfConfig config, PDRectangle pageSize) {
        this(document, config, pageSize, null);
    }

    /** Constructor with optional branding header. */
    public PageContext(PDDocument document, PdfConfig config,
                       PDRectangle pageSize, PageHeader header) {
        this.document        = document;
        this.config          = config;
        this.pageSize        = pageSize;
        this.header          = header;
        this.backgroundColor = (config.getBackgroundColor() != null && !config.getBackgroundColor().isBlank())
            ? ColorUtil.fromHex(config.getBackgroundColor(), null)
            : null;
    }

    public void open()  throws IOException { newPage(); }

    public void close() throws IOException {
        if (contentStream != null) { contentStream.close(); contentStream = null; }
    }

    public PDPageContentStream getContentStream() { return contentStream; }
    public float  getYPos()         { return yPos; }
    public void   setYPos(float y)  { this.yPos = y; }
    public float  getUsableWidth()  { return pageSize.getWidth() - config.getMarginLeft() - config.getMarginRight(); }
    public float  getPageWidth()    { return pageSize.getWidth(); }
    public float  getPageHeight()   { return pageSize.getHeight(); }
    public int    getPageNumber()   { return pageNumber; }

    public void advanceY(float amount) throws IOException {
        yPos -= amount;
        if (yPos < config.getMarginBottom()) newPage();
    }

    public boolean wouldOverflow(float height) {
        return (yPos - height) < config.getMarginBottom();
    }

    // -----------------------------------------------------------------------
    // Private — page creation
    // -----------------------------------------------------------------------

    private void newPage() throws IOException {
        if (contentStream != null) contentStream.close();

        currentPage = new PDPage(pageSize);
        document.addPage(currentPage);
        pageNumber++;

        contentStream = new PDPageContentStream(document, currentPage);

        // 1. Background fill (full page)
        if (backgroundColor != null) {
            contentStream.setNonStrokingColor(backgroundColor);
            contentStream.addRect(0, 0, pageSize.getWidth(), pageSize.getHeight());
            contentStream.fill();
        }

        // 2. Branding header (drawn on every page)
        float headerHeight = 0f;
        if (header != null) {
            headerHeight = drawHeader();
        }

        // 3. Cursor starts below header (or just below top margin if no header)
        yPos = pageSize.getHeight() - headerHeight - config.getMarginTop();
    }

    /**
     * Draws the branding header band and logo.
     * Returns the total height consumed so the cursor can be set correctly.
     */
    private float drawHeader() throws IOException {
        float pageWidth   = pageSize.getWidth();
        float bandHeight  = header.getBandHeight();
        float bandY       = pageSize.getHeight() - bandHeight;

        // Draw the colored band across the full page width
        if (header.hasBand()) {
            Color bandColor = ColorUtil.fromHex(header.getBandColor(), Color.DARK_GRAY);
            contentStream.setNonStrokingColor(bandColor);
            contentStream.addRect(0, bandY, pageWidth, bandHeight);
            contentStream.fill();
        }

        // Draw the logo inside the band
        if (header.hasLogo()) {
            File logoFile = new File(header.getLogoPath());
            if (!logoFile.exists()) {
                logger.warning("Header logo not found, skipping: " + header.getLogoPath());
            } else {
                PDImageXObject logo = PDImageXObject.createFromFile(header.getLogoPath(), document);

                float usableWidth = pageWidth - config.getMarginLeft() - config.getMarginRight();
                float maxLogoWidth  = usableWidth * header.getLogoWidthPercent() / 100f;
                float maxLogoHeight = bandHeight - 10f; // 5pt padding top and bottom

                // Scale to fit within the allocated width and the band height
                float scaleW = maxLogoWidth  / logo.getWidth();
                float scaleH = maxLogoHeight / logo.getHeight();
                float scale  = Math.min(scaleW, scaleH);

                float logoW = logo.getWidth()  * scale;
                float logoH = logo.getHeight() * scale;
                float logoY = bandY + (bandHeight - logoH) / 2f; // vertically centered in band

                float logoX = switch (header.getLogoAlign()) {
                    case LEFT   -> config.getMarginLeft();
                    case RIGHT  -> pageWidth - config.getMarginRight() - logoW;
                    case CENTER -> (pageWidth - logoW) / 2f;
                };

                contentStream.drawImage(logo, logoX, logoY, logoW, logoH);
                logger.fine(String.format("Drew header logo (%.0fx%.0f) at (%.0f, %.0f)", logoW, logoH, logoX, logoY));
            }
        }

        return bandHeight + 8f; // 8pt gap below band before content starts
    }
}
