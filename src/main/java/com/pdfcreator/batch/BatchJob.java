package com.pdfcreator.batch;

import com.pdfcreator.datasource.DataSource;

/**
 * Represents a single unit of work in a batch render run.
 *
 * A batch job knows:
 *   - which template to use
 *   - where to get its data (any DataSource implementation)
 *   - where to write the output PDF
 *   - an optional reference ID for logging and audit trails
 *
 * BatchJobs are produced by BatchJobFactory implementations
 * (e.g. CsvBatchJobFactory, DirectoryBatchJobFactory).
 */
public class BatchJob {

    private final String     referenceId;  // e.g. customer number, used in logs
    private final String     templateId;
    private final DataSource dataSource;
    private final String     outputPath;

    public BatchJob(String referenceId, String templateId,
                    DataSource dataSource, String outputPath) {
        this.referenceId = referenceId;
        this.templateId  = templateId;
        this.dataSource  = dataSource;
        this.outputPath  = outputPath;
    }

    public String     getReferenceId() { return referenceId; }
    public String     getTemplateId()  { return templateId; }
    public DataSource getDataSource()  { return dataSource; }
    public String     getOutputPath()  { return outputPath; }

    @Override
    public String toString() {
        return String.format("BatchJob[ref=%s, template=%s, output=%s]",
            referenceId, templateId, outputPath);
    }
}
