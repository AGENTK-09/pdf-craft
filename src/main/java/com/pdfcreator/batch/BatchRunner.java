package com.pdfcreator.batch;

import com.pdfcreator.pipeline.RenderPipeline;
import com.pdfcreator.signature.PdfSigner;
import com.pdfcreator.signature.SigningOptions;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Executes a list of BatchJobs, optionally in parallel.
 *
 * When a SigningOptions template is supplied via withSigning(), each PDF is
 * signed immediately after generation using PdfSigner. The signed file
 * replaces the unsigned output — no intermediate unsigned files are kept
 * on disk (the unsigned file is written then atomically replaced by the
 * signed version in-place).
 *
 * Design:
 *   - One shared RenderPipeline (templates and configs are cached inside it).
 *   - Jobs run on a thread pool of configurable size.
 *   - Each job renders independently; no shared mutable state between jobs.
 *   - PdfSigner is NOT shared across threads — a new instance is created per
 *     job when signing is enabled, because PdfSigner holds per-call state.
 *   - Results are collected and a summary is printed when all jobs complete.
 *
 * Usage (render only):
 *   new BatchRunner(pipeline, 4).run(jobs);
 *
 * Usage (render + sign):
 *   SigningOptions signingTemplate = new SigningOptions.Builder()
 *       .keystorePath("bank.p12")
 *       .keystorePassword("secret")
 *       .reason("Monthly Account Statement")
 *       .location("Mumbai, India")
 *       .tsaUrl("http://timestamp.digicert.com")
 *       .build();  // inputPath and outputPath are set per-job
 *   new BatchRunner(pipeline, 4).withSigning(signingTemplate).run(jobs);
 */
public class BatchRunner {

    private static final Logger logger = Logger.getLogger(BatchRunner.class.getName());

    private final RenderPipeline pipeline;
    private final int            threads;

    /**
     * Optional signing template. When non-null, every rendered PDF is signed.
     * inputPath and outputPath fields in this template are overridden per job.
     */
    private SigningOptions signingTemplate = null;

    public BatchRunner(RenderPipeline pipeline, int threads) {
        this.pipeline = pipeline;
        this.threads  = Math.max(1, threads);
    }

    /**
     * Enables sign-on-generate for this batch run.
     *
     * The signingTemplate provides all signing parameters (keystore, reason,
     * location, TSA URL etc.) except inputPath and outputPath, which are set
     * automatically per job from the job's output path.
     *
     * @param template SigningOptions with all fields set except input/output paths
     * @return this, for fluent chaining
     */
    public BatchRunner withSigning(SigningOptions template) {
        this.signingTemplate = template;
        return this;
    }

    /**
     * Runs all jobs and returns the results.
     * Blocks until all jobs are complete (success or failure).
     */
    public List<BatchResult> run(List<BatchJob> jobs) throws InterruptedException {
        if (jobs.isEmpty()) {
            logger.warning("BatchRunner.run() called with an empty job list.");
            return List.of();
        }

        System.out.printf("Batch run: %d jobs, %d thread(s)%s%n",
            jobs.size(), threads,
            signingTemplate != null ? ", signing enabled" : "");
        long batchStart = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<BatchResult>> futures = jobs.stream()
            .map(job -> executor.submit(() -> executeJob(job)))
            .collect(Collectors.toList());

        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

        List<BatchResult> results = futures.stream()
            .map(f -> { try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); } })
            .collect(Collectors.toList());

        printSummary(results, System.currentTimeMillis() - batchStart);
        return results;
    }

    private BatchResult executeJob(BatchJob job) {
        long start = System.currentTimeMillis();
        try {
            // Ensure output directory exists
            Path outPath = Path.of(job.getOutputPath());
            Files.createDirectories(outPath.getParent() != null
                ? outPath.getParent() : Path.of("."));

            // Step 1: Render
            pipeline.render(job.getTemplateId(), job.getDataSource(), job.getOutputPath());

            // Step 2: Sign (if signing enabled)
            if (signingTemplate != null) {
                signRenderedPdf(job.getOutputPath());
            }

            long duration = System.currentTimeMillis() - start;
            logger.info("OK: " + job.getReferenceId() + " (" + duration + "ms)"
                + (signingTemplate != null ? " [signed]" : ""));
            return BatchResult.success(job, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            logger.severe("FAIL: " + job.getReferenceId() + " — " + e.getMessage());
            return BatchResult.failure(job, duration, e);
        }
    }

    /**
     * Signs a rendered PDF in place.
     *
     * The unsigned PDF at renderedPath is used as input; the signed version
     * is written to a temporary file then moved back to renderedPath,
     * replacing the unsigned version atomically. This ensures the final file
     * at renderedPath is always the signed version.
     */
    private void signRenderedPdf(String renderedPath) throws Exception {
        String tempSignedPath = renderedPath + ".signed.tmp";
        try {
            SigningOptions jobOpts = buildJobSigningOptions(renderedPath, tempSignedPath);
            new PdfSigner().sign(jobOpts);

            // Atomically replace unsigned with signed
            File signedFile   = new File(tempSignedPath);
            File unsignedFile = new File(renderedPath);
            if (!signedFile.renameTo(unsignedFile)) {
                // renameTo can fail across file systems — fall back to copy + delete
                Files.move(signedFile.toPath(), unsignedFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            logger.fine("Signed and replaced: " + renderedPath);
        } catch (Exception e) {
            // Clean up temp file on failure
            new File(tempSignedPath).delete();
            throw e;
        }
    }

    /**
     * Builds a per-job SigningOptions by copying all fields from the template
     * but setting inputPath = renderedPath and outputPath = tempSignedPath.
     */
    private SigningOptions buildJobSigningOptions(String inputPath,
                                                   String outputPath) {
        return new SigningOptions.Builder()
            .inputPath(inputPath)
            .outputPath(outputPath)
            .keystorePath(signingTemplate.getKeystorePath())
            .keystorePassword(signingTemplate.getKeystorePassword())
            .keystoreType(signingTemplate.getKeystoreType())
            .keyAlias(signingTemplate.getKeyAlias())
            .reason(signingTemplate.getReason())
            .location(signingTemplate.getLocation())
            .contactInfo(signingTemplate.getContactInfo())
            .signerName(signingTemplate.getSignerName())
            .tsaUrl(signingTemplate.getTsaUrl())
            .visible(signingTemplate.isVisible())
            .signaturePage(signingTemplate.getSignaturePage())
            .signatureRect(
                signingTemplate.getSigX(),
                signingTemplate.getSigY(),
                signingTemplate.getSigWidth(),
                signingTemplate.getSigHeight())
            .build();
    }

    private void printSummary(List<BatchResult> results, long totalMs) {
        long succeeded = results.stream().filter(BatchResult::isSuccess).count();
        long failed    = results.size() - succeeded;

        System.out.println();
        System.out.println("═".repeat(60));
        System.out.printf("Batch complete: %d OK, %d FAILED, total %.1fs%n",
            succeeded, failed, totalMs / 1000.0);
        System.out.println("─".repeat(60));
        results.forEach(r -> System.out.println("  " + r));
        if (failed > 0) {
            System.out.println();
            System.out.println("Failed jobs:");
            results.stream()
                   .filter(r -> !r.isSuccess())
                   .forEach(r -> System.out.println("  " + r.getJob().getReferenceId()
                       + " → " + r.getError().getMessage()));
        }
        System.out.println("═".repeat(60));
    }
}
