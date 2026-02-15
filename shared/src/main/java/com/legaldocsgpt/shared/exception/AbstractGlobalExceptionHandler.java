package com.legaldocsgpt.shared.exception;

import com.legaldocsgpt.shared.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
public abstract class AbstractGlobalExceptionHandler {

    @ExceptionHandler(BaseBusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BaseBusinessException ex, HttpServletRequest request) {

        GlobalErrorCode error = ex.getErrorCode();
        log.warn("Business Exception: [{}] {} at path {}", error.getCode(), ex.getMessage(), request.getRequestURI());

        ErrorResponse response = ErrorResponse.builder()
                .code(error.getCode())
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .traceId(getTraceId())
                .build();

        return new ResponseEntity<>(response, error.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> validationErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);
        });

        log.warn("Validation failed for {} errors at {}", validationErrors.size(), request.getRequestURI());

        ErrorResponse response = ErrorResponse.builder()
                .code(GlobalErrorCode.INVALID_INPUT.getCode())
                .message("Validation failed for one or more fields.")
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .validationErrors(validationErrors)
                .traceId(getTraceId())
                .build();

        return new ResponseEntity<>(response, GlobalErrorCode.INVALID_INPUT.getStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(
            Exception ex, HttpServletRequest request) {

        log.error("CRITICAL: Unhandled exception at path {}", request.getRequestURI(), ex);

        ErrorResponse response = ErrorResponse.builder()
                .code(GlobalErrorCode.INTERNAL_ERROR.getCode())
                .message("An internal server error occurred. Please contact support.")
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .traceId(getTraceId())
                .build();

        return new ResponseEntity<>(response, GlobalErrorCode.INTERNAL_ERROR.getStatus());
    }

    protected String getTraceId() {
        // Placeholder: Replace with actual tracing library call later
        return UUID.randomUUID().toString().substring(0, 8);
    }
}