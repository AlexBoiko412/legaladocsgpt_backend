package com.legaldocsgpt.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.Map;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        String path,
        LocalDateTime timestamp,
        String traceId,
        Map<String, String> validationErrors
) {}