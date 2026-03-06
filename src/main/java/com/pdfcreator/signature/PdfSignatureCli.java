package com.pdfcreator.signature;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * CLI handler for PDF digital signature operations.
 *
 * Called from PdfCreator.main() when any of the following flags are present:
 *   --sign, --verify, --list-signatures, --export-cert
 *
 * ── SIGN ──────────────────────────────────────────────────────────────────
 *
 *   --sign
 *   --input <path>              Source PDF (required)
 *   --output <path>             Signed output PDF (required)
 *   --keystore <path>           PKCS12 (.p12) or JKS keystore (required)
 *   --keystore-password <pwd>   Keystore password (required)
 *   --keystore-type <type>      PKCS12 (default) or JKS
 *   --alias <n>              Key alias in keystore (default: auto-detect first)
 *   --reason <text>             Reason for signing (optional)
 *   --location <text>           Signing location (optional)
 *   --contact <email>           Contact information (optional)
 *   --signer-name <n>        Override display name (default: cert CN)
 *   --tsa-url <url>             RFC 3161 TSA endpoint for trusted timestamp
 *                               e.g. http://timestamp.digicert.com
 *   --visible                   Render a visible signature box on the page
 *   --sig-page <n>              Page for visible sig box, 1-based (default: last)
 *   --sig-x <pts>               Box left edge in PDF points (default: 50)
 *   --sig-y <pts>               Box bottom edge in PDF points (default: 50)
 *   --sig-width <pts>           Box width in PDF points (default: 200)
 *   --sig-height <pts>          Box height in PDF points (default: 60)
 *
 *   Examples:
 *
 *   # Invisible signature — server-side batch
 *   java -jar pdf-creator.jar --sign \
 *     --input statement.pdf --output statement-signed.pdf \
 *     --keystore bank.p12 --keystore-password secret \
 *     --reason "Monthly Account Statement" --location "Mumbai, India"
 *
 *   # With trusted timestamp (recommended for customer-facing documents)
 *   java -jar pdf-creator.jar --sign \
 *     --input statement.pdf --output statement-signed.pdf \
 *     --keystore bank.p12 --keystore-password secret \
 *     --reason "Monthly Account Statement" \
 *     --tsa-url http://timestamp.digicert.com
 *
 *   # Visible signature box on page 1
 *   java -jar pdf-creator.jar --sign \
 *     --input contract.pdf --output contract-signed.pdf \
 *     --keystore signing.p12 --keystore-password secret \
 *     --visible --sig-page 1 --sig-x 50 --sig-y 50 \
 *     --sig-width 200 --sig-height 60
 *
 * ── VERIFY ────────────────────────────────────────────────────────────────
 *
 *   --verify
 *   --input <path>              Signed PDF to verify (required)
 *   --truststore <path>         Truststore or .cer file for chain validation
 *                               (optional — uses JVM cacerts if omitted)
 *   --truststore-password <pwd> Truststore password (omit for .cer files)
 *   --truststore-type <type>    JKS, PKCS12, or CER (auto-detected if omitted)
 *
 *   Examples:
 *
 *   # Verify with JVM default trust (commercial CA certs)
 *   java -jar pdf-creator.jar --verify --input statement-signed.pdf
 *
 *   # Verify with custom trust (self-signed or internal CA)
 *   java -jar pdf-creator.jar --verify \
 *     --input statement-signed.pdf --truststore bank-public.cer
 *
 *   # Verify with a JKS truststore
 *   java -jar pdf-creator.jar --verify \
 *     --input statement-signed.pdf \
 *     --truststore internal-ca.jks --truststore-password changeit
 *
 * ── LIST SIGNATURES ───────────────────────────────────────────────────────
 *
 *   --list-signatures
 *   --input <path>              PDF to inspect (required)
 *
 *   Lists all signatures with metadata (signer, date, reason) without
 *   performing full cryptographic verification.
 *
 *   Example:
 *   java -jar pdf-creator.jar --list-signatures --input statement-signed.pdf
 *
 * ── EXPORT CERT ───────────────────────────────────────────────────────────
 *
 *   --export-cert
 *   --keystore <path>           Keystore to export from (required)
 *   --keystore-password <pwd>   Keystore password (required)
 *   --keystore-type <type>      PKCS12 (default) or JKS
 *   --alias <n>              Key alias (default: auto-detect first)
 *   --output <path>             Output .cer file path (required)
 *
 *   Exports the public certificate from a keystore as a DER-encoded .cer file.
 *   Share this file with recipients who need to manually trust your signature.
 *   They import it into Adobe Acrobat trusted identities once.
 *
 *   Example:
 *   java -jar pdf-creator.jar --export-cert \
 *     --keystore bank.p12 --keystore-password secret \
 *     --output bank-public.cer
 *
 * ── GENERATE KEYSTORE ─────────────────────────────────────────────────────
 *
 *   Generate a self-signed test keystore using keytool:
 *
 *   keytool -genkeypair \
 *     -storepass changeit -storetype PKCS12 -alias pdfcreator \
 *     -validity 365 -keyalg RSA -keysize 2048 \
 *     -dname "CN=MyOrg Statements, O=MyOrg, L=Chennai, ST=TN, C=IN" \
 *     -keystore test-keystore.p12
 */
