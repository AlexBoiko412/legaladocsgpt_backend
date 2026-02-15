package com.legaldocsgpt.storageservice.controller;

import com.legaldocsgpt.storageservice.service.S3StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;


@RestController
@RequestMapping("/docs")
@RequiredArgsConstructor
public class StorageController {

    private final S3StorageService storageService;

    @GetMapping("/{jobId}.pdf")
    public Mono<ResponseEntity<byte[]>> downloadPdf(@PathVariable String jobId) {
        return storageService.downloadPdf(jobId);
    }

    @PostMapping(value = "/upload/{jobId}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<Void>> uploadPdf(@PathVariable String jobId, @RequestBody byte[] content) {
        return storageService.uploadFile(jobId, content)
                .thenReturn(ResponseEntity.ok().build());
    }
}