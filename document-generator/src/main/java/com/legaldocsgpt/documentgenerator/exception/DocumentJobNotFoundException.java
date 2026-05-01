package com.legaldocsgpt.documentgenerator.exception;

import com.legaldocsgpt.shared.exception.EntityNotFoundException;
import com.legaldocsgpt.shared.exception.GlobalErrorCode;

public class DocumentJobNotFoundException extends EntityNotFoundException {
    public DocumentJobNotFoundException(String jobId) {
        super(GlobalErrorCode.NOT_FOUND,
                String.format("Document job [%s] was not found or you do not have permission to view it.", jobId));
    }
}