package com.pdfcreator.server.service;

import org.springframework.http.ResponseEntity;
import java.io.InputStream;

public interface ExtractService {
    ResponseEntity<?> extractTextResponse(InputStream pdfStream) throws Exception;
}
