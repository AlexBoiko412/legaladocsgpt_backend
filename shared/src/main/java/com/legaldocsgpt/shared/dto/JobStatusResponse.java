package com.legaldocsgpt.shared.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JobStatusResponse {
    private String jobId;
    private String status;
    private String fileUrl;
    private String errorDetails;
    private String generatedContent;
}