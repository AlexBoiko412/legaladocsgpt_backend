package com.legaldocsgpt.storageservice.controller;

import com.legaldocsgpt.shared.client.DocumentJobClient;
import com.legaldocsgpt.shared.dto.EditTokenClaims;
import com.legaldocsgpt.shared.exception.UnauthorizedException;
import com.legaldocsgpt.shared.services.EditTokenService;
import com.legaldocsgpt.storageservice.service.S3StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class StorageController {

    private final S3StorageService storageService;
    private final EditTokenService editTokenService;
    private final DocumentJobClient documentJobClient;

    @GetMapping("/{jobId}.pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String jobId) {
        return storageService.downloadPdf(jobId);
    }

    /**
     * Called by OnlyOffice Document Server to fetch the DOCX for editing.
     * Secured by the signed EditToken (same mechanism as /callback).
     * No user cookie required — this is server-to-server.
     */
    @GetMapping("/download-editing")
    public ResponseEntity<byte[]> downloadForEditing(@RequestParam("token") String token) {
        EditTokenClaims claims;
        try {
            claims = editTokenService.verify(token);
        } catch (UnauthorizedException e) {
            log.warn("Invalid edit token");
            return ResponseEntity.status(403).build();
        }

        String key = claims.jobId() + ".docx";
        log.info("OnlyOffice fetching DOCX for job: {}", claims.jobId());

        return storageService.downloadFile(key);
    }
    /**
     * Called by the browser/frontend to download a document.
     * X-User-Id is injected by the gateway after JWT validation.
     * Ownership is verified against the job record.
     */
    @GetMapping("/download-raw")
    public ResponseEntity<byte[]> downloadRaw(
            @RequestParam("key") String key,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        if (key.startsWith("templates/")) {
            return storageService.downloadFile(key);
        }

        if (userId == null) {
            log.warn("Missing X-User-Id for non-template download of key: {}", key);
            return ResponseEntity.status(403).build();
        }

        String jobId = key.contains(".") ? key.substring(0, key.lastIndexOf('.')) : key;
        boolean owned = documentJobClient.checkOwnership(jobId, userId);
        if (!owned) {
            log.warn("User {} attempted to download job {} they don't own", userId, jobId);
            return ResponseEntity.status(403).build();
        }

        return storageService.downloadFile(key);
    }
    @GetMapping("/download-internal")
    public ResponseEntity<byte[]> downloadInternal(@RequestParam("key") String key) {
        return storageService.downloadFile(key);
    }


    @PostMapping(value = "/upload-raw", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Void> uploadRaw(
            @RequestParam("name") String fileName,
            @RequestParam("type") String contentType,
            @RequestBody byte[] content) {
        storageService.uploadFile(fileName, contentType, content);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete-raw")
    public ResponseEntity<Void> deleteRaw(@RequestParam("key") String key) {
        storageService.deleteFile(key);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> onlyOfficeCallback(
            @RequestParam("token") String token,
            @RequestBody Map<String, Object> body) {

        EditTokenClaims claims;
        try {
            claims = editTokenService.verify(token);
        } catch (UnauthorizedException e) {
            log.warn("Invalid edit token on callback");
            return ResponseEntity.status(403).body(Map.of("error", 1));
        }

        Integer status = (Integer) body.get("status");
        String jobId = claims.jobId();

        log.info("Callback received - status: {}, jobId: {}", status, jobId);

        if (status == 2 || status == 6) {
            String downloadUrl = (String) body.get("url");
            if (downloadUrl != null) {
                downloadAndSaveEditedFile(jobId, downloadUrl);
                log.info("Saved OnlyOffice edits to MinIO for job {} (status {})", jobId, status);
            } else {
                log.warn("Callback status {} received but no URL provided for job {}", status, jobId);
            }
        }

        return ResponseEntity.ok(Map.of("error", 0));
    }

    private void downloadAndSaveEditedFile(String jobId, String downloadUrl) {
        try {
            String internalUrl = downloadUrl.replace("localhost:8089", "onlyoffice-ds:80")
                    .replace("localhost", "onlyoffice-ds");

            log.info("Downloading updated DOCX for job {} from internal URL: {}", jobId, internalUrl);

            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(internalUrl))
                    .GET()
                    .build();

            java.net.http.HttpResponse<byte[]> response = httpClient.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new RuntimeException("OnlyOffice download failed with status: " + response.statusCode());
            }

            byte[] content = response.body();
            storageService.uploadFile(jobId + ".docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    content);

            log.info("Successfully updated Minio with edited file for job {}", jobId);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to save OnlyOffice changes: {}", e.getMessage());
            throw new RuntimeException("Failed to save OnlyOffice callback file", e);
        }
    }
}