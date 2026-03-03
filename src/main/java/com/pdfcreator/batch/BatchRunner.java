package com.pdfcreator.batch;

import com.pdfcreator.pipeline.RenderPipeline;

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
 * Design:
 *   - One shared RenderPipeline (templates and configs are cached inside it).
 *   - Jobs run on a thread pool of configurable size.
 *   - Each job renders independently; no shared mutable state between jobs.
 *   - Results are collected and a summary is printed when all jobs complete.
 *
 * Thread safety:
 *   RenderPipeline itself is thread-safe because:
 *     - TemplateService and ConfigService use ConcurrentHashMap caches.
 *     - Each job creates its own PDDocument (PDFBox documents are not thread-safe
 *       and must never be shared across threads).
 *     - The renderer registry holds stateless SectionRenderer instances.
 *
 * Usage:
 *   List<BatchJob> jobs = new CsvBatchJobFactory(...).createJobs();
 *   new BatchRunner(pipeline, 4).run(jobs);
 *
 * Alternatively via CLI:
 *   java -jar pdf-creator.jar --batch --template-id bank-statement \
 *     --csv-file customers.csv --output-dir statements/ --threads 8
 */
public class BatchRunner {

    private static final Logger logger = Logger.getLogger(BatchRunner.class.getName());

    private final RenderPipeline pipeline;
    private final int            threads;

    public BatchRunner(RenderPipeline pipeline, int threads) {
        this.pipeline = pipeline;
        this.threads  = Math.max(1, threads);
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

        System.out.printf("Batch run: %d jobs, %d thread(s)%n", jobs.size(), threads);
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

            pipeline.render(job.getTemplateId(), job.getDataSource(), job.getOutputPath());
            long duration = System.currentTimeMillis() - start;
            logger.info("OK: " + job.getReferenceId() + " (" + duration + "ms)");
            return BatchResult.success(job, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            logger.severe("FAIL: " + job.getReferenceId() + " — " + e.getMessage());
            return BatchResult.failure(job, duration, e);
        }
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
