package com.legaldocsgpt.documentgenerator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class TemplateInfo {
    private String id;
    private String name;
    private List<String> fields;
}
