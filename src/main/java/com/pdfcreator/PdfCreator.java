package com.pdfcreator;

import com.pdfcreator.batch.*;
import com.pdfcreator.config.PdfConfig;
import com.pdfcreator.datasource.DocumentData;
import com.pdfcreator.datasource.JsonFileDataSource;
import com.pdfcreator.generator.PageContext;
import com.pdfcreator.pipeline.RenderPipeline;
import com.pdfcreator.renderer.SectionRendererRegistry;
import com.pdfcreator.service.ConfigService;
import com.pdfcreator.extractor.PdfExtractorCli;
import com.pdfcreator.manipulator.PdfManipulatorCli;
import com.pdfcreator.printer.PdfPrinterCli;
import com.pdfcreator.security.PdfSecurityCli;
import com.pdfcreator.signature.PdfSignatureCli;
import com.pdfcreator.rasterizer.PdfRasterizerCli;
import com.pdfcreator.validator.PdfValidatorCli;
import com.pdfcreator.template.DocumentMetadata;
import com.pdfcreator.template.TemplateSection;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.Security;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import com.pdfcreator.pdfa.FontLoader;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Entry point. Three operating modes:
 *
 *   TEMPLATE MODE  --template-id + --data-file
 *     Full pipeline: template + structured JSON data → one PDF.
 *
 *   BATCH MODE     --batch + --template-id + (--data-dir | --csv-file)
 *     Parallel batch: one template + N data sources → N PDFs.
 *
 *   DIRECT MODE    --title / --text / --text-file
 *     Quick generation without a template file.
 */
public class PdfCreator {

    private static final String DEFAULT_OUTPUT        = "output.pdf";
    private static final String DEFAULT_CONFIG_FILE   = "configs/pdf-configs.json";
    private static final String DEFAULT_CONFIG_ID     = "default";
    private static final String DEFAULT_TEMPLATE_FILE = "templates/pdf-templates.json";

