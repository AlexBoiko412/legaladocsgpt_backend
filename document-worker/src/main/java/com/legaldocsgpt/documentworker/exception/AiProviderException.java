package com.legaldocsgpt.documentworker.exception;

import com.legaldocsgpt.shared.exception.BaseBusinessException;
import com.legaldocsgpt.shared.exception.GlobalErrorCode;

public class AiProviderException extends BaseBusinessException {

    public AiProviderException(String message) {
        super(GlobalErrorCode.AI_PROVIDER_UNAVAILABLE, message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(GlobalErrorCode.AI_PROVIDER_UNAVAILABLE, message);
        this.initCause(cause);
    }
}