package com.pdfcreator.batch;

/**
 * The outcome of a single BatchJob render.
 *
 * Used by BatchRunner to accumulate results and produce a summary.
 * Distinguishes between success, failure, and the specific error if failed.
 */
public class BatchResult {

    private final BatchJob  job;
    private final boolean   success;
    private final long      durationMs;
    private final Throwable error;        // null on success

    private BatchResult(BatchJob job, boolean success, long durationMs, Throwable error) {
        this.job        = job;
        this.success    = success;
        this.durationMs = durationMs;
        this.error      = error;
    }

    public static BatchResult success(BatchJob job, long durationMs) {
        return new BatchResult(job, true, durationMs, null);
    }

    public static BatchResult failure(BatchJob job, long durationMs, Throwable error) {
        return new BatchResult(job, false, durationMs, error);
    }

    public BatchJob  getJob()       { return job; }
    public boolean   isSuccess()    { return success; }
    public long      getDurationMs(){ return durationMs; }
    public Throwable getError()     { return error; }

    @Override
    public String toString() {
        if (success)
            return String.format("OK   [%4dms] %s", durationMs, job.getReferenceId());
        else
            return String.format("FAIL [%4dms] %s — %s", durationMs,
                job.getReferenceId(), error.getMessage());
    }
}
