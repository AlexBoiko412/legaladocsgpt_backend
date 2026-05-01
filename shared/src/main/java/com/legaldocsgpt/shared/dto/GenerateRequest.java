package com.legaldocsgpt.shared.dto;


import lombok.Data;
import java.util.Map;

@Data
public class GenerateRequest {
    private String templateId;
    private Map<String, String> data;
}