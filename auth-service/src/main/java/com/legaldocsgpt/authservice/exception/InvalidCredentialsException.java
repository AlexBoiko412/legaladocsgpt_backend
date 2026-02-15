package com.legaldocsgpt.authservice.exception;

import com.legaldocsgpt.shared.exception.GlobalErrorCode;
import com.legaldocsgpt.shared.exception.UnauthorizedException;

public class InvalidCredentialsException extends UnauthorizedException {
    public InvalidCredentialsException() {
        super(GlobalErrorCode.UNAUTHORIZED, "Invalid username or password.");
    }
}