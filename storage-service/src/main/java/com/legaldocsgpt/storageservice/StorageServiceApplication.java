package com.legaldocsgpt.storageservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@SpringBootApplication
public class StorageServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(StorageServiceApplication.class, args);
    }

    @Configuration
    static class WebConfig implements WebFluxConfigurer {
        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            // Map /api/storage/docs/** to the physical folder on the server
            registry.addResourceHandler("/docs/**")
                    .addResourceLocations("file:/app/storage/docs/");
        }
    }

    @RestController
    static class HealthController {
        @GetMapping("/health")
        public String health() {
            return "Storage Service is healthy!";
        }
    }
}