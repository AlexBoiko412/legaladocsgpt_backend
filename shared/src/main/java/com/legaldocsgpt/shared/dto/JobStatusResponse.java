package com.legaldocsgpt.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class JobStatusResponse {
    private String jobId;
    private String status;
    private String title;
    private String docxUrl;
    private String pdfUrl;
    private String errorDetails;
    private String generatedContent;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastEditedAt;
}