package com.pdfcreator.security;

import com.pdfcreator.extractor.PasswordRequiredException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.PDEncryption;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Core engine for PDF encryption, decryption, password management,
 * and permission updates using AES-256 (PDF 2.0).
 *
 * Thread-safe — no mutable state is held between calls. Each operation opens
 * its own PDDocument, applies changes, saves, and closes.
 *
 * ── AES-256 REQUIREMENT ───────────────────────────────────────────────────
 *
 * AES-256 encryption in PDFBox 3.x requires BouncyCastle on the classpath.
 * Without it, PDFBox silently falls back to a weaker algorithm or throws a
 * runtime error depending on the JVM. The pom.xml includes:
 *
 *   org.bouncycastle:bcprov-jdk18on:1.78.1
 *
 * ── PDFBox ENCRYPTION API ─────────────────────────────────────────────────
 *
 *   StandardProtectionPolicy  — encapsulates owner password, user password,
 *                               AccessPermission flags, and key length.
 *
 *   policy.setEncryptionKeyLength(256)  — selects AES-256.
 *   policy.setPreferAES(true)           — required; without this PDFBox uses RC4
 *                                         even when key length is 256.
 *
 *   PDDocument.protect(policy)          — applies the policy to the in-memory
 *                                         document. The encryption is written
 *                                         to disk on the next save().
 *
 *   PDDocument.setAllSecurityToBeRemoved(true)  — signals PDFBox to strip all
 *                                                   encryption on the next save().
 *
 * ── TWO-PASSWORD MODEL ────────────────────────────────────────────────────
 *
 *   Owner password  — full access; used to open the document for modification.
 *                     Required for decrypt, change-password, update-permissions.
 *
 *   User password   — restricted access subject to AccessPermission flags.
 *                     May be "" (empty) — the document opens without prompting
 *                     but permission flags are still enforced.
 *
 * ── OPERATION OVERVIEW ────────────────────────────────────────────────────
 *
 *   encrypt()            — load plain or encrypted PDF, apply AES-256 policy,
 *                          save encrypted output.
 *
 *   decrypt()            — load encrypted PDF with owner password,
 *                          setAllSecurityToBeRemoved(true), save plain output.
 *
 *   changePassword()     — load with current owner password, re-protect with
 *                          new passwords (preserving existing permission flags),
 *                          save encrypted output.
 *
 *   updatePermissions()  — load with owner password, re-protect with same
 *                          passwords but new AccessPermission flags, save output.
 *
 *   inspect()            — load (optionally with password), read encryption
 *                          status and permission flags, return without saving.
 *
 * Usage:
 *
 *   PdfSecurityManager mgr = new PdfSecurityManager();
 *
 *   // Encrypt
 *   EncryptionOptions opts = new EncryptionOptions.Builder(Operation.ENCRYPT)
 *       .inputPath("report.pdf")
 *       .outputPath("report-enc.pdf")
 *       .ownerPassword("ownerSecret")
 *       .userPassword("")
 *       .permissions(PdfPermissions.readOnly())
 *       .build();
 *   SecurityResult result = mgr.execute(opts);
 *   System.out.println(result.getSummary());
 */
public class PdfSecurityManager {

    private static final Logger logger = Logger.getLogger(PdfSecurityManager.class.getName());

    /** AES-256 key length constant for PDFBox StandardProtectionPolicy. */
    private static final int AES_256_KEY_LENGTH = 256;

    // -----------------------------------------------------------------------
    // Main dispatcher
    // -----------------------------------------------------------------------

    /**
     * Executes the security operation described by the given EncryptionOptions.
     *
     * @param opts fully-configured EncryptionOptions
     * @return SecurityResult describing what was done and the resulting state
     * @throws IOException               on file read/write errors
     * @throws PasswordRequiredException if the supplied password is wrong or missing
     */
    public SecurityResult execute(EncryptionOptions opts) throws IOException {
        logger.info("Executing security operation: " + opts);
        return switch (opts.getOperation()) {
            case ENCRYPT            -> encrypt(opts);
            case DECRYPT            -> decrypt(opts);
            case CHANGE_PASSWORD    -> changePassword(opts);
            case UPDATE_PERMISSIONS -> updatePermissions(opts);
            case INSPECT            -> inspect(opts);
        };
    }

    // -----------------------------------------------------------------------
    // ENCRYPT
    // -----------------------------------------------------------------------

