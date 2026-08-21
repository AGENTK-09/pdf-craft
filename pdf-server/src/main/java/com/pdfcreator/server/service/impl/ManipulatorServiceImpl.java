package com.pdfcreator.server.service.impl;

import com.pdfcreator.manipulator.MergeOptions;
import com.pdfcreator.manipulator.SplitOptions;
import com.pdfcreator.manipulator.SplitResult;
import com.pdfcreator.manipulator.PdfManipulator;
import com.pdfcreator.server.service.ManipulatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ManipulatorServiceImpl implements ManipulatorService {

    @Override
    public ResponseEntity<?> mergeFiles(java.util.List<InputStream> pdfStreams, java.util.List<String> passwords) throws Exception {
        // Write inputs to temp files
        java.util.List<String> paths = new java.util.ArrayList<>();
        for (int i = 0; i < pdfStreams.size(); i++) {
            File tmp = Files.createTempFile("pdfcraft-merge-", ".pdf").toFile();
            tmp.deleteOnExit();
            Files.copy(pdfStreams.get(i), tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            paths.add(tmp.getAbsolutePath());
        }

        MergeOptions.Builder b = new MergeOptions.Builder();
        for (int i = 0; i < paths.size(); i++) {
            String pwd = (passwords != null && i < passwords.size()) ? passwords.get(i) : null;
            b.addInput(paths.get(i), pwd);
        }
        File out = Files.createTempFile("pdfcraft-merge-out-", ".pdf").toFile();
        out.deleteOnExit();
        b.output(out.getAbsolutePath());

        new PdfManipulator().merge(b.build());

        org.springframework.core.io.InputStreamResource res = new org.springframework.core.io.InputStreamResource(new java.io.FileInputStream(out));
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=merged.pdf")
            .contentLength(out.length())
            .body(res);
    }

    @Override
    public ResponseEntity<?> splitFile(InputStream pdfStream, String strategy, int everyN, int intoParts, String rangesCsv) throws Exception {
        File tmp = Files.createTempFile("pdfcraft-split-", ".pdf").toFile();
        tmp.deleteOnExit();
        Files.copy(pdfStream, tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        SplitOptions.Builder sb = new SplitOptions.Builder(tmp.getAbsolutePath(), Files.createTempDirectory("pdfcraft-split-out").toAbsolutePath().toString());
        if ("every-n".equals(strategy)) sb.everyNPages(everyN);
        else if ("into-parts".equals(strategy)) sb.intoParts(intoParts);
        else if ("ranges".equals(strategy)) {
            // parse rangesCsv like 1-3,4-6
            String[] parts = rangesCsv.split(",");
            int[][] ranges = new int[parts.length][2];
            for (int i = 0; i < parts.length; i++) {
                String[] bounds = parts[i].trim().split("-");
                ranges[i][0] = Integer.parseInt(bounds[0]);
                ranges[i][1] = Integer.parseInt(bounds[1]);
            }
            sb.byPageRanges(ranges);
        } else {
            throw new IllegalArgumentException("Unknown split strategy: " + strategy);
        }

        SplitResult res = new com.pdfcreator.manipulator.PdfManipulator().split(sb.build());

        // Collect output files into a zip and return
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (String path : res.getOutputPaths()) {
                ZipEntry ze = new ZipEntry(new File(path).getName());
                zos.putNextEntry(ze);
                Files.copy(new File(path).toPath(), zos);
                zos.closeEntry();
            }
        }

        byte[] zipBytes = baos.toByteArray();
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=split-parts.zip")
            .contentLength(zipBytes.length)
            .body(zipBytes);
    }
}
