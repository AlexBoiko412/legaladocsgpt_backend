package com.legaldocsgpt.documentgenerator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GenerateResponse {
    private String fileUrl;
}