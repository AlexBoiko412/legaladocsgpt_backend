package com.legaldocsgpt.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentFinalizeEvent {
    private String jobId;
    private String userId;
    private String refinementPrompt;

}