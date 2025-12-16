package com.legaldocsgpt.documentgenerator.service.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAIProvider implements AIProvider {
    private final WebClient client;

    public OpenAIProvider(WebClient.Builder builder, @Value("${openai.api.key}") String apiKey) {
        this.client = builder
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    @Override
    public String generateText(String prompt) {
        return client.post()
                .uri("/chat/completions")
                .bodyValue(Map.of(
                        "model", "gpt-4.1-mini",
                        "messages", List.of(Map.of("role", "user", "content", prompt))
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .map(resp -> ((Map)((List) resp.get("choices")).get(0)).get("message"))
                .map(msg -> (String) ((Map)msg).get("content"))
                .block();
    }
}
