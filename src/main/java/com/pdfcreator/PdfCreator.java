package com.pdfcreator;

import com.pdfcreator.config.PdfConfig;
import com.pdfcreator.generator.PdfGenerator;
import com.pdfcreator.service.ConfigService;
import com.pdfcreator.template.TemplateRenderer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Entry point — thin CLI layer.
 *
 * Two operating modes:
 *
 *   TEMPLATE MODE  (--template-id + --data-file)
 *     Loads a named template, fills {{placeholders}} from a JSON data file,
 *     and generates the PDF. Config preset is determined by the template.
 *
 *   DIRECT MODE    (--title / --text / --text-file, as before)
 *     Generates a PDF from raw CLI arguments. Config preset set via --config-id.
 *
 * If --template-id is present, template mode is used. Otherwise direct mode.
 */
public class PdfCreator {

    private static final String DEFAULT_OUTPUT        = "output.pdf";
    private static final String DEFAULT_CONFIG_FILE   = "configs/pdf-configs.json";
    private static final String DEFAULT_CONFIG_ID     = "default";
    private static final String DEFAULT_TEMPLATE_FILE = "templates/pdf-templates.json";

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || hasFlag(args, "--help")) {
            printHelp();
            return;
        }

        String outputPath    = getArg(args, "--output",        DEFAULT_OUTPUT);
        String configFile    = getArg(args, "--config-file",   DEFAULT_CONFIG_FILE);
        String templateFile  = getArg(args, "--template-file", DEFAULT_TEMPLATE_FILE);
        String templateId    = getArg(args, "--template-id",   null);

        if (templateId != null) {
            // ---- TEMPLATE MODE ----
            String dataFile = getArg(args, "--data-file", null);
            if (dataFile == null) {
                System.err.println("Error: --data-file is required when using --template-id.");
                System.err.println("Usage: --template-id <id> --data-file <path> --output <file>");
                System.exit(1);
            }
            new TemplateRenderer(templateFile, configFile)
                .render(templateId, dataFile, outputPath);

        } else {
            // ---- DIRECT MODE ----
            String configId   = getArg(args, "--config-id", DEFAULT_CONFIG_ID);
            String title      = getArg(args, "--title",     null);
            String author     = getArg(args, "--author",    null);
            String inlineText = getArg(args, "--text",      null);
            String textFile   = getArg(args, "--text-file", null);
            List<String> imagePaths = resolveImagePaths(args);

            TextInputResolver textResolver = new TextInputResolver(inlineText, textFile);
            String bodyText = textResolver.resolve();

            ConfigService configService = new ConfigService(configFile);
            PdfConfig config;
            try {
                config = configService.getConfig(configId);
            } catch (IllegalArgumentException e) {
                System.err.println("Error: " + e.getMessage());
                System.err.println("Available configs: " + configService.listAvailableIds());
                System.exit(1);
                return;
            }

            System.out.println("Mode          : direct");
            System.out.println("Using config  : " + config.getId());
            System.out.println("Output file   : " + outputPath);
            System.out.println("Text source   : " + textResolver.describeSource());
            if (!imagePaths.isEmpty()) System.out.println("Images        : " + imagePaths);

            new PdfGenerator().generate(config, title, author, bodyText, imagePaths, outputPath);
        }

        System.out.println("Done: " + outputPath);
    }

    // -----------------------------------------------------------------------

    private static List<String> resolveImagePaths(String[] args) {
        String multi = getArg(args, "--images", null);
        if (multi != null) {
            return Arrays.stream(multi.split(","))
                         .map(String::trim)
                         .filter(s -> !s.isBlank())
                         .collect(Collectors.toList());
        }
        String single = getArg(args, "--image", null);
        if (single != null) return List.of(single.trim());
        return List.of();
    }

    private static void printHelp() {
        System.out.println("""
            PdfCreator - Config-driven, template-based PDF generation using Apache PDFBox

            Usage:
              java -jar pdf-creator.jar [options]

            TEMPLATE MODE  (provide --template-id and --data-file):
              --template-id <id>       Template to use (from pdf-templates.json)
              --data-file <path>       JSON file with placeholder values
              --template-file <path>   Template definitions file (default: templates/pdf-templates.json)
              --output <file>          Output PDF path (default: output.pdf)

            DIRECT MODE  (no --template-id):
              --config-id <id>         Config preset (default: "default")
              --config-file <path>     Config file path (default: configs/pdf-configs.json)
              --title <text>           Document title
              --author <text>          Document author
              --text <text>            Inline body text
              --text-file <path>       Plain text file to use as body
              --image <path>           Single image to embed
              --images <p1,p2,...>     Multiple images (comma-separated)
              --output <file>          Output PDF path (default: output.pdf)

            Examples:
              # Template mode — invoice
              java -jar pdf-creator.jar \\
                --template-id invoice \\
                --data-file data/invoice-acme.json \\
                --output invoices/acme-0042.pdf

              # Template mode — report
              java -jar pdf-creator.jar \\
                --template-id report \\
                --data-file data/q4-report-data.json \\
                --output q4-report.pdf

              # Direct mode (as before)
              java -jar pdf-creator.jar \\
                --config-id report --title "Q4 Analysis" \\
                --text-file ./q4.txt --output q4.pdf
            """);
    }

    private static String getArg(String[] args, String flag, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(flag)) return args[i + 1];
        }
        return defaultValue;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) if (a.equals(flag)) return true;
        return false;
    }
}


