package com.legaldocsgpt.shared.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode {
    // General Errors (001-099)
    INTERNAL_ERROR("GEN_001", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_INPUT("GEN_002", "Invalid request parameters", HttpStatus.BAD_REQUEST),
    NOT_FOUND("GEN_003", "Resource not found", HttpStatus.NOT_FOUND),

    // Auth Service (100-199)
    UNAUTHORIZED("AUTH_101", "Authentication required", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN("AUTH_102", "Invalid or expired token", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("AUTH_103", "Insufficient permissions", HttpStatus.FORBIDDEN),
    USER_ALREADY_EXISTS("AUTH_104", "User already exists", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS("AUTH_105", "Invalid username or password", HttpStatus.UNAUTHORIZED),

    // Template Service (200-299)
    TEMPLATE_NOT_FOUND("TMP_201", "The requested template does not exist", HttpStatus.NOT_FOUND),
    INVALID_TEMPLATE_SCHEMA("TMP_202", "Template structure is invalid", HttpStatus.BAD_REQUEST),

    // Document & AI Service (300-399)
    DOC_GENERATION_FAILED("DOC_301", "Failed to generate document", HttpStatus.INTERNAL_SERVER_ERROR),
    AI_PROVIDER_UNAVAILABLE("DOC_302", "AI provider (OpenAI/Groq) is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE),

    // Storage Service (400-499)
    STORAGE_UPLOAD_ERROR("STR_401", "Failed to upload file to storage", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_NOT_FOUND("STR_402", "Requested file not found in storage", HttpStatus.NOT_FOUND);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus status;
}