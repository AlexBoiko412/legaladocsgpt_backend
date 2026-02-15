package com.legaldocsgpt.authservice.exception;

import com.legaldocsgpt.shared.exception.BaseBusinessException;
import com.legaldocsgpt.shared.exception.GlobalErrorCode;

public class UserAlreadyExistsException extends BaseBusinessException {
    public UserAlreadyExistsException(String message) {
        super(GlobalErrorCode.USER_ALREADY_EXISTS, message);
    }
}