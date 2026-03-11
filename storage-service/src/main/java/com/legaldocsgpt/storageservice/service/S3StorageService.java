package com.legaldocsgpt.storageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public void uploadFile(String fileName, String contentType, byte[] content) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileName)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content)
        );
    }

    public ResponseEntity<byte[]> downloadFile(String path) {
        try {
            byte[] bytes = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(path)
                            .build()
            ).asByteArray();

            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            if (path.endsWith(".pdf")) mediaType = MediaType.APPLICATION_PDF;
            else if (path.endsWith(".docx"))
                mediaType = MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path + "\"")
                    .body(bytes);
        } catch (Exception e) {
            log.error("Error downloading file {}: {}", path, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    public ResponseEntity<byte[]> downloadPdf(String jobId) {
        String key = jobId + ".pdf";
        try {
            byte[] bytes = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build()
            ).asByteArray();

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + key + "\"")
                    .body(bytes);
        } catch (Exception e) {
            log.error("Error downloading PDF {}: {}", key, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}