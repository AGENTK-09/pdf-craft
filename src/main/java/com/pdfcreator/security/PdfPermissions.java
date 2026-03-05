package com.pdfcreator.security;

import org.apache.pdfbox.pdmodel.encryption.AccessPermission;

/**
 * Immutable representation of PDF access permissions.
 *
 * Maps one-to-one with the eight flag bits defined in the PDF specification
 * and exposed by PDFBox's AccessPermission class. Each flag controls what
 * an authenticated user (who opened the file with the user password) is
 * allowed to do. An owner (who opened with the owner password) always has
 * full access regardless of these flags.
 *
 * ── PDF PERMISSION FLAGS ──────────────────────────────────────────────────
 *
 *   print              Print the document at full resolution.
 *   printFaithful      Full-quality (faithful) printing allowed.
 *                     When false and print=true, only degraded (draft) print is permitted.
 *   copy               Copy text or graphics to the clipboard.
 *   modify             Modify the document content (beyond forms and annotations).
 *   modifyAnnotations  Add, modify, or delete annotations and form fields.
 *   fillForms          Fill in existing form fields (always implies modifyAnnotations).
 *   accessibility      Extract text for accessibility tools (screen readers).
 *                      Almost always left true; disabling it affects assistive tech.
 *   assemble           Insert, delete, or rotate pages; create bookmarks or thumbnails.
 *
 * ── PRESETS ───────────────────────────────────────────────────────────────
 *
 *   PdfPermissions.allAllowed()    — every flag true  (default for most encrypt use-cases)
 *   PdfPermissions.readOnly()      — only print + accessibility allowed
 *   PdfPermissions.printOnly()     — only print allowed (no copy, no accessibility for users)
 *   PdfPermissions.noCopy()        — all allowed except copy
 *
 * ── USAGE ─────────────────────────────────────────────────────────────────
 *
 *   // Preset
 *   PdfPermissions perms = PdfPermissions.readOnly();
 *
 *   // Custom
 *   PdfPermissions perms = new PdfPermissions.Builder()
 *       .allowPrint(true)
 *       .allowCopy(false)
 *       .allowModify(false)
 *       .allowFillForms(true)
 *       .allowAccessibility(true)
 *       .build();
 *
 *   // Convert to PDFBox object for doc.protect()
 *   AccessPermission ap = perms.toAccessPermission();
 */
public final class PdfPermissions {

    // -----------------------------------------------------------------------
    // Fields — one per PDF spec permission bit
    // -----------------------------------------------------------------------

    private final boolean print;
    private final boolean printFaithful;
    private final boolean copy;
    private final boolean modify;
    private final boolean modifyAnnotations;
    private final boolean fillForms;
    private final boolean accessibility;
    private final boolean assemble;