public class PdfSignatureCli {

    private final PdfSigner            signer   = new PdfSigner();
    private final PdfSignatureVerifier verifier = new PdfSignatureVerifier();

    /** Entry point called from PdfCreator.main(). */
    public void run(String[] args) throws IOException {
        if (hasFlag(args, "--sign")) {
            runSign(args);
        } else if (hasFlag(args, "--verify")) {
            runVerify(args);
        } else if (hasFlag(args, "--list-signatures")) {
            runListSignatures(args);
        } else if (hasFlag(args, "--export-cert")) {
            runExportCert(args);
        } else {
            System.err.println("Error: no signature operation specified. " +
                "Use --sign, --verify, --list-signatures, or --export-cert.");
            System.exit(1);
        }
    }

    // -----------------------------------------------------------------------
    // SIGN
    // -----------------------------------------------------------------------

    private void runSign(String[] args) throws IOException {
        String input     = requireArg(args, "--input",             "--sign");
        String output    = requireArg(args, "--output",            "--sign");
        String keystore  = requireArg(args, "--keystore",          "--sign");
        String ksPwd     = requireArg(args, "--keystore-password", "--sign");
        String ksType    = getArg(args, "--keystore-type", "PKCS12");
        String alias     = getArg(args, "--alias",           null);
        String reason    = getArg(args, "--reason",          null);
        String location  = getArg(args, "--location",        null);
        String contact   = getArg(args, "--contact",         null);
        String sigName   = getArg(args, "--signer-name",     null);
        String tsaUrl    = getArg(args, "--tsa-url",         null);
        boolean visible  = hasFlag(args, "--visible");
        int sigPage      = intArg(args, "--sig-page",   -1);
        float sigX       = floatArg(args, "--sig-x",    50f);
        float sigY       = floatArg(args, "--sig-y",    50f);
        float sigW       = floatArg(args, "--sig-width",200f);
        float sigH       = floatArg(args, "--sig-height",60f);

        SigningOptions opts = new SigningOptions.Builder()
            .inputPath(input)
            .outputPath(output)
            .keystorePath(keystore)
            .keystorePassword(ksPwd)
            .keystoreType(ksType)
            .keyAlias(alias)
            .reason(reason)
            .location(location)
            .contactInfo(contact)
            .signerName(sigName)
            .tsaUrl(tsaUrl)
            .visible(visible)
            .signaturePage(sigPage)
            .signatureRect(sigX, sigY, sigW, sigH)
            .build();

        // Print summary header
        System.out.println("Mode           : sign");
        System.out.printf("Input          : %s%n", input);
        System.out.printf("Output         : %s%n", output);
        System.out.printf("Keystore       : %s (%s)%n", keystore, ksType);
        System.out.printf("Alias          : %s%n", alias != null ? alias : "auto");
        System.out.printf("Reason         : %s%n", reason  != null ? reason   : "(not set)");
        System.out.printf("Location       : %s%n", location!= null ? location : "(not set)");
        System.out.printf("TSA            : %s%n", tsaUrl  != null ? tsaUrl   : "none");
        System.out.printf("Visible sig    : %s%n",
            visible ? "YES (page " + (sigPage < 1 ? "last" : sigPage) + ")" : "NO");
        System.out.println();

        try {
            SignatureResult result = signer.sign(opts);
            System.out.println(result.getSummary());
        } catch (GeneralSecurityException e) {
            System.err.println("\nError: " + e.getMessage());
            System.err.println("  Hint: check --keystore path, --keystore-password, and --alias.");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("\nError: " + e.getMessage());
            System.exit(1);
        }
    }

    // -----------------------------------------------------------------------
    // VERIFY
    // -----------------------------------------------------------------------

    private void runVerify(String[] args) throws IOException {
        String input          = requireArg(args, "--input", "--verify");
        String truststore     = getArg(args, "--truststore",          null);
        String truststorePwd  = getArg(args, "--truststore-password", null);
        String truststoreType = getArg(args, "--truststore-type",     null);

        System.out.println("Mode           : verify");
        System.out.printf("Input          : %s%n", input);
        System.out.printf("Truststore     : %s%n", truststore != null ? truststore : "JVM default cacerts");
        System.out.println();

        List<VerificationResult> results =
            verifier.verify(input, truststore, truststorePwd, truststoreType);

        System.out.println(VerificationResult.summariseAll(results, input));
    }

    // -----------------------------------------------------------------------
    // LIST SIGNATURES
    // -----------------------------------------------------------------------