    public static void main(String[] args) throws Exception {
        // Register BouncyCastle as a JCE security provider.
        // Required for all CMS signing, TSA timestamping, and certificate chain
        // operations. addProvider() is a no-op if BC is already registered.
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        if (args.length == 0 || hasFlag(args, "--help")) { printHelp(); return; }

        String outputPath   = getArg(args, "--output",        DEFAULT_OUTPUT);
        String configFile   = getArg(args, "--config-file",   DEFAULT_CONFIG_FILE);
        String templateFile = getArg(args, "--template-file", DEFAULT_TEMPLATE_FILE);
        String templateId   = getArg(args, "--template-id",   null);

        if (hasFlag(args, "--validate")) {
            // ---- VALIDATE MODE ----
            new PdfValidatorCli().run(args);
            return;
        }

        if (hasFlag(args, "--rasterize")) {
            // ---- RASTERIZE MODE ----
            new PdfRasterizerCli().run(args);
            return;
        }

        if (hasFlag(args, "--extract") || hasFlag(args, "--extract-images")) {
            // ---- EXTRACT / EXTRACT-IMAGES MODE ----
            new PdfExtractorCli().run(args);
            return;
        }

        if (hasFlag(args, "--merge") || hasFlag(args, "--split")) {
            // ---- MERGE / SPLIT MODE ----
            new PdfManipulatorCli().run(args);
            return;
        }

        if (hasFlag(args, "--print") || hasFlag(args, "--list-printers")) {
            // ---- PRINT / LIST-PRINTERS MODE ----
            new PdfPrinterCli().run(args);
            return;
        }

        // --sign is excluded here when --batch is also present: batch signing
        // is handled inside the BATCH MODE block below, not by PdfSignatureCli.
        // --verify, --list-signatures, --export-cert have no batch equivalent
        // so they always route to PdfSignatureCli regardless.
        if ((hasFlag(args, "--sign") && !hasFlag(args, "--batch"))
         || hasFlag(args, "--verify")
         || hasFlag(args, "--list-signatures")
         || hasFlag(args, "--export-cert")) {
            // ---- SIGNATURE MODE ----
            new PdfSignatureCli().run(args);
            return;
        }

        if (hasFlag(args, "--encrypt")
         || hasFlag(args, "--decrypt")
         || hasFlag(args, "--change-password")
         || hasFlag(args, "--update-permissions")
         || hasFlag(args, "--inspect-security")) {
            // ---- SECURITY MODE ----
            new PdfSecurityCli().run(args);
            return;
        }

        if (hasFlag(args, "--generate-form")
         || hasFlag(args, "--extract-form")
         || hasFlag(args, "--fill-form")) {
            // ---- ACROFORM MODE ----
            new com.pdfcreator.forms.PdfFormCli().run(args);
            return;
        }

        if (hasFlag(args, "--batch")) {
            // ---- BATCH MODE ----
            if (templateId == null) {
                System.err.println("Error: --template-id is required for batch mode.");
                System.exit(1);
            }
            String dataDir    = getArg(args, "--data-dir",   null);
            String csvFile    = getArg(args, "--csv-file",   null);
            String outputDir  = getArg(args, "--output-dir", "output/");
            int    threads    = Integer.parseInt(getArg(args, "--threads", "4"));

            RenderPipeline pipeline = new RenderPipeline(templateFile, configFile)
                .withPdfA(hasFlag(args, "--pdfa"));
            List<BatchJob> jobs;

            if (dataDir != null) {
                jobs = new DirectoryBatchJobFactory(templateId, dataDir, outputDir).createJobs();
            } else if (csvFile != null) {
                String refCol = getArg(args, "--ref-col", "ref_id");
                String outCol = getArg(args, "--out-col", "output_file");
                jobs = new CsvBatchJobFactory(templateId, csvFile, outputDir, refCol, outCol).createJobs();
            } else {
                System.err.println("Error: --batch requires --data-dir or --csv-file.");
                System.exit(1);
                return;
            }

            // ── Signing: read all signing flags and wire BatchRunner.withSigning()
            // when --keystore is present. inputPath / outputPath are intentionally
            // not set here — BatchRunner.buildJobSigningOptions() sets them per-job.
            String bsKeystore   = getArg(args, "--keystore",          null);
            String bsKsPwd      = getArg(args, "--keystore-password", null);
            String bsKsType     = getArg(args, "--keystore-type",     "PKCS12");
            String bsAlias      = getArg(args, "--alias",             null);
            String bsReason     = getArg(args, "--reason",            null);
            String bsLocation   = getArg(args, "--location",          null);
            String bsContact    = getArg(args, "--contact",           null);
            String bsSignerName = getArg(args, "--signer-name",       null);
            String bsTsaUrl     = getArg(args, "--tsa-url",           null);
            boolean bsVisible   = hasFlag(args, "--visible");
            int   bsSigPage     = intArg(args,   "--sig-page",   -1);
            float bsSigX        = floatArg(args, "--sig-x",      50f);
            float bsSigY        = floatArg(args, "--sig-y",      50f);
            float bsSigW        = floatArg(args, "--sig-width",  200f);
            float bsSigH        = floatArg(args, "--sig-height", 60f);

            BatchRunner runner = new BatchRunner(pipeline, threads);

            if (bsKeystore != null) {
                if (bsKsPwd == null) {
                    System.err.println("Error: --keystore-password is required when --keystore is supplied in batch mode.");
                    System.exit(1);
                }
                com.pdfcreator.signature.SigningOptions signingTemplate =
                    new com.pdfcreator.signature.SigningOptions.Builder()
                        .keystorePath(bsKeystore)
                        .keystorePassword(bsKsPwd)
                        .keystoreType(bsKsType)
                        .keyAlias(bsAlias)
                        .reason(bsReason)
                        .location(bsLocation)
                        .contactInfo(bsContact)
                        .signerName(bsSignerName)
                        .tsaUrl(bsTsaUrl)
                        .visible(bsVisible)
                        .signaturePage(bsSigPage)
                        .signatureRect(bsSigX, bsSigY, bsSigW, bsSigH)
                        .buildTemplate();
                runner.withSigning(signingTemplate);
                System.out.printf("Signing        : enabled (keystore: %s, alias: %s)%n",
                    bsKeystore, bsAlias != null ? bsAlias : "auto");
                System.out.printf("TSA            : %s%n", bsTsaUrl != null ? bsTsaUrl : "none");
            }

            runner.run(jobs);

        } else if (templateId != null) {
            // ---- TEMPLATE MODE ----
            String dataFile = getArg(args, "--data-file", null);
            if (dataFile == null) {
                System.err.println("Error: --data-file is required with --template-id.");
                System.exit(1);
            }
            new RenderPipeline(templateFile, configFile)
                .withPdfA(hasFlag(args, "--pdfa"))
                .render(templateId, new JsonFileDataSource(dataFile), outputPath);

        } else {
            // ---- DIRECT MODE ----
            String configId   = getArg(args, "--config-id", DEFAULT_CONFIG_ID);
            String title      = getArg(args, "--title",     null);
            String author     = getArg(args, "--author",    null);
            String subject    = getArg(args, "--subject",   null);
            String keywords   = getArg(args, "--keywords",  null);
            String creator    = getArg(args, "--creator",   null);
            String inlineText = getArg(args, "--text",      null);
            String textFile   = getArg(args, "--text-file", null);
            List<String> imagePaths = resolveImagePaths(args);

            TextInputResolver textResolver = new TextInputResolver(inlineText, textFile);
            String bodyText = textResolver.resolve();

            PdfConfig config = new ConfigService(configFile).getConfig(configId);
            System.out.println("Mode   : direct | Config: " + config.getId() + " | Output: " + outputPath);

            renderDirect(config, title, author, subject, keywords, creator, bodyText, imagePaths, outputPath);
        }

        System.out.println("Done.");
    }