    /**
     * Applies AES-256 encryption to a PDF.
     *
     * The source PDF may itself be already encrypted — if so, ownerPassword
     * is used to open it, and the result is re-encrypted with the new policy.
     * This allows changing the algorithm of an existing file.
     *
     * Steps:
     *   1. Load source with ownerPassword (or no password for plain PDFs)
     *   2. Build StandardProtectionPolicy with AES-256 + permission flags
     *   3. Call doc.protect(policy)
     *   4. Save to outputPath
     *
     * @throws PasswordRequiredException if the source is encrypted and the
     *         supplied owner password is wrong or missing
     */
    private SecurityResult encrypt(EncryptionOptions opts) throws IOException {
        File inputFile  = requireFile(opts.getInputPath());
        File outputFile = new File(opts.getOutputPath());
        ensureParentDir(outputFile);

        try (PDDocument doc = loadWithOwnerPassword(
                inputFile, opts.getInputPath(), opts.getOwnerPassword())) {

            // PDF/A-1b (ISO 19005-1 §6.1.3) forbids encryption.
            // Encrypting a PDF/A document makes it non-conformant.
            if (com.pdfcreator.pdfa.PdfACompliance.isPdfA(doc)) {
                logger.warning("Encrypting a PDF/A document will make it non-conformant with ISO 19005-1. " +
                    "The output will no longer pass PDF/A validation.");
                System.err.println("Warning: " + opts.getInputPath() + " is a PDF/A document. " +
                    "Encryption is forbidden by ISO 19005-1 — the output will NOT be PDF/A compliant.");
            }

            // Build the protection policy
            AccessPermission ap     = opts.getPermissions().toAccessPermission();
            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                opts.getOwnerPassword(),
                opts.getUserPassword() != null ? opts.getUserPassword() : "",
                ap);

            policy.setEncryptionKeyLength(AES_256_KEY_LENGTH);
            policy.setPreferAES(true);   // REQUIRED — without this PDFBox uses RC4

            doc.protect(policy);
            doc.save(outputFile);

            logger.info("Encrypted → " + opts.getOutputPath());

            return new SecurityResult.Builder()
                .operation(EncryptionOptions.Operation.ENCRYPT)
                .inputPath(opts.getInputPath())
                .outputPath(opts.getOutputPath())
                .encrypted(true)
                .algorithm("AES-256")
                .keyLengthBits(AES_256_KEY_LENGTH)
                .permissions(opts.getPermissions())
                .outputSizeBytes(outputFile.length())
                .build();
        }
    }

    // -----------------------------------------------------------------------
    // DECRYPT
    // -----------------------------------------------------------------------

    /**
     * Removes all encryption from a PDF.
     *
     * Requires the owner password — not the user password. The owner password
     * grants the right to modify security settings, including removing them.
     *
     * Steps:
     *   1. Load with ownerPassword
     *   2. Call doc.setAllSecurityToBeRemoved(true)
     *   3. Save to outputPath — PDFBox writes a plain, unencrypted PDF
     *
     * @throws PasswordRequiredException if the owner password is wrong
     */
    private SecurityResult decrypt(EncryptionOptions opts) throws IOException {
        File inputFile  = requireFile(opts.getInputPath());
        File outputFile = new File(opts.getOutputPath());
        ensureParentDir(outputFile);

        if (!isEncrypted(inputFile))
            throw new IOException(
                "Cannot decrypt: the PDF is not encrypted: " + opts.getInputPath());

        try (PDDocument doc = loadWithOwnerPassword(
                inputFile, opts.getInputPath(), opts.getOwnerPassword())) {

            doc.setAllSecurityToBeRemoved(true);
            doc.save(outputFile);

            logger.info("Decrypted → " + opts.getOutputPath());

            return new SecurityResult.Builder()
                .operation(EncryptionOptions.Operation.DECRYPT)
                .inputPath(opts.getInputPath())
                .outputPath(opts.getOutputPath())
                .encrypted(false)
                .algorithm("none")
                .keyLengthBits(0)
                .permissions(null)
                .outputSizeBytes(outputFile.length())
                .build();
        }
    }

    // -----------------------------------------------------------------------
    // CHANGE PASSWORD
    // -----------------------------------------------------------------------

    /**
     * Updates the owner password, user password, or both on an encrypted PDF.
     *
     * Permission flags are read from the existing document and preserved
     * exactly as-is — this operation only changes credentials.
     *
     * Steps:
     *   1. Load with current ownerPassword
     *   2. Read current AccessPermission flags from the open document
     *   3. Build new policy with newOwnerPassword / newUserPassword
     *      (falling back to the current password if new one is null)
     *   4. Apply AES-256 policy, save to outputPath
     *
     * Note: PDFBox does not expose the current user password in plaintext
     * (it is stored hashed). When newUserPassword is null, we re-use the
     * string from ownerPassword as a conservative default. Callers who want
     * to preserve the exact user password must supply it explicitly.
     *
     * @throws PasswordRequiredException if the current owner password is wrong
     */
    private SecurityResult changePassword(EncryptionOptions opts) throws IOException {
        File inputFile  = requireFile(opts.getInputPath());
        File outputFile = new File(opts.getOutputPath());
        ensureParentDir(outputFile);

        if (!isEncrypted(inputFile))
            throw new IOException(
                "Cannot change password: the PDF is not encrypted: " + opts.getInputPath());

        try (PDDocument doc = loadWithOwnerPassword(
                inputFile, opts.getInputPath(), opts.getOwnerPassword())) {

            // Preserve existing permission flags.
            // Must read from enc.getPermissions() (the stored bitmask), NOT
            // getCurrentAccessPermission() — the latter returns all-true when
            // opened as owner, losing the actual stored restrictions.
            PDEncryption existingEnc = doc.getEncryption();
            AccessPermission existingAp = existingEnc != null
                ? new AccessPermission(existingEnc.getPermissions())
                : new AccessPermission();
            PdfPermissions existingPerms = PdfPermissions.fromAccessPermission(existingAp);

            // Use new passwords where supplied, fall back to current otherwise
            String newOwner = opts.getNewOwnerPassword() != null
                ? opts.getNewOwnerPassword() : opts.getOwnerPassword();
            String newUser  = opts.getNewUserPassword()  != null
                ? opts.getNewUserPassword()  : "";

            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                newOwner, newUser, existingPerms.toAccessPermission());
            policy.setEncryptionKeyLength(AES_256_KEY_LENGTH);
            policy.setPreferAES(true);

            // Clear the old security handler before applying the new policy
            doc.setAllSecurityToBeRemoved(true);
            doc.protect(policy);
            doc.save(outputFile);

            logger.info("Password changed → " + opts.getOutputPath());

            return new SecurityResult.Builder()
                .operation(EncryptionOptions.Operation.CHANGE_PASSWORD)
                .inputPath(opts.getInputPath())
                .outputPath(opts.getOutputPath())
                .encrypted(true)
                .algorithm("AES-256")
                .keyLengthBits(AES_256_KEY_LENGTH)
                .permissions(existingPerms)
                .outputSizeBytes(outputFile.length())
                .build();
        }
    }

    // -----------------------------------------------------------------------
    // UPDATE PERMISSIONS
    // -----------------------------------------------------------------------

    /**
     * Changes the permission flags on an encrypted PDF while keeping the
     * existing passwords.
     *
     * Because PDFBox does not expose the stored user-password hash in
     * plaintext, the only way to re-encrypt with modified flags is to
     * protect() again. We use the supplied ownerPassword as both owner and
     * user passwords in the new policy, then set the user password to ""
     * unless opts.getUserPassword() is explicitly provided.
     *
     * For full control over both passwords during a permission update,
     * callers can use CHANGE_PASSWORD + UPDATE_PERMISSIONS by running two
     * sequential operations, or use ENCRYPT with the known credentials.
     *
     * Steps:
     *   1. Load with ownerPassword
     *   2. Apply new AccessPermission flags via StandardProtectionPolicy
     *   3. Re-encrypt AES-256, save to outputPath
     *
     * @throws PasswordRequiredException if the owner password is wrong
     */
    private SecurityResult updatePermissions(EncryptionOptions opts) throws IOException {
        File inputFile  = requireFile(opts.getInputPath());
        File outputFile = new File(opts.getOutputPath());
        ensureParentDir(outputFile);

        if (!isEncrypted(inputFile))
            throw new IOException(
                "Cannot update permissions: the PDF is not encrypted: " + opts.getInputPath());

        try (PDDocument doc = loadWithOwnerPassword(
                inputFile, opts.getInputPath(), opts.getOwnerPassword())) {

            AccessPermission ap = opts.getPermissions().toAccessPermission();

            // Keep the same owner password; user password defaults to ""
            // unless caller explicitly supplied one via opts.getUserPassword()
            String userPwd = opts.getUserPassword() != null ? opts.getUserPassword() : "";

            // ── IMPORTANT: strip the existing security handler first ──────
            //
            // Calling protect() on a document that is still in a "decrypted
            // but security-handler-attached" state can leave stale permission
            // data from the old handler in memory. setAllSecurityToBeRemoved(true)
            // clears the old handler so protect() starts from a clean slate.
            // This is the same pattern PDFBox uses internally when re-encrypting.
            doc.setAllSecurityToBeRemoved(true);

            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                opts.getOwnerPassword(), userPwd, ap);
            policy.setEncryptionKeyLength(AES_256_KEY_LENGTH);
            policy.setPreferAES(true);

            doc.protect(policy);
            doc.save(outputFile);

            logger.info("Permissions updated → " + opts.getOutputPath());

            return new SecurityResult.Builder()
                .operation(EncryptionOptions.Operation.UPDATE_PERMISSIONS)
                .inputPath(opts.getInputPath())
                .outputPath(opts.getOutputPath())
                .encrypted(true)
                .algorithm("AES-256")
                .keyLengthBits(AES_256_KEY_LENGTH)
                .permissions(opts.getPermissions())
                .outputSizeBytes(outputFile.length())
                .build();
        }
    }

    // -----------------------------------------------------------------------
    // INSPECT
    // -----------------------------------------------------------------------

    /**
     * Reads the encryption status and permission flags of a PDF without
     * modifying it.
     *
     * ── FIX: inspect no longer requires a password for PDFs encrypted with
     * an empty user password (the default when only --owner-password is set).
     *
     * The previous implementation called loadWithOwnerPassword() which threw
     * PasswordRequiredException whenever no password was supplied and the file
     * was encrypted — even when PDFBox could open it with the empty string.
     * This made --inspect-security unusable without --owner-password for the
     * most common encrypt use-case (empty user password, custom flags).
     *
     * The fix uses Loader.loadPDF(file, "") — PDFBox opens files that have an
     * empty user password without prompting. The PDEncryption dictionary is
     * readable regardless of which password was used to open the file.
     * The stored permission bitmask is read from enc.getPermissions() (not
     * getCurrentAccessPermission()) so the real on-disk flags are always
     * reported accurately, whether opened as user or owner.
     *
     * Behaviour matrix after fix:
     *   Encrypted, empty user pwd, no --owner-password  → OK, shows flags + note
     *   Encrypted, empty user pwd, --owner-password     → OK, shows flags (no note)
     *   Encrypted, non-empty user pwd, no password      → PasswordRequiredException (correct)
     *   Encrypted, non-empty user pwd, --owner-password → OK, shows flags
     *   Plain PDF, no password                          → OK, unrestricted
     *
     * No output file is written.
     *
     * @param opts EncryptionOptions with at least inputPath set
     * @return SecurityResult with encryption and permission details
     */
    private SecurityResult inspect(EncryptionOptions opts) throws IOException {
        File   inputFile = requireFile(opts.getInputPath());
        String pwd       = opts.getOwnerPassword();  // may be null

        // Use the supplied password, or "" to attempt opening without prompting.
        // PDFBox opens files whose user-password is "" with the empty string.
        // For plain PDFs the empty string is also accepted.
        String loadPwd = (pwd != null && !pwd.isBlank()) ? pwd : "";

        try (PDDocument doc = Loader.loadPDF(inputFile, loadPwd)) {

            if (!doc.isEncrypted()) {
                // Plain PDF — no encryption at all
                return new SecurityResult.Builder()
                    .operation(EncryptionOptions.Operation.INSPECT)
                    .inputPath(opts.getInputPath())
                    .encrypted(false)
                    .algorithm("none")
                    .keyLengthBits(0)
                    .permissions(PdfPermissions.allAllowed())
                    .build();
            }

            PDEncryption enc  = doc.getEncryption();
            String       algo = resolveAlgorithm(enc);
            int          keyBits = enc != null ? enc.getLength() * 8 : 0;

            // ── IMPORTANT: read stored flags from the encryption dictionary ──
            //
            // doc.getCurrentAccessPermission() returns what the CURRENT SESSION
            // is allowed to do — all-true when opened as owner, restricted when
            // opened as user. Neither reflects the real stored flags reliably.
            //
            // The stored flags live in enc.getPermissions() as a raw integer
            // bitmask (PDF spec table 22). Constructing AccessPermission from
            // that integer always gives the real, on-disk flags regardless of
            // how the document was opened.
            PdfPermissions perms;
            if (enc != null) {
                AccessPermission storedAp = new AccessPermission(enc.getPermissions());
                perms = PdfPermissions.fromAccessPermission(storedAp);
            } else {
                perms = PdfPermissions.allAllowed();
            }

            // If no owner password was supplied, note that flags are from the
            // stored dictionary — accurate, but unconfirmed at owner level.
            if (pwd == null || pwd.isBlank()) {
                System.err.println("Note: --owner-password not supplied. " +
                    "Permission flags are read from the stored encryption dictionary " +
                    "and are accurate. Supply --owner-password to confirm owner-level access.");
            }

            logger.info("Inspected: " + opts.getInputPath() + " — " + algo);

            return new SecurityResult.Builder()
                .operation(EncryptionOptions.Operation.INSPECT)
                .inputPath(opts.getInputPath())
                .encrypted(true)
                .algorithm(algo)
                .keyLengthBits(keyBits)
                .permissions(perms)
                .build();

        } catch (InvalidPasswordException e) {
            // Only reaches here when the file has a non-empty user password
            // AND no password (or the wrong password) was supplied.
            // This is genuinely correct — the file is locked to all access.
            throw new com.pdfcreator.extractor.PasswordRequiredException(
                opts.getInputPath(), pwd != null && !pwd.isBlank(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Loads a PDF using the owner password for full access.
     *
     * PDFBox 3.x tries the supplied string as the user password first, then
     * as the owner password. For operations that require owner-level access
     * (decrypt, change password, update permissions) the caller must supply
     * the owner password — supplying only the user password will open the
     * document but with restricted AccessPermission flags.
     *
     * @throws PasswordRequiredException if the password is wrong or missing
     */
    private static PDDocument loadWithOwnerPassword(File file, String path,
                                                     String password) throws IOException {
        try {
            if (password != null && !password.isBlank()) {
                return Loader.loadPDF(file, password);
            } else {
                // No password supplied — try opening without one (works for plain PDFs)
                PDDocument doc = Loader.loadPDF(file);
                if (doc.isEncrypted()) {
                    doc.close();
                    throw new PasswordRequiredException(path, false);
                }
                return doc;
            }
        } catch (InvalidPasswordException e) {
            throw new PasswordRequiredException(path, password != null && !password.isBlank(), e);
        }
    }

    /**
     * Checks whether a PDF file is encrypted by loading it with an empty
     * password and inspecting isEncrypted(). Does not throw for wrong passwords —
     * returns true if the file is encrypted, false otherwise.
     */
    private static boolean isEncrypted(File file) {
        // PDFBox opens many encrypted PDFs with an empty-string "password".
        // We use this to peek at isEncrypted() without needing the real password.
        try (PDDocument doc = Loader.loadPDF(file, "")) {
            return doc.isEncrypted();
        } catch (InvalidPasswordException e) {
            // An InvalidPasswordException on empty password means the file IS encrypted
            // and the empty password is not accepted as even the user password.
            return true;
        } catch (IOException e) {
            return false;   // corrupted / not a PDF — let the caller handle it
        }
    }

    /**
     * Resolves a human-readable algorithm name from PDFBox's PDEncryption.
     *
     * PDFBox stores the algorithm as an integer (V value in PDF spec):
     *   V=1 — RC4-40
     *   V=2 — RC4-128 (or AES-128 with AES flag)
     *   V=4 — AES-128 (PDF 1.6)
     *   V=5 — AES-256 (PDF 2.0)
     *
     * PDFBox 3.x PDEncryption does not expose a direct "algorithm name" getter,
     * so we derive it from the V value and key length.
     */
    private static String resolveAlgorithm(PDEncryption enc) {
        if (enc == null) return "none";
        int v          = enc.getVersion();
        int keyBits    = enc.getLength() * 8;

        return switch (v) {
            case 1  -> "RC4-40";
            case 2  -> keyBits >= 128 ? "RC4-128" : "RC4-" + keyBits;
            case 4  -> "AES-128";
            case 5  -> "AES-256";
            default -> "AES-" + keyBits + " (V=" + v + ")";
        };
    }

    /**
     * Verifies the input file exists and returns it as a File.
     * Throws IOException with a clear message if not found.
     */
    private static File requireFile(String path) throws IOException {
        File f = new File(path);
        if (!f.exists() || !f.isFile())
            throw new IOException("PDF file not found: " + path);
        return f;
    }

    /**
     * Creates parent directories for the given output file if they do not
     * already exist.
     */
    private static void ensureParentDir(File outputFile) throws IOException {
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IOException("Could not create output directory: " + parent);
    }
}
