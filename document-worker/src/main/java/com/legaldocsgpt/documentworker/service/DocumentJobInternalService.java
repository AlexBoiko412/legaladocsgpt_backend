package com.legaldocsgpt.documentworker.service;

import com.legaldocsgpt.shared.entity.DocumentJob;
import com.legaldocsgpt.shared.entity.JobStatus;
import com.legaldocsgpt.shared.exception.EntityNotFoundException;
import com.legaldocsgpt.shared.exception.GlobalErrorCode;
import com.legaldocsgpt.shared.repository.DocumentJobRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DocumentJobInternalService {
    private final DocumentJobRepository repository;

    @Transactional
    public void updateStatus(String jobId, String userId, JobStatus status) {
        DocumentJob job = repository.findByJobIdAndUserId(jobId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        GlobalErrorCode.NOT_FOUND,
                        "Job not found or access denied for ID: " + jobId
                ));
        job.setStatus(status);
        repository.save(job);
    }

    @Transactional
    public void completeJob(String jobId, String userId, String docxUrl, String pdfUrl, String latestGeneratedContent) {
        repository.findByJobIdAndUserId(jobId, userId).ifPresentOrElse(job -> {
            job.setDocxUrl(docxUrl);
            job.setPdfUrl(pdfUrl);
            job.setStatus(JobStatus.COMPLETED);
            if (latestGeneratedContent != null) {
                job.setGeneratedContent(latestGeneratedContent);
                job.setVersion(job.getVersion() + 1);
            }
            job.setLastEditedAt(LocalDateTime.now());
            job.setCompletedAt(LocalDateTime.now());
            repository.save(job);
        }, () -> {
            throw new EntityNotFoundException(GlobalErrorCode.NOT_FOUND,
                    "Attempted to complete job " + jobId + " but it no longer exists for user " + userId);
        });
    }

    @Transactional
    public void failJob(String jobId, String userId, String error) {
        repository.findByJobIdAndUserId(jobId, userId).ifPresent(job -> {
            job.setStatus(JobStatus.FAILED);
            String safeError = (error != null && error.length() > 250)
                    ? error.substring(0, 247) + "..."
                    : error;
            job.setErrorDetails(safeError);
            repository.save(job);
        });
    }

    public DocumentJob getJob(String jobId, String userId) {
        return repository.findByJobIdAndUserId(jobId, userId)
                .orElseThrow(() -> new EntityNotFoundException(GlobalErrorCode.NOT_FOUND,
                        "Job " + jobId + " not found"));
    }
}