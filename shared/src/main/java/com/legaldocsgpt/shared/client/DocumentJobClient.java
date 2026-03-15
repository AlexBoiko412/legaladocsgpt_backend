package com.legaldocsgpt.shared.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "document-generator", url = "http://document-generator:8082")
public interface DocumentJobClient {

    @GetMapping("/ownership")
    boolean checkOwnership(@RequestParam("jobId") String jobId,
                           @RequestParam("userId") String userId);
}