package com.pdfcreator.extractor;

/**
 * Immutable model representing a single image extracted from a PDF page.
 *
 * Fields:
 *
 *   pageNumber    — 1-based page number the image was found on
 *   imageIndex    — 0-based index of this image within its page
 *                   (multiple images can exist on a single page)
 *   format        — output format: "jpg" or "png"
 *   width         — image width in pixels
 *   height        — image height in pixels
 *   sizeBytes     — size of the raw image byte array
 *   data          — raw image bytes (JPEG or PNG encoded)
 *   suggestedName — auto-generated filename: "page{N}-img{I}.{ext}"
 *                   e.g. "page3-img0.png"
 *
 * The data field contains the image already encoded in the target format.
 * Write it directly to a file:
 *
 *   Files.write(Path.of(image.getSuggestedName()), image.getData());
 *
 * Or use a custom path:
 *
 *   Files.write(Path.of(outputDir + "/" + image.getSuggestedName()), image.getData());
 */
public class ExtractedImage {

    private final int    pageNumber;
    private final int    imageIndex;
    private final String format;       // "jpg" or "png"
    private final int    width;
    private final int    height;
    private final int    sizeBytes;
    private final byte[] data;
    private final String suggestedName;

    private ExtractedImage(Builder b) {
        this.pageNumber    = b.pageNumber;
        this.imageIndex    = b.imageIndex;
        this.format        = b.format;
        this.width         = b.width;
        this.height        = b.height;
        this.data          = b.data;
        this.sizeBytes     = b.data != null ? b.data.length : 0;
        this.suggestedName = "page" + pageNumber + "-img" + imageIndex + "." + format;
    }

    /** 1-based page number this image was extracted from. */
    public int    getPageNumber()    { return pageNumber; }

    /** 0-based index within the page (for pages with multiple images). */
    public int    getImageIndex()    { return imageIndex; }

    /** Output format: "jpg" or "png". */
    public String getFormat()        { return format; }

    /** Image width in pixels. */
    public int    getWidth()         { return width; }

    /** Image height in pixels. */
    public int    getHeight()        { return height; }

    /** Size of the raw byte array in bytes. */
    public int    getSizeBytes()     { return sizeBytes; }

    /** Raw encoded image bytes (ready to write to a file). */
    public byte[] getData()          { return data; }

    /**
     * Auto-generated filename in the form "page{N}-img{I}.{ext}".
     * Use as the filename when saving to a directory.
     */
    public String getSuggestedName() { return suggestedName; }

    @Override
    public String toString() {
        return String.format(
            "ExtractedImage[page=%d, index=%d, %dx%d, format=%s, size=%d bytes, name=%s]",
            pageNumber, imageIndex, width, height, format, sizeBytes, suggestedName);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private int    pageNumber;
        private int    imageIndex;
        private String format   = "png";
        private int    width;
        private int    height;
        private byte[] data;

        public Builder pageNumber(int v)  { this.pageNumber = v; return this; }
        public Builder imageIndex(int v)  { this.imageIndex = v; return this; }
        public Builder format(String v)   { this.format     = v; return this; }
        public Builder width(int v)       { this.width      = v; return this; }
        public Builder height(int v)      { this.height     = v; return this; }
        public Builder data(byte[] v)     { this.data       = v; return this; }

        public ExtractedImage build() { return new ExtractedImage(this); }
    }
}
