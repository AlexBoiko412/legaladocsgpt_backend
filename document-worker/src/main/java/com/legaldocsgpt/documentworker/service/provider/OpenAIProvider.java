package com.legaldocsgpt.documentworker.service.provider;

import com.legaldocsgpt.documentworker.exception.AiProviderException;
import com.legaldocsgpt.shared.exception.ThirdPartyApiException;
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

    public OpenAIProvider(
            WebClient.Builder builder,
            @Value("${ai.provider}") String provider,
            @Value("${groq.api.key:}") String groqKey,
            @Value("${openai.api.key:}") String openaiKey) {

        String baseUrl;
        String apiKey;

        if ("groq".equals(provider)) {
            baseUrl = "https://api.groq.com/openai/v1";
            apiKey = groqKey;
            this.model = "llama-3.3-70b-versatile";
            this.providerName = "Groq";
            log.info("Using Groq Provider with model: {}", model);
        } else {
            baseUrl = "https://api.openai.com/v1";
            apiKey = openaiKey;
            this.model = "gpt-4o-mini";
            this.providerName = "OpenAI";
            log.info("Using OpenAI Provider with model: {}", model);
        }

        this.client = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    @Override
    public String generateText(String prompt) {
        return client.post()
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
                .block();
    }

    public String handleAiFallback(String prompt, Throwable t) {
        log.error("Circuit Breaker OPEN or AI Provider failed. Reason: {}", t.getMessage());

        if (t instanceof AiProviderException) {
            throw (AiProviderException) t;
        }

        throw new ThirdPartyApiException(providerName,
                "The AI service is currently overloaded or unresponsive. Please try again in a few minutes.");
    }
}
