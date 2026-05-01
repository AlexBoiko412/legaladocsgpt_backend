package com.legaldocsgpt.documentworker.service.prompt;

import com.legaldocsgpt.documentworker.client.TemplateClient;
import com.legaldocsgpt.shared.dto.DocumentGenerationEvent;
import com.legaldocsgpt.shared.dto.TemplateDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PromptBuilder {

    private final TemplateClient templateClient;

    public String buildFinalPrompt(DocumentGenerationEvent event) {
        TemplateDefinition template = templateClient.getTemplateById(event.getTemplateId());

        String userDataBlock = event.getData().entrySet().stream()
                .map(entry -> "- " + entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining("\n"));

        return String.format(
                "%s\n\n" +
                        "### USER PROVIDED DATA ###\n" +
                        "%s\n\n" +
                        "### STRICT INSTRUCTIONS ###\n" +
                        "- Use ONLY the values provided above. Never use placeholders like [INSERT NAME], [DATE], [jurisdiction] etc.\n" +
                        "- Do NOT include a document title at the top.\n" +
                        "- Do NOT include a signature block — the document template already contains one.\n" +
                        "- Do NOT add a 'Note:' or disclaimer at the end.\n" +
                        "- Do NOT repeat the parties section — the document template already lists the parties.\n" +
                        "- Generate ONLY the substantive legal body clauses.\n" +
                        "- Use markdown formatting: ## for article headings, **bold** for defined terms, * for bullet lists.\n" +
                        "- Start directly with the first article/clause (e.g. ## ARTICLE 1: DEFINITIONS).",
                template.getSystemPrompt(),
                userDataBlock
        );
    }
}