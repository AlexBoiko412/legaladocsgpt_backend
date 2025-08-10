package com.legaldocsgpt.apiGateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class ProxyController {

    private final WebClient.Builder webClientBuilder;

    public ProxyController(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Value("${services.auth:http://auth-service:8081}")
    private String authServiceUrl;

    @Value("${services.documents:http://document-generator:8082}")
    private String documentServiceUrl;

    @Value("${services.templates:http://template-service:8083}")
    private String templateServiceUrl;

    @Value("${services.storage:http://storage-service:8084}")
    private String storageServiceUrl;

    @GetMapping("/auth/{path}")
    public Mono<String> proxyAuth(@PathVariable String path) {
        return webClientBuilder.build()
                .get()
                .uri(authServiceUrl + "/" + path)
                .retrieve()
                .bodyToMono(String.class);
    }

    @GetMapping("/documents/{path}")
    public Mono<String> proxyDocuments(@PathVariable String path) {
        return webClientBuilder.build()
                .get()
                .uri(documentServiceUrl + "/" + path)
                .retrieve()
                .bodyToMono(String.class);
    }

    @GetMapping("/templates/{path}")
    public Mono<String> proxyTemplates(@PathVariable String path) {
        return webClientBuilder.build()
                .get()
                .uri(templateServiceUrl + "/" + path)
                .retrieve()
                .bodyToMono(String.class);
    }

    @GetMapping("/storage/{path}")
    public Mono<String> proxyStorage(@PathVariable String path) {
        return webClientBuilder.build()
                .get()
                .uri(storageServiceUrl + "/" + path)
                .retrieve()
                .bodyToMono(String.class);
    }
}
