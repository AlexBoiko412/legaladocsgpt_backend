package com.legaldocsgpt.shared.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class TemplateDefinition {
    private String id;
    private String name;
    private String description;
    private String systemPrompt;
    private List<TemplateField> fields;

    @Data
    @Builder
    public static class TemplateField {
        private String key;
        private String label;
        private String type;
        private String placeholder;
        private boolean required;
    }
}