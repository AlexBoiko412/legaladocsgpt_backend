package com.legaldocsgpt.authservice.exception;

import com.legaldocsgpt.shared.exception.BaseBusinessException;
import com.legaldocsgpt.shared.exception.GlobalErrorCode;

public class InvalidInputException extends BaseBusinessException {
    public InvalidInputException(GlobalErrorCode errorCode) {
        super(errorCode);
    }

    public InvalidInputException(GlobalErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}