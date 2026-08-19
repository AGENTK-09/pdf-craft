package com.pdfcreator.server.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import com.pdfcreator.server.service.*;
import java.io.InputStream;

@RestController
@RequestMapping("/api/v1/pdf")
public class PdfController {

    private final HtmlService htmlService;
    private final ExtractService extractService;
    private final ManipulatorService manipulatorService;
    private final SecurityService securityService;
    private final SignatureService signatureService;
    private final RasterizerService rasterizerService;
    private final BatchService batchService;
    private final FormService formService;
    private final PrinterService printerService;

    @Autowired
    public PdfController(HtmlService htmlService,
                         ExtractService extractService,
                         ManipulatorService manipulatorService,
                         SecurityService securityService,
                         SignatureService signatureService,
                         RasterizerService rasterizerService,
                         BatchService batchService,
                         FormService formService,
                         PrinterService printerService) {
        this.htmlService = htmlService;
        this.extractService = extractService;
        this.manipulatorService = manipulatorService;
        this.securityService = securityService;
        this.signatureService = signatureService;
        this.rasterizerService = rasterizerService;
        this.batchService = batchService;
        this.formService = formService;
        this.printerService = printerService;
    }

    // Example endpoint: HTML -> PDF
    @PostMapping(value = "/html-to-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "application/pdf")
    public ResponseEntity<?> htmlToPdf(@RequestPart(value = "htmlFile", required = false) MultipartFile htmlFile,
                                       @RequestParam(value = "pageSize", required = false, defaultValue = "A4") String pageSize) throws Exception {
        // Delegate to HtmlService which will stream PDF back
        InputStream in = htmlFile != null ? htmlFile.getInputStream() : null;
        return htmlService.convertHtmlToPdfResponse(in, pageSize);
    }

    // Example endpoint: Extract text
    @PostMapping(value = "/extract/text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> extractText(@RequestPart("file") MultipartFile file) throws Exception {
        InputStream in = file.getInputStream();
        return extractService.extractTextResponse(in);
    }

    // Other endpoints will be added similarly and delegate to service classes
}
