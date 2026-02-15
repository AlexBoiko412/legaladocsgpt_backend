package com.legaldocsgpt.storageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService {
    private final S3AsyncClient s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public Mono<Void> uploadFile(String jobId, byte[] content) {
        return Mono.fromFuture(s3Client.putObject(
                r -> r.bucket(bucketName).key(jobId + ".pdf").contentType("application/pdf"),
                AsyncRequestBody.fromBytes(content)
        )).then();
    }

    public Mono<ResponseEntity<byte[]>> downloadPdf(String jobId) {
        String key = jobId + ".pdf";

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        return Mono.fromFuture(s3Client.getObject(request, AsyncResponseTransformer.toBytes()))
                .map(response -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + key + "\"")
                        .body(response.asByteArray()))
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
    }
}