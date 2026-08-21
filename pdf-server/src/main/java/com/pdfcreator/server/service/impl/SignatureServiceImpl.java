package com.pdfcreator.server.service.impl;

import com.pdfcreator.server.service.SignatureService;
import com.pdfcreator.signature.PdfSigner;
import com.pdfcreator.signature.SigningOptions;
import com.pdfcreator.signature.SignatureResult;
import com.pdfcreator.signature.PdfSignatureVerifier;
import com.pdfcreator.signature.VerificationResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class SignatureServiceImpl implements SignatureService {

    public ResponseEntity<?> sign(InputStream pdfStream, MultipartFile keystoreFile, String keystorePassword,
                                  String keyAlias, String reason, String location, String contactInfo,
                                  boolean visible, int signaturePage, int rectX, int rectY, int rectW, int rectH,
                                  String tsaUrl) throws Exception {
        File in = Files.createTempFile("pdfcraft-sign-in-", ".pdf").toFile();
        in.deleteOnExit();
        Files.copy(pdfStream, in.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        File out = Files.createTempFile("pdfcraft-sign-out-", ".pdf").toFile();
        out.deleteOnExit();

        File ks = null;
        if (keystoreFile != null) {
            ks = Files.createTempFile("pdfcraft-ks-", ".p12").toFile();
            ks.deleteOnExit();
            Files.copy(keystoreFile.getInputStream(), ks.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        SigningOptions.Builder sb = new SigningOptions.Builder()
            .inputPath(in.getAbsolutePath())
            .outputPath(out.getAbsolutePath())
            .keystorePath(ks != null ? ks.getAbsolutePath() : null)
            .keystorePassword(keystorePassword)
            .keyAlias(keyAlias)
            .reason(reason)
            .location(location)
            .contactInfo(contactInfo)
            .visible(visible)
            .signaturePage(signaturePage)
            .signatureRect(rectX, rectY, rectW, rectH)
            .tsaUrl(tsaUrl);

        PdfSigner signer = new PdfSigner();
        SignatureResult result = signer.sign(sb.build());

        org.springframework.core.io.InputStreamResource res = new org.springframework.core.io.InputStreamResource(new java.io.FileInputStream(out));
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=signed.pdf")
            .contentLength(out.length())
            .body(res);
    }

    public ResponseEntity<?> verify(InputStream pdfStream, String truststorePath, String truststorePassword, String truststoreType) throws Exception {
        File in = Files.createTempFile("pdfcraft-verify-in-", ".pdf").toFile();
        in.deleteOnExit();
        Files.copy(pdfStream, in.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        PdfSignatureVerifier verifier = new PdfSignatureVerifier();
        List<com.pdfcreator.signature.VerificationResult> results = verifier.verify(in.getAbsolutePath(), truststorePath, truststorePassword, truststoreType);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(results);
    }

    public ResponseEntity<?> exportCert(MultipartFile keystoreFile, String keystorePassword, String alias) throws Exception {
        File ks = null;
        if (keystoreFile != null) {
            ks = Files.createTempFile("pdfcraft-ks-", ".p12").toFile();
            ks.deleteOnExit();
            Files.copy(keystoreFile.getInputStream(), ks.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } else {
            throw new IllegalArgumentException("keystore file is required");
        }

        // Use PdfSigner helper to export cert
        byte[] certBytes = com.pdfcreator.signature.PdfSigner.exportCert(ks.getAbsolutePath(), keystorePassword, alias);

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=cert.cer")
            .contentLength(certBytes.length)
            .body(certBytes);
    }
}
