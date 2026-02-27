package com.pdfcreator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Resolves the final body text for a PDF from one or more input sources.
 *
 * Priority / combination rules:
 *   --text-file only   → content of the file
 *   --text only        → inline CLI text
 *   both provided      → file content + "\n\n" + inline CLI text (concatenated)
 *   neither provided   → null (no body text rendered)
 *
 * Supported file types: any plain text file (.txt, .md, .csv, etc.)
 * Encoding: UTF-8
 *
 * Future extension point:
 *   Add resolveFromUrl(), resolveFromStdin(), etc. here without touching
 *   PdfCreator.java or PdfGenerator.java.
 */
public class TextInputResolver {

    private static final Logger logger = Logger.getLogger(TextInputResolver.class.getName());

    private final String inlineText;   // from --text, may be null
    private final String textFilePath; // from --text-file, may be null

    public TextInputResolver(String inlineText, String textFilePath) {
        this.inlineText   = inlineText;
        this.textFilePath = textFilePath;
    }

    /**
     * Resolves and returns the final body text, or null if no input was provided.
     *
     * @throws IOException          if --text-file was given but the file cannot be read
     * @throws IllegalArgumentException if --text-file path exists but is a directory
     */
    public String resolve() throws IOException {
        String fileContent  = readFile();
        String inlineContent = normalise(inlineText);

        if (fileContent == null && inlineContent == null) {
            logger.fine("No text input provided.");
            return null;
        }

        if (fileContent != null && inlineContent != null) {
            logger.info("Both --text-file and --text provided — concatenating.");
            return fileContent + "\n\n" + inlineContent;
        }

        return fileContent != null ? fileContent : inlineContent;
    }

    /**
     * Returns a human-readable summary of where text is coming from.
     * Useful for console output before generation starts.
     */
    public String describeSource() {
        boolean hasFile   = textFilePath != null && !textFilePath.isBlank();
        boolean hasInline = inlineText   != null && !inlineText.isBlank();

        if (hasFile && hasInline) return "file (" + textFilePath + ") + inline --text";
        if (hasFile)              return "file (" + textFilePath + ")";
        if (hasInline)            return "inline --text";
        return "(none)";
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private String readFile() throws IOException {
        if (textFilePath == null || textFilePath.isBlank()) return null;

        Path path = Path.of(textFilePath);

        if (!Files.exists(path)) {
            throw new IOException("Text file not found: " + textFilePath);
        }
        if (Files.isDirectory(path)) {
            throw new IllegalArgumentException("--text-file points to a directory, not a file: " + textFilePath);
        }

        String content = Files.readString(path); // UTF-8
        logger.info("Read " + content.length() + " characters from: " + textFilePath);
        return normalise(content);
    }

    /**
     * Trims and returns null if blank, otherwise returns the trimmed value.
     */
    private String normalise(String text) {
        if (text == null || text.isBlank()) return null;
        return text.trim();
    }
}
