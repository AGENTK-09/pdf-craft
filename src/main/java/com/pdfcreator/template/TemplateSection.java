package com.pdfcreator.template;

/**
 * Represents a single section within a PdfTemplate.
 *
 * Supported section types:
 *   heading     - Large bold text.
 *   subheading  - Medium bold text.
 *   body        - Regular paragraph text with word-wrap and multi-page flow.
 *   divider     - A horizontal rule.
 *   spacer      - Vertical whitespace.
 *   image       - An embedded image. content = file path (may be a {{placeholder}}).
 *
 * Image-specific optional attributes:
 *   align        - "left" | "center" | "right"  (default: left)
 *   widthPercent - integer 1-100, percentage of usable page width (default: 100)
 *
 * Example:
 *   { "type": "image", "content": "{{chart_path}}", "align": "center", "widthPercent": "60" }
 *   { "type": "image", "content": "assets/logo.png", "align": "right",  "widthPercent": "25" }
 */
public class TemplateSection {

    public enum Type {
        HEADING, SUBHEADING, BODY, DIVIDER, SPACER, IMAGE;

        public static Type fromString(String value) {
            if (value == null) throw new IllegalArgumentException("Section type cannot be null");
            return switch (value.trim().toUpperCase()) {
                case "HEADING"    -> HEADING;
                case "SUBHEADING" -> SUBHEADING;
                case "BODY"       -> BODY;
                case "DIVIDER"    -> DIVIDER;
                case "SPACER"     -> SPACER;
                case "IMAGE"      -> IMAGE;
                default -> throw new IllegalArgumentException(
                    "Unknown section type: '" + value + "'. " +
                    "Valid types: heading, subheading, body, divider, spacer, image");
            };
        }
    }

    public enum Align { LEFT, CENTER, RIGHT }

    private final Type   type;
    private final String content;
    private final Align  align;        // applies to IMAGE sections
    private final int    widthPercent; // applies to IMAGE sections (1–100)

    public TemplateSection(Type type, String content, Align align, int widthPercent) {
        this.type         = type;
        this.content      = content;
        this.align        = align;
        this.widthPercent = widthPercent;
    }

    // Convenience constructor — defaults for non-image sections
    public TemplateSection(Type type, String content) {
        this(type, content, Align.LEFT, 100);
    }

    public Type   getType()         { return type; }
    public String getContent()      { return content; }
    public Align  getAlign()        { return align; }
    public int    getWidthPercent() { return widthPercent; }

    /** Returns a copy with resolved content (used by PlaceholderResolver). */
    public TemplateSection withContent(String resolvedContent) {
        return new TemplateSection(this.type, resolvedContent, this.align, this.widthPercent);
    }

    @Override
    public String toString() {
        String preview = content != null && content.length() > 40
            ? content.substring(0, 40) + "..." : content;
        return String.format("TemplateSection[type=%s, align=%s, width=%d%%, content=%s]",
            type, align, widthPercent, preview);
    }
}
