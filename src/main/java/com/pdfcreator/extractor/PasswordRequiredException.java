package com.pdfcreator.extractor;

import java.io.IOException;

/**
 * Thrown when a PDF requires a password and either none was provided or the
 * provided password is incorrect.
 *
 * Extends IOException so it fits naturally into the existing throws signatures
 * without requiring callers to change their exception handling, while still
 * allowing callers who care to catch it specifically:
 *
 *   try {
 *       extractor.extract("protected.pdf");
 *   } catch (PasswordRequiredException e) {
 *       System.err.println("Password required: " + e.getMessage());
 *       // prompt user for password and retry
 *   } catch (IOException e) {
 *       System.err.println("Other read error: " + e.getMessage());
 *   }
 *
 * PDFBox 3.x signals an encrypted PDF with either:
 *   - org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
 *     (wrong password supplied)
 *   - A document that loads but has isEncrypted() == true and
 *     getCurrentAccessPermission().canExtractContent() == false
 *     (opened with empty-string owner password but content locked)
 *
 * Both cases are caught in the extractor load helpers and re-thrown as this class.
 */
public class PasswordRequiredException extends IOException {

    private final String pdfPath;
    private final boolean passwordProvided;

    /**
     * @param pdfPath          path to the PDF that required a password
     * @param passwordProvided true if a password was supplied but was wrong;
     *                         false if no password was supplied at all
     */
    public PasswordRequiredException(String pdfPath, boolean passwordProvided) {
        super(passwordProvided
            ? "Incorrect password for PDF: " + pdfPath
            : "PDF is password-protected and no password was supplied: " + pdfPath);
        this.pdfPath           = pdfPath;
        this.passwordProvided  = passwordProvided;
    }

    public PasswordRequiredException(String pdfPath, boolean passwordProvided, Throwable cause) {
        super(passwordProvided
            ? "Incorrect password for PDF: " + pdfPath
            : "PDF is password-protected and no password was supplied: " + pdfPath,
            cause);
        this.pdfPath           = pdfPath;
        this.passwordProvided  = passwordProvided;
    }

    /** Path of the PDF that triggered this exception. */
    public String getPdfPath() { return pdfPath; }

    /** True if a password was supplied but rejected; false if no password was given. */
    public boolean wasPasswordProvided() { return passwordProvided; }
}
