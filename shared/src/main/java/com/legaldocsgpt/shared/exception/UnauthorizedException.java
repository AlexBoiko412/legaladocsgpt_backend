package com.legaldocsgpt.shared.exception;

public class UnauthorizedException extends BaseBusinessException {
    public UnauthorizedException(String message) {
        super(GlobalErrorCode.UNAUTHORIZED, message);
    }

    public UnauthorizedException(GlobalErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}