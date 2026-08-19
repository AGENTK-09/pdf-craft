package com.pdfcreator.server.service.impl;

import com.pdfcreator.server.service.ExtractService;
import com.pdfcreator.extractor.ExtractionOptions;
import com.pdfcreator.extractor.ExtractionResult;
import com.pdfcreator.extractor.PdfTextExtractor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

@Service
public class ExtractServiceImpl implements ExtractService {

    @Override
    public ResponseEntity<?> extractTextResponse(InputStream pdfStream) throws Exception {
        if (pdfStream == null) {
            return ResponseEntity.badRequest().body("Missing file part");
        }

        File tempPdf = Files.createTempFile("pdfcraft-upload-", ".pdf").toFile();
        tempPdf.deleteOnExit();
        Files.copy(pdfStream, tempPdf.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        ExtractionOptions opts = new ExtractionOptions.Builder().build();
        PdfTextExtractor extractor = new PdfTextExtractor();
        ExtractionResult result = extractor.extract(tempPdf.getAbsolutePath(), opts);

        String text = result.getText();
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(text);
    }
}
