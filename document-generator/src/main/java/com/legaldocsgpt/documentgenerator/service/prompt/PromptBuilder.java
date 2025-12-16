package com.legaldocsgpt.documentgenerator.service.prompt;

import com.legaldocsgpt.documentgenerator.dto.GenerateRequest;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {
    public String buildPrompt(GenerateRequest request) {
        return "Generate a legal document of type: ";
    }
}
