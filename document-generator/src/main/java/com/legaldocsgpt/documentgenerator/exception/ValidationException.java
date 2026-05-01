package com.legaldocsgpt.documentgenerator.exception;

import com.legaldocsgpt.shared.exception.BaseBusinessException;
import com.legaldocsgpt.shared.exception.GlobalErrorCode;

public class ValidationException extends BaseBusinessException {
    public ValidationException(String message) {
        super(GlobalErrorCode.INVALID_INPUT, message);
    }
}