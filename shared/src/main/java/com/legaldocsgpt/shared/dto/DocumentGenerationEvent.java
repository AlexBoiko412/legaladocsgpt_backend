package com.legaldocsgpt.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentGenerationEvent {
    private String jobId;
    private String userId;
    private String templateId;
    private Map<String, String> data;
}