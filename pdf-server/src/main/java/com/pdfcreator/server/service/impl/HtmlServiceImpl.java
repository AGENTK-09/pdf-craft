package com.pdfcreator.server.service.impl;

import com.pdfcreator.server.service.HtmlService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import com.pdfcreator.htmlconverter.HtmlToPdfConverter;
import com.pdfcreator.htmlconverter.HtmlConversionOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;

@Service
public class HtmlServiceImpl implements HtmlService {

    @Override
    public ResponseEntity<?> convertHtmlToPdfResponse(InputStream htmlStream, String pageSize) throws Exception {
        if (htmlStream == null) {
            return ResponseEntity.badRequest().body("Missing htmlFile part");
        }

        File tempHtml = Files.createTempFile("pdfcraft-", ".html").toFile();
        File tempPdf  = Files.createTempFile("pdfcraft-", ".pdf").toFile();
        tempHtml.deleteOnExit();
        tempPdf.deleteOnExit();

        // Write uploaded HTML to temp file
        Files.copy(htmlStream, tempHtml.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        HtmlConversionOptions opts = new HtmlConversionOptions.Builder()
            .inputPath(tempHtml.getAbsolutePath())
            .outputPath(tempPdf.getAbsolutePath())
            .pageSize(pageSize != null ? pageSize : "A4")
            .build();

        HtmlToPdfConverter converter = new HtmlToPdfConverter();
        try {
            converter.convert(opts);
        } catch (IllegalStateException ise) {
            return ResponseEntity.status(500).body("wkhtmltopdf not found or not executable: " + ise.getMessage());
        }

        InputStreamResource resource = new InputStreamResource(new FileInputStream(tempPdf));
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=output.pdf");

        return ResponseEntity.ok()
            .headers(headers)
            .contentLength(tempPdf.length())
            .contentType(MediaType.APPLICATION_PDF)
            .body(resource);
    }
}
