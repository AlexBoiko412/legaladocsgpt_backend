package com.legaldocsgpt.shared.exception;

public class EntityNotFoundException extends BaseBusinessException {
    public EntityNotFoundException(String message) {
        super(GlobalErrorCode.NOT_FOUND, message);
    }

    public EntityNotFoundException(GlobalErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}