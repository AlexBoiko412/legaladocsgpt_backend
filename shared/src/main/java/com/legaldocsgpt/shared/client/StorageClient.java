package com.legaldocsgpt.shared.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "storage-service", url = "http://storage-service:8084")
public interface StorageClient {

    @PostMapping(value = "/upload-raw", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    void uploadGeneric(
            @RequestParam("name") String fileName,
            @RequestParam("type") String contentType,
            @RequestBody byte[] content
    );

    @GetMapping(value = "/download-raw")
    byte[] downloadGeneric(@RequestParam("key") String key);

    @DeleteMapping("/delete-raw")
    void deleteGeneric(@RequestParam("key") String key);
}