package com.legaldocsgpt.documentworker.service.prompt;

import com.legaldocsgpt.shared.dto.DocumentGenerationEvent;
import com.legaldocsgpt.shared.dto.TemplateDefinition;
import com.legaldocsgpt.shared.service.TemplateRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PromptBuilder {

    private final TemplateRegistry templateRegistry;

    public String buildFinalPrompt(DocumentGenerationEvent event) {
        TemplateDefinition template = templateRegistry.getTemplate(event.getTemplateId())
                .orElseThrow(() -> new RuntimeException("Template not found: " + event.getTemplateId()));

        String userDataBlock = event.getData().entrySet().stream()
                .map(entry -> "- " + entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining("\n"));

        return String.format(
                "%s\n\n" +
                        "### USER PROVIDED DATA ###\n" +
                        "%s\n\n" +
                        "Please generate the complete legal text now.",
                template.getSystemPrompt(),
                userDataBlock
        );
    }
}