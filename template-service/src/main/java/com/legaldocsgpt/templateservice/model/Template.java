package com.legaldocsgpt.templateservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Document(collection = "templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Template {
    @Id
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