package com.legaldocsgpt.templateservice.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class TemplateRequest {
    private String name;
    private String description;
    private String systemPrompt;
    private String fields;
    private MultipartFile file;
}