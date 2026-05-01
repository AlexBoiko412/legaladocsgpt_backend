package com.legaldocsgpt.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateDefinition {
    private String id;
    private String name;
    private String description;
    private String systemPrompt;
    private String docxPath;
    private List<TemplateField> fields;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateField {
        private String key;
        private String label;
        private String type;
        private String placeholder;
        private boolean required;
    }
}