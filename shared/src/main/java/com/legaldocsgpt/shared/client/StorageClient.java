package com.legaldocsgpt.shared.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "storage-service", url = "http://storage-service:8084")
public interface StorageClient {

    @PostMapping(value = "/docs/upload/{jobId}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    void uploadDocument(@PathVariable("jobId") String jobId, @RequestBody byte[] content);
}