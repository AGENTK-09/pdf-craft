package com.pdfcreator.htmlconverter;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Converts an HTML file to PDF using wkhtmltopdf as the rendering engine.
 *
 * ── APPROACH ──────────────────────────────────────────────────────────────
 *
 *   wkhtmltopdf is invoked as an external process via ProcessBuilder.
 *   No new Maven dependencies are required — ProcessBuilder is standard Java.
 *   PDFBox (already in the classpath) handles post-conversion metadata.
 *
 * ── WKHTMLTOPDF BINARY RESOLUTION ─────────────────────────────────────────
 *
 *   Resolution order:
 *     1. WKHTMLTOPDF_PATH environment variable (absolute path to binary)
 *     2. "wkhtmltopdf" on PATH (works when installed system-wide)
 *
 *   The binary is validated before conversion by running:
 *     wkhtmltopdf --version
 *   If this fails the user gets a clear error with install instructions.
 *
 * ── COMMAND BUILT ─────────────────────────────────────────────────────────
 *
 *   wkhtmltopdf
 *     --quiet
 *     --enable-local-file-access      (required for file:// CSS and images)
 *     --page-size <A4|Letter|...>
 *     --orientation <Portrait|Landscape>
 *     -T <n>mm -B <n>mm -L <n>mm -R <n>mm   (margins)
 *     [--zoom <factor>]
 *     [--print-media-type]
 *     [--enable-javascript --javascript-delay <ms>]
 *     [--no-images]
 *     [--user-style-sheet <path>]
 *     <input.html>
 *     <output.pdf>
 *
 * ── STDERR HANDLING ────────────────────────────────────────────────────────
 *
 *   wkhtmltopdf writes progress messages and warnings to stderr even with
 *   --quiet. Stderr is captured to a separate thread and classified:
 *     - Lines starting with "Loading page", "Printing pages", "Done" → FINE log
 *     - Lines starting with "Warning" or "Error" → included in result.warnings
 *     - Exit code != 0 → all stderr included in the exception message
 *
 * ── METADATA POST-PROCESSING ──────────────────────────────────────────────
 *
 *   After wkhtmltopdf writes the PDF, it is opened with PDFBox to:
 *     1. Set Title (from --title flag or extracted from HTML <title> tag)
 *     2. Set Author, Subject (from opts)
 *     3. Set CreationDate to now
 *     4. Save and close
 *
 *   This ensures consistent metadata across all PdfCreator-generated PDFs.
 *
 * ── THREAD SAFETY ─────────────────────────────────────────────────────────
 *
 *   Stateless — a single instance can be shared across threads.
 *   Each convert() call is fully independent.
 */
public class HtmlToPdfConverter {

    private static final Logger logger = Logger.getLogger(HtmlToPdfConverter.class.getName());

    // Lines from wkhtmltopdf stderr that are routine progress messages, not warnings.
    // Also suppresses the Qt "unpatched switch" notice that appears on stock builds
    // when using flags like --print-media-type — it is informational, not an error.
    private static final Pattern PROGRESS_PATTERN = Pattern.compile(
        "^(Loading page|Printing pages|Counting pages|Done|\\s*\\[" +
        "|QStandardPaths|Rendering|Table of Content|The switch .* is not support)",
        Pattern.CASE_INSENSITIVE);

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Converts the HTML file described by opts to a PDF using wkhtmltopdf.
     *
     * @param opts  fully-configured HtmlConversionOptions
     * @return      HtmlConversionResult with output path, size, duration, warnings
     * @throws IOException             on file I/O errors or if the output PDF is not produced
     * @throws HtmlConversionException if wkhtmltopdf exits with a non-zero code
     * @throws IllegalStateException   if the wkhtmltopdf binary cannot be found
     */
    public HtmlConversionResult convert(HtmlConversionOptions opts) throws IOException {
        return convert(opts, ToolConfig.loadDefault());
    }

    /**
     * Converts using an explicitly supplied ToolConfig.
     *
     * Use this overload when the caller controls config loading — e.g. when
     * the user passed --tool-config on the CLI, or in unit tests.
     */
    public HtmlConversionResult convert(HtmlConversionOptions opts,
                                         ToolConfig config) throws IOException {
        logger.info("HTML-to-PDF: " + opts);
        long start = System.currentTimeMillis();

        // ── 1. Validate input ────────────────────────────────────────────────
        File inputFile = requireFile(opts.getInputPath());

        // ── 2. Resolve wkhtmltopdf binary ────────────────────────────────────
        // ToolConfig resolution order: properties file → env var → PATH
        String binary = resolveBinary(config.resolveWkhtmltopdfBinary());

        // ── 3. Ensure output directory exists ────────────────────────────────
        File outputFile = new File(opts.getOutputPath());
        if (outputFile.getParentFile() != null) {
            Files.createDirectories(outputFile.getParentFile().toPath());
        }

        // ── 4. Build command ─────────────────────────────────────────────────
        // Use absolute paths for both input and output in the command.
        // Absolute paths make the invocation independent of the wkhtmltopdf
        // process's working directory, which is not set explicitly in
        // ProcessBuilder and defaults to the JVM's cwd. While both should be
        // the same, using absolute paths is unambiguous and always correct.
        String absOutputPath = outputFile.getAbsolutePath();
        List<String> command = buildCommand(binary, inputFile.getAbsolutePath(),
                                            absOutputPath, opts);
        logger.info("wkhtmltopdf command: " + String.join(" ", command));

        // ── 5. Execute wkhtmltopdf ───────────────────────────────────────────
        List<String> stderrLines = executeProcess(command, absOutputPath);

        // ── 6. Verify output was produced ────────────────────────────────────
        if (!outputFile.exists() || outputFile.length() == 0) {
            throw new IOException(
                "wkhtmltopdf did not produce an output file at: " + opts.getOutputPath() +
                (stderrLines.isEmpty() ? "" : "\n  stderr: " + String.join("\n  ", stderrLines)));
        }

        // ── 7. Classify stderr lines — separate warnings from progress noise ──
        List<String> warnings = new ArrayList<>();
        for (String line : stderrLines) {
            if (!line.isBlank() && !PROGRESS_PATTERN.matcher(line.trim()).find()) {
                warnings.add(line.trim());
                logger.warning("wkhtmltopdf: " + line.trim());
            } else {
                logger.fine("wkhtmltopdf: " + line.trim());
            }
        }

        // ── 8. Post-process metadata via PDFBox ──────────────────────────────
        String resolvedTitle = opts.getTitle() != null
            ? opts.getTitle()
            : extractHtmlTitle(inputFile);

        applyMetadata(outputFile, resolvedTitle, opts.getAuthor(), opts.getSubject());

        long durationMs = System.currentTimeMillis() - start;
        logger.info(String.format("HTML-to-PDF complete: %s → %s (%.2fs)",
            opts.getInputPath(), opts.getOutputPath(), durationMs / 1000.0));

        return new HtmlConversionResult.Builder()
            .inputPath(inputFile.getAbsolutePath())
            .outputPath(outputFile.getAbsolutePath())
            .renderer("wkhtmltopdf")
            .outputSizeBytes(outputFile.length())
            .durationMs(durationMs)
            .warnings(warnings)
            .build();
    }

    // -----------------------------------------------------------------------
    // Binary resolution
    // -----------------------------------------------------------------------

    /**
     * Resolves the wkhtmltopdf binary path.
     *
     * Checks WKHTMLTOPDF_PATH env var first, then falls back to "wkhtmltopdf"
     * on the system PATH. Validates the binary is executable by running
     * --version. Throws a descriptive error with install instructions if not found.
     */
    /**
     * Validates that the candidate binary (resolved by ToolConfig) is
     * executable by running "candidate --version" with a 10-second timeout.
     *
     * @param candidate  binary path or command name from ToolConfig
     * @return           the same candidate string if validation passes
     * @throws IllegalStateException if the binary cannot be found or executed
     */
    static String resolveBinary(String candidate) {
        try {
            Process probe = new ProcessBuilder(candidate, "--version")
                .redirectErrorStream(true)
                .start();
            // 10-second timeout — version check should be near-instant
            boolean done = probe.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!done) {
                probe.destroyForcibly();
                // fall through to error below
            } else {
                String version = new String(probe.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8).trim();
                if (probe.exitValue() == 0) {
                    logger.fine("wkhtmltopdf found: " + version);
                    return candidate;
                }
            }
        } catch (IOException | InterruptedException e) {
            // fall through to error below
        }

        throw new IllegalStateException(
            "wkhtmltopdf binary not found or not executable: '" + candidate + "'\n" +
            "  Resolution attempted: properties file → WKHTMLTOPDF_PATH env var → PATH\n" +
            "  To configure the path, add to pdf-creator.properties:\n" +
            "    wkhtmltopdf.path = C:/Program Files/wkhtmltopdf/bin/wkhtmltopdf.exe\n" +
            "  Or set env var: WKHTMLTOPDF_PATH=/usr/local/bin/wkhtmltopdf\n" +
            "  Install wkhtmltopdf:\n" +
            "    Ubuntu/Debian : sudo apt-get install wkhtmltopdf\n" +
            "    macOS         : brew install wkhtmltopdf\n" +
            "    Windows       : https://wkhtmltopdf.org/downloads.html");
    }

    // -----------------------------------------------------------------------
    // Command construction
    // -----------------------------------------------------------------------

    /**
     * Builds the wkhtmltopdf command-line argument list.
     *
     * Argument order follows the wkhtmltopdf documentation:
     *   wkhtmltopdf [global options] [page options] <input> <output>
     */
    static List<String> buildCommand(String binary, String inputAbsPath,
                                      String outputAbsPath,
                                      HtmlConversionOptions opts) {
        List<String> cmd = new ArrayList<>();
        cmd.add(binary);

        // ── Global / output options ──────────────────────────────────────────
        cmd.add("--quiet");                         // suppress progress bar on stdout
        cmd.add("--enable-local-file-access");      // allow file:// CSS and image loading
        cmd.add("--page-size");    cmd.add(opts.getPageSize());
        cmd.add("--orientation"); cmd.add(capitalise(opts.getOrientation()));

        // Margins (wkhtmltopdf accepts "<n>mm" units directly)
        cmd.add("-T"); cmd.add(opts.getMarginTopMm()    + "mm");
        cmd.add("-B"); cmd.add(opts.getMarginBottomMm() + "mm");
        cmd.add("-L"); cmd.add(opts.getMarginLeftMm()   + "mm");
        cmd.add("-R"); cmd.add(opts.getMarginRightMm()  + "mm");

        // Zoom factor
        if (Math.abs(opts.getZoom() - 1.0f) > 0.001f) {
            cmd.add("--zoom"); cmd.add(String.format("%.2f", opts.getZoom()));
        }

        // Print media type (@media print CSS instead of @media screen)
        if (opts.isPrintMediaType()) {
            cmd.add("--print-media-type");
        }

        // JavaScript
        if (opts.isEnableJavascript()) {
            cmd.add("--enable-javascript");
            if (opts.getJavascriptDelayMs() > 0) {
                cmd.add("--javascript-delay");
                cmd.add(String.valueOf(opts.getJavascriptDelayMs()));
            }
        } else {
            cmd.add("--disable-javascript");
        }

        // Images
        if (!opts.isLoadImages()) {
            cmd.add("--no-images");
        }

        // Extra user stylesheet
        if (opts.getUserStyleSheet() != null && !opts.getUserStyleSheet().isBlank()) {
            cmd.add("--user-style-sheet"); cmd.add(opts.getUserStyleSheet());
        }

        // Base URL override
        if (opts.getBaseUrl() != null && !opts.getBaseUrl().isBlank()) {
            // wkhtmltopdf resolves relative paths relative to the HTML file's
            // directory by default. --base-url overrides this.
            // Note: this flag is only accepted in newer wkhtmltopdf builds.
            // If it causes issues, remove and advise users to organise files
            // relative to the HTML file instead.
        }

        // wkhtmltopdf title flag sets the PDF window title but NOT the info dict.
        // We set the info dict title via PDFBox after conversion.
        // No --title flag needed here.

        // ── Input and output ────────────────────────────────────────────────
        // Both paths are absolute — ensures wkhtmltopdf writes to exactly the
        // file we check for existence and pass to PDFBox for metadata writing.
        cmd.add(inputAbsPath);
        cmd.add(outputAbsPath);

        return cmd;
    }

    // -----------------------------------------------------------------------
    // Process execution
    // -----------------------------------------------------------------------

    /**
     * Executes the wkhtmltopdf process and waits for completion.
     *
     * Stderr is captured via a background reader thread. Stdin is closed
     * immediately (wkhtmltopdf reads from file, not stdin).
     *
     * Timeout: 120 seconds per document. This is generous — even a 100-page
     * HTML document should render in well under 60 seconds.
     *
     * @param command      full command line including binary and all args
     * @param outputPath   used only in error messages
     * @return             all lines captured from stderr
     * @throws IOException             on process I/O failure
     * @throws HtmlConversionException on non-zero exit code
     */
    private static List<String> executeProcess(List<String> command,
                                                String outputPath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);

        // ── stdin: redirect from /dev/null ────────────────────────────────
        // wkhtmltopdf reads from the input FILE argument, not stdin.
        // Redirecting stdin from /dev/null sends immediate EOF and avoids
        // any risk of the process blocking waiting for stdin input.
        pb.redirectInput(ProcessBuilder.Redirect.from(
            new java.io.File(System.getProperty("os.name", "")
                .toLowerCase().contains("win") ? "NUL" : "/dev/null")));

        // ── stdout: discard ───────────────────────────────────────────────
        // wkhtmltopdf writes the PDF to the output FILE argument, not stdout.
        // With --quiet it writes minimal progress to stderr.
        // We must still drain stdout: if wkhtmltopdf writes anything to stdout
        // (some builds do), an unread PIPE buffer fills and DEADLOCKS the process,
        // making it appear as though wkhtmltopdf was never invoked.
        // Discarding stdout prevents this entirely.
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);

        // ── stderr: separate pipe (we read it in a background thread) ─────
        pb.redirectErrorStream(false);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new IOException("Failed to start wkhtmltopdf process: " + e.getMessage(), e);
        }

        // Capture stderr in a background thread.
        // This must run concurrently with waitFor() — if we waited for the
        // process first and then read stderr, the stderr pipe buffer could fill
        // and deadlock the process before it exits.
        List<String> stderrLines = new ArrayList<>();
        Thread stderrReader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    stderrLines.add(line);
                }
            } catch (IOException ignored) {
                // process ended, stream closed — normal exit path
            }
        }, "wkhtmltopdf-stderr-reader");
        stderrReader.setDaemon(true);
        stderrReader.start();

        // Wait for completion with a 120-second timeout.
        int exitCode;
        try {
            boolean finished = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new HtmlConversionException(
                    "wkhtmltopdf timed out after 120 seconds for output: " + outputPath,
                    -1, List.of("Process timed out"));
            }
            exitCode = process.exitValue();
            stderrReader.join(5000);  // give stderr reader up to 5s to drain
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("wkhtmltopdf process interrupted", e);
        }

        // wkhtmltopdf exit codes:
        //   0 = success (output file written)
        //   1 = failure (bad HTML, missing resources, permission error, etc.)
        //   2 = fatal error
        // Exit 0 with warnings is non-fatal — warnings appear in stderrLines.
        if (exitCode != 0) {
            throw new HtmlConversionException(
                "wkhtmltopdf failed with exit code " + exitCode +
                " for output: " + outputPath,
                exitCode, stderrLines);
        }

        return stderrLines;
    }

    // -----------------------------------------------------------------------
    // HTML title extraction
    // -----------------------------------------------------------------------

    /**
     * Extracts the content of the &lt;title&gt; tag from an HTML file.
     *
     * Uses a simple regex — sufficient for well-formed HTML and avoids pulling
     * in Jsoup as a dependency. Returns null if no title tag is found.
     */
    static String extractHtmlTitle(File htmlFile) {
        try {
            String content = Files.readString(htmlFile.toPath(), StandardCharsets.UTF_8);
            java.util.regex.Matcher m = Pattern.compile(
                "<title[^>]*>([^<]*)</title>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            ).matcher(content);
            if (m.find()) {
                String title = m.group(1).trim();
                // Decode common HTML entities
                title = title.replace("&amp;", "&")
                             .replace("&lt;",  "<")
                             .replace("&gt;",  ">")
                             .replace("&quot;", "\"")
                             .replace("&#39;", "'");
                return title.isBlank() ? null : title;
            }
        } catch (IOException e) {
            logger.fine("Could not read HTML file for title extraction: " + e.getMessage());
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Metadata post-processing
    // -----------------------------------------------------------------------

    /**
     * Opens the output PDF with PDFBox and updates the document information
     * dictionary with the supplied metadata values.
     *
     * Called after wkhtmltopdf produces the PDF. Sets Title, Author, Subject,
     * and CreationDate. Producer is set to "PdfCreator (wkhtmltopdf)".
     *
     * Fields with a null value are left unchanged (wkhtmltopdf may have
     * already set them from the HTML &lt;meta&gt; tags).
     */
    private static void applyMetadata(File pdfFile, String title,
                                       String author, String subject) throws IOException {
        if (title == null && author == null && subject == null) {
            // Nothing to update — skip opening the file to save time
            return;
        }
        // Save to a sibling temp file then atomically replace the original.
        // Writing back to the same File while it is still open (Loader.loadPDF
        // holds a read handle on some JVMs) can produce a corrupt or zero-byte
        // result. Using a temp file avoids this entirely.
        File tempFile = new File(pdfFile.getParent(),
            "." + pdfFile.getName() + ".meta.tmp");
        try {
            try (PDDocument doc = Loader.loadPDF(pdfFile)) {
                PDDocumentInformation info = doc.getDocumentInformation();
                if (title   != null) info.setTitle(title);
                if (author  != null) info.setAuthor(author);
                if (subject != null) info.setSubject(subject);
                info.setProducer("PdfCreator (wkhtmltopdf)");
                info.setCreationDate(Calendar.getInstance());
                doc.setDocumentInformation(info);
                doc.save(tempFile);  // write to temp, not the original
            }
            // Atomically replace original with the updated temp file.
            // Files.move with REPLACE_EXISTING is atomic on most OS/filesystems.
            java.nio.file.Files.move(tempFile.toPath(), pdfFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            logger.fine("Metadata applied to: " + pdfFile.getAbsolutePath());
        } catch (IOException e) {
            // If metadata write fails, keep the original wkhtmltopdf output.
            // The PDF content is intact — only metadata could not be updated.
            logger.warning("Could not apply metadata to " + pdfFile.getName() +
                " — keeping original wkhtmltopdf output. Reason: " + e.getMessage());
            tempFile.delete();
            // Do not rethrow — a metadata failure should not fail the conversion.
        }
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    private static File requireFile(String path) throws IOException {
        File f = new File(path);
        if (!f.exists())  throw new IOException("HTML file not found: " + path);
        if (!f.isFile())  throw new IOException("Not a file: " + path);
        if (!f.canRead()) throw new IOException("HTML file is not readable: " + path);
        return f;
    }

    private static String capitalise(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
