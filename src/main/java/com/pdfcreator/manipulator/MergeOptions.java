package com.pdfcreator.manipulator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for a PDF merge operation.
 *
 * Describes the ordered list of input PDFs to merge and where to write the
 * output. Each input entry carries its own optional password so password-
 * protected PDFs can be included in the same merge as unprotected ones.
 *
 * Usage:
 *
 *   MergeOptions opts = new MergeOptions.Builder()
 *       .addInput("jan.pdf")
 *       .addInput("feb.pdf")
 *       .addInput("mar-protected.pdf", "secret")
 *       .output("q1-combined.pdf")
 *       .copyMetadataFromFirst(true)
 *       .build();
 *
 * Input ordering matters — pages are appended in the order inputs are added.
 */
public class MergeOptions {

    /**
     * A single input PDF entry: a file path plus an optional password.
     * Password is null for unprotected PDFs.
     */
    public static class InputEntry {
        private final String path;
        private final String password;   // null = no password

        public InputEntry(String path, String password) {
            this.path     = path;
            this.password = password;
        }

        public String getPath()     { return path; }
        public String getPassword() { return password; }
        public boolean hasPassword(){ return password != null && !password.isEmpty(); }

        @Override
        public String toString() {
            return "InputEntry[path=" + path
                + (hasPassword() ? ", password=***" : "") + "]";
        }
    }

    private final List<InputEntry> inputs;
    private final String           outputPath;

    /**
     * When true, the Title, Author, Subject, Keywords, Creator fields from the
     * first input document are copied to the merged output.
     * Default: true.
     */
    private final boolean copyMetadataFromFirst;

    private MergeOptions(Builder b) {
        this.inputs               = Collections.unmodifiableList(b.inputs);
        this.outputPath           = b.outputPath;
        this.copyMetadataFromFirst = b.copyMetadataFromFirst;
    }

    public List<InputEntry> getInputs()             { return inputs; }
    public String           getOutputPath()         { return outputPath; }
    public boolean          isCopyMetadataFromFirst(){ return copyMetadataFromFirst; }

    @Override
    public String toString() {
        return String.format("MergeOptions[inputs=%d, output=%s, copyMeta=%b]",
            inputs.size(), outputPath, copyMetadataFromFirst);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private final List<InputEntry> inputs = new ArrayList<>();
        private String  outputPath            = "merged.pdf";
        private boolean copyMetadataFromFirst = true;

        /** Add an unprotected input PDF. */
        public Builder addInput(String path) {
            inputs.add(new InputEntry(path, null));
            return this;
        }

        /** Add a password-protected input PDF. */
        public Builder addInput(String path, String password) {
            inputs.add(new InputEntry(path, password));
            return this;
        }

        /** Destination path for the merged PDF (default: merged.pdf). */
        public Builder output(String path) {
            this.outputPath = path;
            return this;
        }

        /**
         * Whether to copy document metadata (title, author, etc.) from the first
         * input document to the output (default: true).
         */
        public Builder copyMetadataFromFirst(boolean v) {
            this.copyMetadataFromFirst = v;
            return this;
        }

        public MergeOptions build() {
            if (inputs.size() < 2)
                throw new IllegalStateException(
                    "MergeOptions requires at least 2 input files, got: " + inputs.size());
            if (outputPath == null || outputPath.isBlank())
                throw new IllegalStateException("MergeOptions: output path must not be blank");
            return new MergeOptions(this);
        }
    }
}
