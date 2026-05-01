package com.legaldocsgpt.shared.exception;

import lombok.Getter;

@Getter
public abstract class BaseBusinessException extends RuntimeException {
    private final GlobalErrorCode errorCode;

    protected BaseBusinessException(GlobalErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    protected BaseBusinessException(GlobalErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}