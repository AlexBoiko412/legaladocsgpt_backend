package com.legaldocsgpt.documentworker.service.prompt;

import com.legaldocsgpt.shared.dto.DocumentGenerationEvent;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;

@Component
public class PromptBuilder {

    public String buildPrompt(DocumentGenerationEvent event) {
        // Map the user data into a readable string for the AI
        String userData = event.getData().entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining(", "));

        return String.format(
                "Generate a professional legal document for the template '%s'. " +
                        "The document should be in %s format. " +
                        "Use the following provided information: %s. " +
                        "Ensure the tone is formal and legally binding.",
                event.getTemplateId(),
                event.getFormat(),
                userData
        );
    }

    public String buildSystemPrompt() {
        return "You are a professional legal document generator. " +
                "Your output must be ONLY the final legal text. " +
                "Do not include greetings, introductions, or disclaimers about your capabilities. " +
                "Format the document with clear headings (e.g., 1. DEFINITIONS).";
    }

    public String buildUserPrompt(DocumentGenerationEvent event) {
        return String.format(
                "Generate a %s agreement for %s regarding the amount of %s. " +
                        "Include these specific details: %s.",
                event.getTemplateId(),
                event.getData().get("clientName"),
                event.getData().get("amount"),
                event.getData().toString()
        );
    }
}