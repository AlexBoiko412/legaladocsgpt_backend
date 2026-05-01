package com.legaldocsgpt.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentConvertEvent {
    private String jobId;
    private String userId;
}