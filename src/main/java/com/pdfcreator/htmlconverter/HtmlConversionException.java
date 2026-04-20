package com.pdfcreator.htmlconverter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when wkhtmltopdf exits with a non-zero exit code.
 *
 * Contains the exit code and all stderr lines captured during the failed
 * conversion, making it easy to surface a useful error message to the user.
 *
 * Extends IOException so callers that catch IOException already handle it
 * without requiring separate catch blocks.
 */
public class HtmlConversionException extends IOException {

    private final int          exitCode;
    private final List<String> stderrLines;

    public HtmlConversionException(String message, int exitCode, List<String> stderrLines) {
        super(buildMessage(message, exitCode, stderrLines));
        this.exitCode    = exitCode;
        this.stderrLines = Collections.unmodifiableList(stderrLines);
    }

    /** wkhtmltopdf process exit code. 0 = success; non-zero = failure. */
    public int getExitCode() { return exitCode; }

    /** All lines captured from wkhtmltopdf's stderr during the failed run. */
    public List<String> getStderrLines() { return stderrLines; }

    private static String buildMessage(String base, int code, List<String> lines) {
        if (lines == null || lines.isEmpty()) return base + " (exit code " + code + ")";
        StringBuilder sb = new StringBuilder(base);
        sb.append(" (exit code ").append(code).append(")");
        sb.append("\n  wkhtmltopdf output:");
        lines.stream().limit(20).forEach(l -> sb.append("\n    ").append(l));
        if (lines.size() > 20) sb.append("\n    ... (").append(lines.size() - 20).append(" more lines)");
        return sb.toString();
    }
}