    // -----------------------------------------------------------------------

    private static void renderDirect(PdfConfig config, String title, String author,
                                      String subject, String keywords, String creator,
                                      String bodyText, List<String> imagePaths,
                                      String outputPath) throws Exception {
        List<TemplateSection> sections = new ArrayList<>();
        if (title    != null) sections.add(new TemplateSection.Builder(TemplateSection.Type.HEADING).content(title).build());
        if (author   != null) sections.add(new TemplateSection.Builder(TemplateSection.Type.BODY).content("Author: " + author).build());
        if (bodyText != null) sections.add(new TemplateSection.Builder(TemplateSection.Type.BODY).content(bodyText).build());
        for (String p : imagePaths)
            sections.add(new TemplateSection.Builder(TemplateSection.Type.IMAGE).content(p).build());

        if (sections.isEmpty()) { System.out.println("Nothing to render."); return; }

        PDRectangle pageSize = switch (config.getPageSize().toUpperCase()) {
            case "LETTER" -> PDRectangle.LETTER; case "A3" -> PDRectangle.A3; default -> PDRectangle.A4;
        };

        DocumentData emptyData = new DocumentData.Builder().build();
        SectionRendererRegistry registry = new SectionRendererRegistry();

        try (PDDocument document = new PDDocument()) {
            // Apply document metadata to PDDocumentInformation
            org.apache.pdfbox.pdmodel.PDDocumentInformation info =
                document.getDocumentInformation();
            if (title    != null && !title.isBlank())    info.setTitle(title);
            if (author   != null && !author.isBlank())   info.setAuthor(author);
            if (subject  != null && !subject.isBlank())  info.setSubject(subject);
            if (keywords != null && !keywords.isBlank()) info.setKeywords(keywords);
            info.setCreator(creator != null && !creator.isBlank() ? creator : "PdfCreator");
            info.setProducer("PdfCreator / Apache PDFBox 3");
            java.util.Calendar now = java.util.Calendar.getInstance();
            info.setCreationDate(now);
            info.setModificationDate(now);

            FontLoader fontLoader = new FontLoader(document, false);
            PageContext ctx = new PageContext(document, config, pageSize);
            ctx.open();
            for (TemplateSection s : sections)
                registry.get(s.getType()).render(s, ctx, config, emptyData, document, fontLoader);
            ctx.close();
            document.save(outputPath);
            System.out.println("Pages: " + ctx.getPageNumber());
        }
    }

