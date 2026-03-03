package com.pdfcreator;

import com.pdfcreator.batch.*;
import com.pdfcreator.config.PdfConfig;
import com.pdfcreator.datasource.DocumentData;
import com.pdfcreator.datasource.JsonFileDataSource;
import com.pdfcreator.generator.PageContext;
import com.pdfcreator.pipeline.RenderPipeline;
import com.pdfcreator.renderer.SectionRendererRegistry;
import com.pdfcreator.service.ConfigService;
import com.pdfcreator.template.DocumentMetadata;
import com.pdfcreator.template.TemplateSection;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

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
        if (args.length == 0 || hasFlag(args, "--help")) { printHelp(); return; }

        String outputPath   = getArg(args, "--output",        DEFAULT_OUTPUT);
        String configFile   = getArg(args, "--config-file",   DEFAULT_CONFIG_FILE);
        String templateFile = getArg(args, "--template-file", DEFAULT_TEMPLATE_FILE);
        String templateId   = getArg(args, "--template-id",   null);

        if (hasFlag(args, "--batch")) {
            // ---- BATCH MODE ----
            if (templateId == null) {
                System.err.println("Error: --template-id is required for batch mode.");
                System.exit(1);
            }
            String dataDir    = getArg(args, "--data-dir",  null);
            String csvFile    = getArg(args, "--csv-file",  null);
            String outputDir  = getArg(args, "--output-dir","output/");
            int    threads    = Integer.parseInt(getArg(args, "--threads", "4"));

            RenderPipeline pipeline = new RenderPipeline(templateFile, configFile);
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

            new BatchRunner(pipeline, threads).run(jobs);

        } else if (templateId != null) {
            // ---- TEMPLATE MODE ----
            String dataFile = getArg(args, "--data-file", null);
            if (dataFile == null) {
                System.err.println("Error: --data-file is required with --template-id.");
                System.exit(1);
            }
            new RenderPipeline(templateFile, configFile)
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

            PageContext ctx = new PageContext(document, config, pageSize);
            ctx.open();
            for (TemplateSection s : sections)
                registry.get(s.getType()).render(s, ctx, config, emptyData, document);
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

            DIRECT MODE:
              --config-id <id>         Config preset (default: default)
              --title <text>           Document title (written to page and metadata)
              --author <text>          Author name (metadata only)
              --subject <text>         Document subject (metadata only)
              --keywords <text>        Space-separated keywords (metadata only)
              --creator <text>         Creating application name (metadata only)
              --text / --text-file / --image / --images
              --output <file>

            SHARED OPTIONS:
              --template-file <path>   Template definitions (default: templates/pdf-templates.json)
              --config-file <path>     Config presets (default: configs/pdf-configs.json)

            EXAMPLES:
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
}
