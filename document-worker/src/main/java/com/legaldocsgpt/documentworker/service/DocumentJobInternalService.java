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
    public void saveGeneratedContent(String jobId, String userId, String content) {
        repository.findByJobIdAndUserId(jobId, userId).ifPresent(job -> {
            job.setGeneratedContent(content);
            job.setLastEditedAt(LocalDateTime.now());
            repository.save(job);
        });
    }
    @Transactional
    public void completeJob(String jobId, String userId, String fileUrl) {
        repository.findByJobIdAndUserId(jobId, userId).ifPresentOrElse(job -> {
            job.setFileUrl(fileUrl);
            job.setStatus(JobStatus.COMPLETED);
            job.setLastEditedAt(LocalDateTime.now());
            repository.save(job);
        }, () -> {
            throw new EntityNotFoundException(
                    GlobalErrorCode.NOT_FOUND,
                    "Attempted to complete job " + jobId +
                            " but it no longer exists for user " + userId
            );
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
}