    private static void printHelp() {
        System.out.println("""
            PdfCreator — Scalable template-based PDF generation

            TEMPLATE MODE:
              --template-id <id>       Template to use
              --data-file <path>       JSON data file (scalars + lists for tables)
              --output <file>          Output PDF (default: output.pdf)

            BATCH MODE:
              --batch
              --template-id <id>       Template to use for all jobs
              --data-dir <path>        Directory of .json data files (one PDF per file)
              --csv-file <path>        CSV of scalar data (one row = one PDF)
              --output-dir <path>      Directory for output PDFs (default: output/)
              --threads <n>            Parallel threads (default: 4)
              --ref-col <col>          CSV column to use as reference ID (default: ref_id)
              --out-col <col>          CSV column for output filename (default: output_file)
              --pdfa                   Generate PDF/A-1b compliant output

            BATCH SIGNING (add to any batch command to sign every PDF):
              --keystore <path>        PKCS12 or JKS keystore (enables signing)
              --keystore-password <p>  Keystore password (required with --keystore)
              --keystore-type <type>   PKCS12 (default) or JKS
              --alias <name>           Key alias (default: auto-detect first)
              --reason <text>          Reason for signing
              --location <text>        Signing location
              --contact <email>        Contact information
              --signer-name <name>     Override display name (default: cert CN)
              --tsa-url <url>          RFC 3161 TSA endpoint for trusted timestamp
              --visible                Render a visible signature box on the page
              --sig-page <n>           Page for visible box, 1-based (default: last)
              --sig-x <pts>            Box left edge in PDF points (default: 50)
              --sig-y <pts>            Box bottom edge in PDF points (default: 50)
              --sig-width <pts>        Box width in PDF points (default: 200)
              --sig-height <pts>       Box height in PDF points (default: 60)

            DIRECT MODE:
              --config-id <id>         Config preset (default: default)
              --title <text>           Document title (written to page and metadata)
              --author <text>          Author name (metadata only)
              --subject <text>         Document subject (metadata only)
              --keywords <text>        Space-separated keywords (metadata only)
              --creator <text>         Creating application name (metadata only)
              --text / --text-file / --image / --images
              --output <file>

            EXTRACT MODE (text):
              --extract
              --input <path>           PDF file to extract text from (required)
              --output <path>          Write extracted text to file (default: stdout)
              --start-page <n>         First page, 1-based (default: 1)
              --end-page <n>           Last page, 1-based (default: last)
              --no-sort                Disable position-based text sorting
              --strip-whitespace       Collapse whitespace into single space
              --no-metadata            Suppress metadata header block
              --no-per-page            Print flat text without page separators
              --password <pwd>         Password for encrypted PDFs

            EXTRACT MODE (images):
              --extract-images
              --input <path>           PDF file to extract images from (required)
              --output-dir <path>      Directory to save images (default: extracted-images/)
              --img-format <fmt>       png (default, lossless) or jpg (lossy)
              --min-width <n>          Skip images narrower than N pixels (default: 10)
              --min-height <n>         Skip images shorter than N pixels (default: 10)
              --start-page <n>         First page to scan, 1-based (default: 1)
              --end-page <n>           Last page to scan, 1-based (default: last)
              --password <pwd>         Password for encrypted PDFs

            MERGE MODE:
              --merge
              --inputs <p1,p2,...>     Comma-separated PDF paths to merge (required, min 2)
              --output <path>          Output PDF path (default: merged.pdf)
              --passwords <pw1,pw2,..> Passwords aligned with --inputs (use empty for none)
              --no-copy-meta           Do not copy metadata from the first input

            SPLIT MODE:
              --split
              --input <path>           PDF to split (required)
              --output-dir <path>      Directory for output files (default: split-output/)
              --password <pwd>         Password if source PDF is encrypted
              --prefix <name>          Filename prefix (default: stem of input filename)
              --every-n-pages <n>      One output file per N pages
              --into-parts <n>         Divide into N roughly equal parts
              --page-ranges <ranges>   Explicit ranges, e.g. "1-3,4-6,7-10"

            ENCRYPT MODE:
              --encrypt
              --input <path>           Source PDF (required)
              --output <path>          Encrypted output PDF (required)
              --owner-password <pwd>   Owner password — full access (required)
              --user-password <pwd>    User password — to open the file (default: "" = no prompt)
              --preset <n>             all-allowed | read-only | print-only | no-copy
              --deny-print             Deny full-resolution printing
              --deny-copy              Deny copying text/graphics
              --deny-modify            Deny modifying document content
              --deny-annotations       Deny editing annotations and form fields
              --deny-fill-forms        Deny filling in existing form fields
              --deny-accessibility     Deny text extraction for assistive tech
              --deny-assemble          Deny inserting/deleting/rotating pages

            DECRYPT MODE:
              --decrypt
              --input <path>           Encrypted source PDF (required)
              --output <path>          Plain output PDF (required)
              --owner-password <pwd>   Owner password (required)

            CHANGE PASSWORD MODE:
              --change-password
              --input <path>           Encrypted source PDF (required)
              --output <path>          Re-encrypted output PDF (required)
              --owner-password <pwd>   Current owner password (required)
              --new-owner-password <p> New owner password
              --new-user-password <p>  New user password (default: "" if omitted)

            UPDATE PERMISSIONS MODE:
              --update-permissions
              --input <path>           Encrypted source PDF (required)
              --output <path>          Re-encrypted output PDF (required)
              --owner-password <pwd>   Owner password (required)
              --preset <n>             Preset permission set (see ENCRYPT MODE)
              --deny-*                 Individual deny flags (see ENCRYPT MODE)

            INSPECT SECURITY MODE:
              --inspect-security
              --input <path>           PDF to inspect (required)
              --owner-password <pwd>   Password if PDF is encrypted (optional for plain PDFs)

            GENERATE FORM MODE:
              --generate-form
              --output <path>          Output PDF path (required)
              --title <text>           Form title heading (default: "Form")
              --config-id <id>         Style config preset (default: default)
              --field <spec>           Field definition (repeatable, see format below)

              Field spec format:  name=<n>,type=<t>[,label=<l>][,required][,readonly]
                                  [,default=<v>][,options=<a|b|c>][,tooltip=<text>]
                                  [,height=<pts>][,rows=<n>][,toggleSize=<pts>]
              Field types:  text | multiline | checkbox | radio | combo | listbox

            EXTRACT FORM MODE:
              --extract-form
              --input <path>           PDF with AcroForm to inspect (required)
              --output <json-path>     Write field info to JSON file (default: stdout)
              --password <pwd>         Password for encrypted PDFs (optional)

            FILL FORM MODE:
              --fill-form
              --input <path>           PDF with AcroForm to fill (required)
              --output <path>          Filled PDF output path (required)
              --field "name=<n>,value=<v>"  Field value to set (repeatable)
              --flatten                Flatten interactive fields after filling
              --password <pwd>         Password for encrypted PDFs (optional)

            PRINT MODE:
              --print
              --input <path>           PDF file to print (required)
              --printer <n>         Printer name (default: system default)
              --start-page <n>         First page to print, 1-based (default: 1)
              --end-page <n>           Last page to print, 1-based (default: last)
              --copies <n>             Number of copies (default: 1)
              --sides <mode>           simplex | duplex-long | duplex-short
              --scaling <mode>         fit | shrink | actual (default: fit)
              --silent                 Print without showing a dialog
              --password <pwd>         Password for encrypted PDFs
              --job-name <n>        Job name shown in OS print queue

              --list-printers          List available printers and exit

            SHARED OPTIONS:
              --template-file <path>   Template definitions (default: templates/pdf-templates.json)
              --config-file <path>     Config presets (default: configs/pdf-configs.json)

            EXAMPLES:
              # Extract all text from a PDF to stdout
              java -jar pdf-creator.jar --extract \\
                --input output/statement.pdf

              # Extract pages 1-2, save to file
              java -jar pdf-creator.jar --extract \\
                --input output/statement.pdf --output extracted.txt \\
                --start-page 1 --end-page 2

              # Normalised, no metadata, flat output
              java -jar pdf-creator.jar --extract \\
                --input output/statement.pdf \\
                --strip-whitespace --no-metadata --no-per-page

              # Extract all images from a PDF
              java -jar pdf-creator.jar --extract-images \\
                --input output/statement.pdf

              # Extract images as JPEGs, min 100x100px, pages 1-3
              java -jar pdf-creator.jar --extract-images \\
                --input output/report.pdf \\
                --output-dir output/images/ \\
                --img-format jpg --min-width 100 --min-height 100 \\
                --start-page 1 --end-page 3

              # Merge three PDFs into one
              java -jar pdf-creator.jar --merge \\
                --inputs jan.pdf,feb.pdf,mar.pdf --output q1.pdf

              # Merge with a password-protected input
              java -jar pdf-creator.jar --merge \\
                --inputs open.pdf,secret.pdf --passwords ,mypassword \\
                --output merged.pdf

              # Split into chunks of 5 pages
              java -jar pdf-creator.jar --split \\
                --input report.pdf --every-n-pages 5 --output-dir split/

              # Split into 4 equal parts
              java -jar pdf-creator.jar --split \\
                --input report.pdf --into-parts 4 --output-dir split/

              # Split at explicit page boundaries
              java -jar pdf-creator.jar --split \\
                --input report.pdf --page-ranges "1-3,4-6,7-10" --output-dir split/

              # List available printers
              java -jar pdf-creator.jar --list-printers

              # Print to default printer (shows dialog)
              java -jar pdf-creator.jar --print --input report.pdf

              # Print silently to a named printer, pages 1-3, duplex
              java -jar pdf-creator.jar --print --input report.pdf \\
                --printer "HP LaserJet 400" --silent \\
                --start-page 1 --end-page 3 --sides duplex-long

              # Single bank statement
              java -jar pdf-creator.jar \\
                --template-id bank-statement \\
                --data-file data/customer-12345.json \\
                --output statements/12345-feb26.pdf

              # Batch: entire customer directory, 8 threads
              java -jar pdf-creator.jar --batch \\
                --template-id bank-statement \\
                --data-dir data/statements/feb26/ \\
                --output-dir output/statements/feb26/ \\
                --threads 8

              # Batch: from CSV (simple notifications)
              java -jar pdf-creator.jar --batch \\
                --template-id notification-letter \\
                --csv-file data/notifications.csv \\
                --output-dir output/letters/
            """);
    }

    private static List<String> resolveImagePaths(String[] args) {
        String multi = getArg(args, "--images", null);
        if (multi != null) return Arrays.stream(multi.split(",")).map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toList());
        String single = getArg(args, "--image", null);
        if (single != null) return List.of(single.trim());
        return List.of();
    }

    private static String getArg(String[] args, String flag, String def) {
        for (int i = 0; i < args.length - 1; i++) if (args[i].equals(flag)) return args[i + 1];
        return def;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) if (a.equals(flag)) return true;
        return false;
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
}
