package com.pdfcreator.server.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.io.InputStream;

public interface HtmlService {
    ResponseEntity<?> convertHtmlToPdfResponse(InputStream htmlStream, String pageSize) throws Exception;
}
