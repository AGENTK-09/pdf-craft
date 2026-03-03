package com.pdfcreator.template;

import java.util.List;

/**
 * Defines a two-column side-by-side layout block.
 *
 * Used for layouts like:
 *   ┌─────────────────────┬──────────────────────┐
 *   │ Customer Name        │ Account: 12345678    │
 *   │ 42 Any Street        │ Sort:    00-00-00    │
 *   │ London EC1A 1BB      │ Date:    23 Feb 2026 │
 *   └─────────────────────┴──────────────────────┘
 *
 * Each column is itself a list of TemplateSection objects — any section type
 * except another COLUMNS (no nesting). Both columns render at the same Y start
 * position. The cursor advances past whichever column was taller.
 *
 * JSON representation:
 * {
 *   "type":        "columns",
 *   "leftWidth":   50,         // % of usable width (right gets the remainder)
 *   "left":  [ { "type": "body", "content": "{{customer_name}}" }, ... ],
 *   "right": [ { "type": "body", "content": "Account: {{account_no}}" }, ... ]
 * }
 *
 * This class is a sibling to TemplateSection (not a subclass) because it carries
 * a fundamentally different structure. TemplateSection.Type.COLUMNS exists as the
 * marker type; ColumnsSection is the rich companion object stored alongside.
 *
 * In PdfTemplate, sections of type COLUMNS carry a reference to their ColumnsSection
 * via TemplateSection.columnsData (see TemplateSection.Builder.columnsData()).
 */
public class ColumnsSection {

    private final int                   leftWidthPct;  // % of usable width for left column
    private final List<TemplateSection> left;
    private final List<TemplateSection> right;

    public ColumnsSection(int leftWidthPct,
                          List<TemplateSection> left,
                          List<TemplateSection> right) {
        this.leftWidthPct = Math.max(10, Math.min(90, leftWidthPct));
        this.left         = left  != null ? List.copyOf(left)  : List.of();
        this.right        = right != null ? List.copyOf(right) : List.of();
    }

    public int                   getLeftWidthPct()  { return leftWidthPct; }
    public int                   getRightWidthPct() { return 100 - leftWidthPct; }
    public List<TemplateSection> getLeft()          { return left; }
    public List<TemplateSection> getRight()         { return right; }

    @Override
    public String toString() {
        return String.format("ColumnsSection[left=%d%%, sections(left=%d, right=%d)]",
            leftWidthPct, left.size(), right.size());
    }
}
