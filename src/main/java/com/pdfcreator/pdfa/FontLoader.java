package com.pdfcreator.pdfa;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Loads and provides fonts for PDF rendering.
 *
 * ── TWO MODES ─────────────────────────────────────────────────────────────
 *
 * STANDARD MODE (pdfa = false):
 *   Returns PDType1Font instances backed by the Standard 14 fonts. These are
 *   guaranteed to be available in every PDF viewer but are never embedded in
 *   the PDF file. Fine for general use; NOT compliant with PDF/A-1b.
 *
 * PDF/A MODE (pdfa = true):
 *   Returns PDType0Font instances loaded from TrueType font files bundled in
 *   src/main/resources/fonts/. These are fully embedded in each PDDocument,
 *   satisfying the PDF/A-1b requirement that all fonts be embedded.
 *
 *   Font files used (Liberation fonts — Apache 2.0 license, metrics-compatible
 *   with Helvetica/Times/Courier):
 *     fonts/LiberationSans-Regular.ttf   → HELVETICA
 *     fonts/LiberationSans-Bold.ttf      → HELVETICA_BOLD
 *     fonts/LiberationSerif-Regular.ttf  → TIMES_ROMAN
 *     fonts/LiberationSerif-Bold.ttf     → TIMES_BOLD
 *     fonts/LiberationMono-Regular.ttf   → COURIER
 *
 * ── DOCUMENT BINDING ──────────────────────────────────────────────────────
 *
 * PDType0Font.load() binds the font to a specific PDDocument — the font
 * object cannot be shared across documents. FontLoader is therefore also
 * bound to one PDDocument per instance. Create a new FontLoader for each
 * document you render.
 *
 * ── FALLBACK ──────────────────────────────────────────────────────────────
 *
 * If a font resource is not found on the classpath (e.g. running outside the
 * fat JAR), FontLoader logs a warning and returns a Standard14 font. This
 * means the document will open correctly but will not be PDF/A-1b compliant.
 */
public class FontLoader {

    private static final Logger logger = Logger.getLogger(FontLoader.class.getName());

    /** Font role used as cache key. */
    public enum Role {
        SANS, SANS_BOLD, SERIF, SERIF_BOLD, MONO
    }

    private static final Map<Role, String> RESOURCE_PATHS = new EnumMap<>(Role.class);
    static {
        RESOURCE_PATHS.put(Role.SANS,       "/fonts/LiberationSans-Regular.ttf");
        RESOURCE_PATHS.put(Role.SANS_BOLD,  "/fonts/LiberationSans-Bold.ttf");
        RESOURCE_PATHS.put(Role.SERIF,      "/fonts/LiberationSerif-Regular.ttf");
        RESOURCE_PATHS.put(Role.SERIF_BOLD, "/fonts/LiberationSerif-Bold.ttf");
        RESOURCE_PATHS.put(Role.MONO,       "/fonts/LiberationMono-Regular.ttf");
    }

    private static final Map<Role, Standard14Fonts.FontName> STANDARD14_FALLBACK =
        new EnumMap<>(Role.class);
    static {
        STANDARD14_FALLBACK.put(Role.SANS,       Standard14Fonts.FontName.HELVETICA);
        STANDARD14_FALLBACK.put(Role.SANS_BOLD,  Standard14Fonts.FontName.HELVETICA_BOLD);
        STANDARD14_FALLBACK.put(Role.SERIF,      Standard14Fonts.FontName.TIMES_ROMAN);
        STANDARD14_FALLBACK.put(Role.SERIF_BOLD, Standard14Fonts.FontName.TIMES_BOLD);
        STANDARD14_FALLBACK.put(Role.MONO,       Standard14Fonts.FontName.COURIER);
    }

    // -----------------------------------------------------------------------
    // Instance state
    // -----------------------------------------------------------------------

    private final PDDocument document;
    private final boolean    pdfaMode;

    /** Per-document cache — avoid embedding the same font bytes multiple times. */
    private final Map<Role, PDFont> cache = new EnumMap<>(Role.class);

    public FontLoader(PDDocument document, boolean pdfaMode) {
        this.document = document;
        this.pdfaMode = pdfaMode;
    }

    // -----------------------------------------------------------------------
    // Public API — by Role
    // -----------------------------------------------------------------------

    public PDFont get(Role role) throws IOException {
        PDFont cached = cache.get(role);
        if (cached != null) return cached;

        PDFont font = pdfaMode ? loadEmbedded(role) : loadStandard14(role);
        cache.put(role, font);
        return font;
    }

    // -----------------------------------------------------------------------
    // Convenience accessors matching PdfConfig.fontFamily naming
    // -----------------------------------------------------------------------

    /** Returns the regular body font for the given fontFamily config value. */
    public PDFont regularFor(String fontFamily) throws IOException {
        return get(roleForFamily(fontFamily, false));
    }

    /** Returns the bold variant of the given fontFamily config value. */
    public PDFont boldFor(String fontFamily) throws IOException {
        return get(roleForFamily(fontFamily, true));
    }

    private static Role roleForFamily(String fontFamily, boolean bold) {
        if (fontFamily == null) return bold ? Role.SANS_BOLD : Role.SANS;
        return switch (fontFamily.toUpperCase()) {
            case "TIMES_ROMAN", "TIMES" -> bold ? Role.SERIF_BOLD : Role.SERIF;
            case "COURIER"              -> Role.MONO;            // no bold variant needed
            default                     -> bold ? Role.SANS_BOLD : Role.SANS;
        };
    }

    // -----------------------------------------------------------------------
    // Private — font loading
    // -----------------------------------------------------------------------

    private PDFont loadEmbedded(Role role) throws IOException {
        String path = RESOURCE_PATHS.get(role);
        try (InputStream is = FontLoader.class.getResourceAsStream(path)) {
            if (is == null) {
                logger.warning("Font resource not found: " + path +
                    " — falling back to Standard14 (PDF/A compliance may be affected)");
                return loadStandard14(role);
            }
            // embedSubset=false: embed the full font so all glyphs are available.
            // Subsetting (true) embeds only used glyphs, which is fine for PDF/A-1b
            // but makes the font unusable for later editing or text extraction.
            PDFont font = PDType0Font.load(document, is, false);
            logger.fine("Embedded font: " + path + " for role " + role);
            return font;
        }
    }

    private static PDFont loadStandard14(Role role) {
        return new PDType1Font(STANDARD14_FALLBACK.get(role));
    }
}