    private void runListSignatures(String[] args) throws IOException {
        String input = requireArg(args, "--input", "--list-signatures");

        System.out.println("Mode           : list-signatures");
        System.out.printf("Input          : %s%n", input);
        System.out.println();

        List<VerificationResult> results = verifier.listSignatures(input);

        if (results.isEmpty()) {
            System.out.println("No signatures found in: " + input);
            return;
        }

        System.out.println("=".repeat(55));
        System.out.println("  Signatures in: " + input);
        System.out.println("=".repeat(55));
        for (VerificationResult r : results) {
            System.out.printf("  [%d] Signer   : %s%n", r.getSignatureIndex() + 1,
                r.getSignerName() != null ? r.getSignerName() : "(unknown)");
            System.out.printf("      Signed at : %s%n",
                r.getSignedAt() != null ? r.getSignedAt() : "(unknown)");
            System.out.printf("      Reason    : %s%n",
                r.getReason() != null ? r.getReason() : "(not set)");
            System.out.printf("      Location  : %s%n",
                r.getLocation() != null ? r.getLocation() : "(not set)");
            System.out.printf("      Coverage  : %s%n",
                r.isCoverageComplete() ? "Complete" : "Partial");
            System.out.println();
        }
        System.out.println("=".repeat(55));
        System.out.printf("  Total: %d signature(s)%n", results.size());
        System.out.println("=".repeat(55));
    }

    // -----------------------------------------------------------------------
    // EXPORT CERT
    // -----------------------------------------------------------------------

    private void runExportCert(String[] args) throws IOException {
        String keystore = requireArg(args, "--keystore",          "--export-cert");
        String ksPwd    = requireArg(args, "--keystore-password", "--export-cert");
        String output   = requireArg(args, "--output",            "--export-cert");
        String ksType   = getArg(args, "--keystore-type", "PKCS12");
        String alias    = getArg(args, "--alias", null);

        System.out.println("Mode           : export-cert");
        System.out.printf("Keystore       : %s (%s)%n", keystore, ksType);
        System.out.printf("Alias          : %s%n", alias != null ? alias : "auto");
        System.out.printf("Output         : %s%n", output);
        System.out.println();

        try {
            java.security.KeyStore ks =
                java.security.KeyStore.getInstance(ksType);
            try (java.io.FileInputStream fis = new java.io.FileInputStream(keystore)) {
                ks.load(fis, ksPwd.toCharArray());
            }

            // Resolve alias
            String resolvedAlias = alias;
            if (resolvedAlias == null || resolvedAlias.isBlank()) {
                java.util.Enumeration<String> aliases = ks.aliases();
                while (aliases.hasMoreElements()) {
                    String a = aliases.nextElement();
                    if (ks.isKeyEntry(a)) { resolvedAlias = a; break; }
                }
            }
            if (resolvedAlias == null)
                throw new IOException("No key entry found in keystore: " + keystore);

            java.security.cert.Certificate cert = ks.getCertificate(resolvedAlias);
            if (cert == null)
                throw new IOException("No certificate for alias '" + resolvedAlias + "'");

            // Write DER-encoded certificate
            java.io.File outFile = new java.io.File(output);
            if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
                fos.write(cert.getEncoded());
            }

            java.security.cert.X509Certificate x509 =
                (java.security.cert.X509Certificate) cert;

            System.out.println("=".repeat(55));
            System.out.println("  Certificate Exported");
            System.out.println("=".repeat(55));
            System.out.printf("  Output file  : %s  (%,d bytes)%n",
                output, outFile.length());
            System.out.printf("  Subject      : %s%n",
                x509.getSubjectX500Principal().getName());
            System.out.printf("  Issuer       : %s%n",
                x509.getIssuerX500Principal().getName());
            System.out.printf("  Valid        : %s  →  %s%n",
                x509.getNotBefore(), x509.getNotAfter());
            System.out.printf("  Serial       : %s%n",
                x509.getSerialNumber().toString(16).toUpperCase());
            System.out.println("=".repeat(55));
            System.out.println();
            System.out.println("Share this .cer file with recipients who need to");
            System.out.println("verify your signatures. They import it into their");
            System.out.println("PDF viewer's trusted identities (once only).");
            System.out.println();
            System.out.println("  Adobe Acrobat: Edit → Preferences → Signatures");
            System.out.println("                 → Identities & Trusted Certificates");
            System.out.println("                 → Import");

        } catch (IOException e) {
            System.err.println("\nError: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("\nError: " + e.getMessage());
            System.err.println("  Hint: check --keystore path, --keystore-password, and --alias.");
            System.exit(1);
        }
    }

    // -----------------------------------------------------------------------
    // CLI argument helpers
    // -----------------------------------------------------------------------

    private static String requireArg(String[] args, String flag, String op) {
        String val = getArg(args, flag, null);
        if (val == null) {
            System.err.println("Error: " + op + " requires " + flag);
            System.exit(1);
        }
        return val;
    }

    private static String getArg(String[] args, String flag, String def) {
        for (int i = 0; i < args.length - 1; i++)
            if (args[i].equals(flag)) return args[i + 1];
        return def;
    }

    private static int intArg(String[] args, String flag, int def) {
        String v = getArg(args, flag, null);
        if (v == null) return def;
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private static float floatArg(String[] args, String flag, float def) {
        String v = getArg(args, flag, null);
        if (v == null) return def;
        try { return Float.parseFloat(v.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) if (a.equals(flag)) return true;
        return false;
    }
}
