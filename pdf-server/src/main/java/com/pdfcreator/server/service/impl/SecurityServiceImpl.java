package com.pdfcreator.server.service.impl;

import com.pdfcreator.security.EncryptionOptions;
import com.pdfcreator.security.PdfSecurityManager;
import com.pdfcreator.security.SecurityResult;
import com.pdfcreator.server.service.SecurityService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

@Service
public class SecurityServiceImpl implements SecurityService {

    public ResponseEntity<?> encrypt(InputStream pdfStream, String ownerPassword, String userPassword, String preset) throws Exception {
        File in = Files.createTempFile("pdfcraft-sec-in-", ".pdf").toFile();
        in.deleteOnExit();
        Files.copy(pdfStream, in.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        File out = Files.createTempFile("pdfcraft-sec-out-", ".pdf").toFile();
        out.deleteOnExit();

        com.pdfcreator.security.PdfPermissions perms = com.pdfcreator.security.PdfPermissions.fromPreset(preset);

        EncryptionOptions opts = new EncryptionOptions.Builder(EncryptionOptions.Operation.ENCRYPT)
            .inputPath(in.getAbsolutePath())
            .outputPath(out.getAbsolutePath())
            .ownerPassword(ownerPassword)
            .userPassword(userPassword == null ? "" : userPassword)
            .permissions(perms)
            .build();

        PdfSecurityManager mgr = new PdfSecurityManager();
        SecurityResult result = mgr.execute(opts);

        org.springframework.core.io.InputStreamResource res = new org.springframework.core.io.InputStreamResource(new java.io.FileInputStream(out));
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=encrypted.pdf")
            .contentLength(out.length())
            .body(res);
    }

    public ResponseEntity<?> decrypt(InputStream pdfStream, String ownerPassword) throws Exception {
        File in = Files.createTempFile("pdfcraft-sec-in-", ".pdf").toFile();
        in.deleteOnExit();
        Files.copy(pdfStream, in.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        File out = Files.createTempFile("pdfcraft-sec-out-", ".pdf").toFile();
        out.deleteOnExit();

        EncryptionOptions opts = new EncryptionOptions.Builder(EncryptionOptions.Operation.DECRYPT)
            .inputPath(in.getAbsolutePath())
            .outputPath(out.getAbsolutePath())
            .ownerPassword(ownerPassword)
            .build();

        PdfSecurityManager mgr = new PdfSecurityManager();
        SecurityResult result = mgr.execute(opts);

        org.springframework.core.io.InputStreamResource res = new org.springframework.core.io.InputStreamResource(new java.io.FileInputStream(out));
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=decrypted.pdf")
            .contentLength(out.length())
            .body(res);
    }

    public ResponseEntity<?> inspect(InputStream pdfStream, String ownerPassword) throws Exception {
        File in = Files.createTempFile("pdfcraft-sec-in-", ".pdf").toFile();
        in.deleteOnExit();
        Files.copy(pdfStream, in.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        EncryptionOptions opts = new EncryptionOptions.Builder(EncryptionOptions.Operation.INSPECT)
            .inputPath(in.getAbsolutePath())
            .ownerPassword(ownerPassword)
            .build();

        PdfSecurityManager mgr = new PdfSecurityManager();
        SecurityResult result = mgr.execute(opts);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }
}
