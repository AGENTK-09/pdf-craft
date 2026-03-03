package com.pdfcreator.batch;

import com.pdfcreator.datasource.JsonFileDataSource;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Creates BatchJobs from a directory of JSON data files.
 *
 * Each .json file in the directory becomes one BatchJob.
 * The reference ID defaults to the filename without extension.
 * The output filename defaults to <refId>.pdf in the outputDir.
 *
 * Best suited for banking where each customer has a full data file
 * with both scalar values and transaction lists.
 *
 * Usage:
 *   List<BatchJob> jobs = new DirectoryBatchJobFactory(
 *       "bank-statement",     // templateId
 *       "data/statements/",   // dataDir — contains CUST-001.json, CUST-002.json, ...
 *       "output/statements/"  // outputDir
 *   ).createJobs();
 *
 * File name convention:
 *   data/statements/CUST-001.json  →  output/statements/CUST-001.pdf
 */
public class DirectoryBatchJobFactory {

    private static final Logger logger = Logger.getLogger(DirectoryBatchJobFactory.class.getName());

    private final String templateId;
    private final String dataDir;
    private final String outputDir;

    public DirectoryBatchJobFactory(String templateId, String dataDir, String outputDir) {
        this.templateId = templateId;
        this.dataDir    = dataDir;
        this.outputDir  = outputDir.endsWith("/") ? outputDir : outputDir + "/";
    }

    public List<BatchJob> createJobs() throws IOException {
        Path dataDirPath = Path.of(dataDir);
        if (!Files.exists(dataDirPath) || !Files.isDirectory(dataDirPath))
            throw new FileNotFoundException("Data directory not found: " + dataDir);

        List<Path> jsonFiles = Files.list(dataDirPath)
            .filter(p -> p.toString().endsWith(".json"))
            .sorted()
            .collect(Collectors.toList());

        if (jsonFiles.isEmpty()) {
            logger.warning("No .json files found in: " + dataDir);
            return List.of();
        }

        List<BatchJob> jobs = new ArrayList<>();
        for (Path jsonFile : jsonFiles) {
            String filename  = jsonFile.getFileName().toString();
            String refId     = filename.substring(0, filename.lastIndexOf('.'));
            String outputPath = outputDir + refId + ".pdf";

            jobs.add(new BatchJob(
                refId,
                templateId,
                new JsonFileDataSource(jsonFile.toString()),
                outputPath
            ));
        }

        logger.info("Created " + jobs.size() + " batch jobs from directory: " + dataDir);
        return jobs;
    }
}
