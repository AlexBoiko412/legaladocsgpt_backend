package com.legaldocsgpt.documentgenerator.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class DocumentVersionResponse {
    private Long id;
    private int version;
    private String source;
    private String content;
    private String docxKey;
    private LocalDateTime createdAt;
    private String refinementPrompt;
}