    private PdfPermissions(Builder b) {
        this.print              = b.print;
        this.printFaithful      = b.printFaithful;
        this.copy               = b.copy;
        this.modify             = b.modify;
        this.modifyAnnotations  = b.modifyAnnotations;
        this.fillForms          = b.fillForms;
        this.accessibility      = b.accessibility;
        this.assemble           = b.assemble;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public boolean canPrint()              { return print; }
    public boolean canPrintFaithful()      { return printFaithful; }
    public boolean canCopy()               { return copy; }
    public boolean canModify()             { return modify; }
    public boolean canModifyAnnotations()  { return modifyAnnotations; }
    public boolean canFillForms()          { return fillForms; }
    public boolean canAccessibility()      { return accessibility; }
    public boolean canAssemble()           { return assemble; }

    // -----------------------------------------------------------------------
    // PDFBox interop
    // -----------------------------------------------------------------------

    /**
     * Converts this PdfPermissions into a PDFBox AccessPermission object
     * ready to pass to StandardProtectionPolicy.
     *
     * PDFBox AccessPermission starts with all permissions set to true by
     * default, so we explicitly set every flag to match our model exactly.
     *
     * @return a fully-configured AccessPermission
     */
    public AccessPermission toAccessPermission() {
        AccessPermission ap = new AccessPermission();
        ap.setCanPrint(print);
        ap.setCanPrintFaithful(printFaithful);
        ap.setCanExtractContent(copy);
        ap.setCanModify(modify);
        ap.setCanModifyAnnotations(modifyAnnotations);
        ap.setCanFillInForm(fillForms);
        ap.setCanExtractForAccessibility(accessibility);
        ap.setCanAssembleDocument(assemble);
        return ap;
    }

    /**
     * Reads a PDFBox AccessPermission (e.g. from an already-encrypted document)
     * and returns an equivalent PdfPermissions.
     *
     * @param ap the AccessPermission from PDDocument.getCurrentAccessPermission()
     * @return PdfPermissions reflecting the document's current flags
     */
    public static PdfPermissions fromAccessPermission(AccessPermission ap) {
        return new Builder()
            .allowPrint(ap.canPrint())
            .allowPrintFaithful(ap.canPrintFaithful())
            .allowCopy(ap.canExtractContent())
            .allowModify(ap.canModify())
            .allowModifyAnnotations(ap.canModifyAnnotations())
            .allowFillForms(ap.canFillInForm())
            .allowAccessibility(ap.canExtractForAccessibility())
            .allowAssemble(ap.canAssembleDocument())
            .build();
    }

    // -----------------------------------------------------------------------
    // Presets
    // -----------------------------------------------------------------------

    /**
     * All permission flags set to true.
     * Use this when you only want password protection (gate on open) but
     * no restrictions once the user has the password.
     */
    public static PdfPermissions allAllowed() {
        return new Builder().build();  // Builder defaults are all true
    }

    /**
     * Read-only: print and accessibility allowed; copy, modify, assemble denied.
     * The most common restriction preset for distributing locked documents.
     */
    public static PdfPermissions readOnly() {
        return new Builder()
            .allowPrint(true)
            .allowPrintFaithful(true)
            .allowCopy(false)
            .allowModify(false)
            .allowModifyAnnotations(false)
            .allowFillForms(false)
            .allowAccessibility(true)
            .allowAssemble(false)
            .build();
    }

    /**
     * Print-only: only printing is allowed; no copy, no accessibility.
     * Useful for physical-output-only distribution of sensitive documents.
     */
    public static PdfPermissions printOnly() {
        return new Builder()
            .allowPrint(true)
            .allowPrintFaithful(false)
            .allowCopy(false)
            .allowModify(false)
            .allowModifyAnnotations(false)
            .allowFillForms(false)
            .allowAccessibility(false)
            .allowAssemble(false)
            .build();
    }

    /**
     * No-copy: all flags true except copy (canExtractContent = false).
     * Allows viewing, printing, and form-filling but blocks clipboard copy.
     */
    public static PdfPermissions noCopy() {
        return new Builder()
            .allowCopy(false)
            .build();
    }

    // -----------------------------------------------------------------------
    // Object overrides
    // -----------------------------------------------------------------------

    @Override
    public String toString() {
        return String.format(
            "PdfPermissions[print=%b, printDeg=%b, copy=%b, modify=%b, " +
            "annots=%b, fillForms=%b, access=%b, assemble=%b]",
            print, printFaithful, copy, modify,
            modifyAnnotations, fillForms, accessibility, assemble);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {

        // All flags default to true — matching PDF spec "unrestricted" state
        private boolean print              = true;
        private boolean printFaithful      = true;
        private boolean copy               = true;
        private boolean modify             = true;
        private boolean modifyAnnotations  = true;
        private boolean fillForms          = true;
        private boolean accessibility      = true;
        private boolean assemble           = true;

        public Builder allowPrint(boolean v)              { this.print              = v; return this; }
        public Builder allowPrintFaithful(boolean v)      { this.printFaithful      = v; return this; }
        public Builder allowCopy(boolean v)               { this.copy               = v; return this; }
        public Builder allowModify(boolean v)             { this.modify             = v; return this; }
        public Builder allowModifyAnnotations(boolean v)  { this.modifyAnnotations  = v; return this; }
        public Builder allowFillForms(boolean v)          { this.fillForms          = v; return this; }
        public Builder allowAccessibility(boolean v)      { this.accessibility      = v; return this; }
        public Builder allowAssemble(boolean v)           { this.assemble           = v; return this; }

        public PdfPermissions build() {
            return new PdfPermissions(this);
        }
    }
}
