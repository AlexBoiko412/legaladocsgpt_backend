package com.legaldocsgpt.documentworker.exception;

import com.legaldocsgpt.shared.exception.BaseBusinessException;
import com.legaldocsgpt.shared.exception.GlobalErrorCode;

public class PdfGenerationException extends BaseBusinessException {

    public PdfGenerationException(String message) {
        super(GlobalErrorCode.DOC_GENERATION_FAILED, message);
    }

    public PdfGenerationException(String message, Throwable cause) {
        super(GlobalErrorCode.DOC_GENERATION_FAILED, message);
        this.initCause(cause);
    }
}