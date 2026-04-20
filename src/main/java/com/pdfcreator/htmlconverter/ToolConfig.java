package com.pdfcreator.htmlconverter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Reads tool binary paths from a Java properties file.
 *
 * Used by HtmlToPdfConverter to locate the wkhtmltopdf executable.
 * Designed to be extended for future renderers (e.g. chromium.path).
 *
 * ── PROPERTIES FILE FORMAT ────────────────────────────────────────────────
 *
 *   Standard Java .properties format — key=value, one per line.
 *   Lines starting with # are comments.
 *
 *   # pdf-creator.properties
 *   wkhtmltopdf.path = C:/Program Files/wkhtmltopdf/bin/wkhtmltopdf.exe
 *
 *   On Linux/macOS (usually on PATH, but can be explicit):
 *   wkhtmltopdf.path = /usr/local/bin/wkhtmltopdf
 *
 * ── DEFAULT FILE LOCATION ─────────────────────────────────────────────────
 *
 *   The default filename is "pdf-creator.properties".
 *   Resolution order for the default location:
 *     1. Same directory as the running JAR file
 *     2. Current working directory
 *
 *   A custom path can be passed via the CLI flag --tool-config <path>
 *   or via the PDFCREATOR_CONFIG environment variable.
 *
 * ── PROPERTY KEYS ─────────────────────────────────────────────────────────
 *
 *   wkhtmltopdf.path    Absolute path to the wkhtmltopdf binary.
 *                       Windows: C:/Program Files/wkhtmltopdf/bin/wkhtmltopdf.exe
 *                       Linux:   /usr/bin/wkhtmltopdf
 *                       macOS:   /usr/local/bin/wkhtmltopdf
 *
 * ── BINARY RESOLUTION ORDER ───────────────────────────────────────────────
 *
 *   When resolving the wkhtmltopdf binary, the following order applies
 *   (most explicit wins):
 *
 *     1. --tool-config file → wkhtmltopdf.path property
 *     2. WKHTMLTOPDF_PATH environment variable
 *     3. "wkhtmltopdf" on the system PATH
 *
 *   This lets users configure the path once in the properties file instead
 *   of setting an environment variable every time.
 *
 * ── EXAMPLE PROPERTIES FILE ───────────────────────────────────────────────
 *
 *   # pdf-creator.properties
 *   #
 *   # Tool binary paths — edit for your installation.
 *   # Use forward slashes on all platforms (Java accepts them on Windows too).
 *   #
 *   wkhtmltopdf.path = C:/Program Files/wkhtmltopdf/bin/wkhtmltopdf.exe
 *
 * ── USAGE ─────────────────────────────────────────────────────────────────
 *
 *   // Load from default location (JAR dir or cwd)
 *   ToolConfig config = ToolConfig.loadDefault();
 *
 *   // Load from explicit path
 *   ToolConfig config = ToolConfig.load("C:/myapp/pdf-creator.properties");
 *
 *   // Resolve wkhtmltopdf binary (properties → env var → PATH)
 *   String binary = config.resolveWkhtmltopdfBinary();
 */
public class ToolConfig {

    private static final Logger logger = Logger.getLogger(ToolConfig.class.getName());

    /** Default properties filename, looked up next to the JAR or in cwd. */
    public static final String DEFAULT_FILENAME = "pdf-creator.properties";

    /** Property key for the wkhtmltopdf binary path. */
    public static final String KEY_WKHTMLTOPDF = "wkhtmltopdf.path";

    /** Environment variable name for the wkhtmltopdf binary path. */
    public static final String ENV_WKHTMLTOPDF = "WKHTMLTOPDF_PATH";

    /** Environment variable for a custom properties file path. */
    public static final String ENV_CONFIG_FILE  = "PDFCREATOR_CONFIG";

    /** Default wkhtmltopdf command when nothing else is configured. */
    private static final String DEFAULT_BINARY  = "wkhtmltopdf";

    // -----------------------------------------------------------------------

    private final Properties props;
    private final String     sourceDescription;  // for logging only

    private ToolConfig(Properties props, String sourceDescription) {
        this.props             = props;
        this.sourceDescription = sourceDescription;
    }

    // -----------------------------------------------------------------------
    // Factory methods
    // -----------------------------------------------------------------------

    /**
     * Loads tool configuration from an explicit file path.
     *
     * @param filePath  absolute or relative path to the properties file
     * @return          loaded ToolConfig
     * @throws IOException if the file exists but cannot be read
     * @throws FileNotFoundException if the file does not exist
     */
    public static ToolConfig load(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException(
                "Tool config file not found: " + file.getAbsolutePath() + "\n" +
                "  Create the file with: wkhtmltopdf.path = <path to wkhtmltopdf>");
        }
        if (!file.isFile() || !file.canRead()) {
            throw new IOException("Cannot read tool config file: " + file.getAbsolutePath());
        }

