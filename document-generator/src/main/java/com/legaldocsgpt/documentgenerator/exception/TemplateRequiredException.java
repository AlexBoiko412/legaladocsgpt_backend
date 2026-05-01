package com.legaldocsgpt.documentgenerator.exception;

import com.legaldocsgpt.shared.exception.BaseBusinessException;
import com.legaldocsgpt.shared.exception.GlobalErrorCode;

public class TemplateRequiredException extends BaseBusinessException {
    public TemplateRequiredException() {
        super(GlobalErrorCode.INVALID_INPUT, "A valid Template ID is required to initiate document generation.");
    }
}