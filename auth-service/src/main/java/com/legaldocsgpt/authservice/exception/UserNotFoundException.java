package com.legaldocsgpt.authservice.exception;

import com.legaldocsgpt.shared.exception.EntityNotFoundException;
import com.legaldocsgpt.shared.exception.GlobalErrorCode;

public class UserNotFoundException extends EntityNotFoundException {
    public UserNotFoundException() {
        super(GlobalErrorCode.NOT_FOUND, "User with the provided credentials was not found.");
    }

    public UserNotFoundException(String message) {
        super(GlobalErrorCode.NOT_FOUND, message);
    }
}