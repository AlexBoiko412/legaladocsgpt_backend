package com.legaldocsgpt.storageservice.controller;

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

    @GetMapping("/{jobId}.pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String jobId) {
        return storageService.downloadPdf(jobId);
    }

    @GetMapping("/download-raw")
    public ResponseEntity<byte[]> downloadRaw(@RequestParam("key") String key) {
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

    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> onlyOfficeCallback(
            @RequestParam("token") String token,
            @RequestBody Map<String, Object> body) {

        Integer status = (Integer) body.get("status");
        String jobId = (String) body.get("key");

        log.info("Received OnlyOffice callback for job {} with status {}", jobId, status);

        if (status == 2 || status == 6) {
            String downloadUrl = (String) body.get("url");
            downloadAndSaveEditedFile(jobId, downloadUrl);
        }

        return ResponseEntity.ok(Map.of("error", 0));
    }

    private void downloadAndSaveEditedFile(String jobId, String downloadUrl) {
        try {
            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .GET()
                    .build();

            byte[] content = httpClient.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofByteArray()).body();

            String fileName = jobId + ".docx";
            String contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            storageService.uploadFile(fileName, contentType, content);

        } catch (Exception e) {
            log.error("Failed to download and save edited file for job {}: {}", jobId, e.getMessage());
            throw new RuntimeException("Failed to save OnlyOffice callback file", e);
        }
    }
}