        Properties props = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            props.load(in);
        }

        logger.info("Tool config loaded from: " + file.getAbsolutePath());
        logLoadedKeys(props, file.getAbsolutePath());
        return new ToolConfig(props, file.getAbsolutePath());
    }

    /**
     * Loads tool configuration from the default location.
     *
     * Resolution order for the properties file:
     *   1. PDFCREATOR_CONFIG environment variable (explicit override)
     *   2. Same directory as the running JAR file
     *   3. Current working directory
     *
     * Returns an empty ToolConfig (no properties) if no file is found in
     * any of these locations. This is not an error — the binary resolution
     * falls back to the env var and PATH in that case.
     */
    public static ToolConfig loadDefault() {
        // 1. Check PDFCREATOR_CONFIG env var
        String envConfig = System.getenv(ENV_CONFIG_FILE);
        if (envConfig != null && !envConfig.isBlank()) {
            try {
                return load(envConfig);
            } catch (IOException e) {
                logger.warning("PDFCREATOR_CONFIG points to unreadable file: " + e.getMessage());
            }
        }

        // 2. Try same directory as the JAR
        Path jarDir = resolveJarDirectory();
        if (jarDir != null) {
            Path candidate = jarDir.resolve(DEFAULT_FILENAME);
            if (Files.exists(candidate)) {
                try {
                    return load(candidate.toString());
                } catch (IOException e) {
                    logger.warning("Could not read " + candidate + ": " + e.getMessage());
                }
            }
        }

        // 3. Try current working directory
        Path cwdCandidate = Paths.get(DEFAULT_FILENAME);
        if (Files.exists(cwdCandidate)) {
            try {
                return load(cwdCandidate.toAbsolutePath().toString());
            } catch (IOException e) {
                logger.warning("Could not read " + cwdCandidate + ": " + e.getMessage());
            }
        }

        // No file found — return empty config, resolution falls back to env var / PATH
        logger.fine("No " + DEFAULT_FILENAME + " found in JAR directory or cwd — " +
            "using environment variable / PATH for tool resolution");
        return new ToolConfig(new Properties(), "none (using env var / PATH)");
    }

    // -----------------------------------------------------------------------
    // Binary resolution
    // -----------------------------------------------------------------------

    /**
     * Resolves the wkhtmltopdf binary path using the priority order:
     *
     *   1. wkhtmltopdf.path in this properties file  (most explicit)
     *   2. WKHTMLTOPDF_PATH environment variable
     *   3. "wkhtmltopdf" on the system PATH           (least explicit)
     *
     * Does NOT validate that the binary exists or is executable —
     * that validation is done by HtmlToPdfConverter.resolveBinary().
     *
     * @return the resolved binary path or command name
     */
    public String resolveWkhtmltopdfBinary() {
        // 1. Properties file
        String fromProps = props.getProperty(KEY_WKHTMLTOPDF);
        if (fromProps != null && !fromProps.isBlank()) {
            String path = fromProps.trim();
            // Normalise Windows backslashes to forward slashes for ProcessBuilder
            // (Java's ProcessBuilder accepts forward slashes on Windows too)
            path = path.replace('\\', '/');
            logger.fine("wkhtmltopdf path from properties file (" +
                sourceDescription + "): " + path);
            return path;
        }

        // 2. Environment variable
        String fromEnv = System.getenv(ENV_WKHTMLTOPDF);
        if (fromEnv != null && !fromEnv.isBlank()) {
            logger.fine("wkhtmltopdf path from env var " + ENV_WKHTMLTOPDF + ": " + fromEnv);
            return fromEnv.trim();
        }

        // 3. Assume it's on PATH
        logger.fine("wkhtmltopdf: using default command name (assumed on PATH)");
        return DEFAULT_BINARY;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    /**
     * Returns the raw value of a property, or null if not set.
     * Use this for any future tool properties.
     */
    public String getProperty(String key) {
        return props.getProperty(key);
    }

    /**
     * Returns the source description for logging (file path or "none").
     */
    public String getSourceDescription() {
        return sourceDescription;
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    /**
     * Returns the directory containing the running JAR file, or null if it
     * cannot be determined (e.g. running from IDE with loose .class files).
     */
    private static Path resolveJarDirectory() {
        try {
            // ProtectionDomain gives the location of the class's source
            java.security.CodeSource cs =
                ToolConfig.class.getProtectionDomain().getCodeSource();
            if (cs == null) return null;

            java.net.URL location = cs.getLocation();
            if (location == null) return null;

            Path jarPath = Paths.get(location.toURI());
            // If the location is a .jar file, return its parent directory.
            // If it's a directory (IDE run), return the directory itself.
            return Files.isDirectory(jarPath) ? jarPath : jarPath.getParent();
        } catch (Exception e) {
            logger.fine("Could not determine JAR directory: " + e.getMessage());
            return null;
        }
    }

    private static void logLoadedKeys(Properties props, String source) {
        if (props.isEmpty()) {
            logger.fine("  (no properties found in " + source + ")");
            return;
        }
        for (String key : props.stringPropertyNames()) {
            // Mask path values slightly in logs for cleanliness
            logger.fine("  " + key + " = " + props.getProperty(key));
        }
    }

    @Override
    public String toString() {
        return "ToolConfig[source=" + sourceDescription +
            ", keys=" + props.stringPropertyNames() + "]";
    }
}
