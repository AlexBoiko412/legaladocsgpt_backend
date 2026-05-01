package com.legaldocsgpt.storageservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@SpringBootApplication
@EnableFeignClients(basePackages = {
        "com.legaldocsgpt.shared.client",
        "com.legaldocsgpt.storageservice.client"
})
@ComponentScan(basePackages = "com.legaldocsgpt")
public class StorageServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(StorageServiceApplication.class, args);
    }

    @RestController
    static class HealthController {
        @GetMapping("/health")
        public String health() {
            return "Storage Service is healthy!";
        }
    }
}