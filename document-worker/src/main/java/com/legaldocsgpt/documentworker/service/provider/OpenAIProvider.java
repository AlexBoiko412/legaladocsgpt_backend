package com.legaldocsgpt.documentworker.service.provider;

import com.legaldocsgpt.documentworker.exception.AiProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnExpression("'${ai.provider}'.equals('openai') || '${ai.provider}'.equals('groq')")
public class OpenAIProvider implements AIProvider {
    private final WebClient client;
    private final String model;
    private final String providerName;
    private final java.time.Duration timeout;

    public OpenAIProvider(
            WebClient.Builder builder,
            @Value("${ai.provider}") String provider,
            @Value("${groq.api.key:}") String groqKey,
            @Value("${openai.api.key:}") String openaiKey,
            @Value("${ai.model:}") String modelOverride,
            @Value("${ai.timeout-seconds:120}") long timeoutSeconds) {

        String baseUrl;
        String apiKey;

        if ("groq".equals(provider)) {
            baseUrl = "https://api.groq.com/openai/v1";
            apiKey = groqKey;
            this.model = modelOverride.isBlank() ? "llama-3.3-70b-versatile" : modelOverride;
            this.providerName = "Groq";
            log.info("Using Groq Provider with model: {}", model);
        } else {
            baseUrl = "https://api.openai.com/v1";
            apiKey = openaiKey;
            this.model = modelOverride.isBlank() ? "gpt-4o-mini" : modelOverride;
            this.providerName = "OpenAI";
            log.info("Using OpenAI Provider with model: {}", model);
        }

        this.timeout = java.time.Duration.ofSeconds(timeoutSeconds);
        this.client = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    @Override
    public String generateText(String prompt) {
        String response = client.post()
                .uri("/chat/completions")
                .bodyValue(Map.of(
                        "model", this.model,
                        "messages", List.of(Map.of("role", "user", "content", prompt))
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .map(resp -> {
                    List choices = (List) resp.get("choices");
                    if (choices == null || choices.isEmpty()) {
                        throw new AiProviderException("No response received from AI service");
                    }
                    Map firstChoice = (Map) choices.get(0);
                    Map message = (Map) firstChoice.get("message");
                    return (String) message.get("content");
                })
                .timeout(timeout)
                .onErrorMap(org.springframework.web.reactive.function.client.WebClientResponseException.class,
                        e -> {
                            log.error("{} API error {}: {}", providerName, e.getStatusCode(), e.getResponseBodyAsString());
                            return e;
                        })
                .block();

        return response;
    }
}
