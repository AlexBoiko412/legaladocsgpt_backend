package com.legaldocsgpt.documentworker.service;

import com.legaldocsgpt.shared.entity.DocumentJob;
import com.legaldocsgpt.shared.entity.DocumentVersion;
import com.legaldocsgpt.shared.entity.JobStatus;
import com.legaldocsgpt.shared.exception.EntityNotFoundException;
import com.legaldocsgpt.shared.exception.GlobalErrorCode;
import com.legaldocsgpt.shared.repository.DocumentJobRepository;
import com.legaldocsgpt.shared.repository.DocumentVersionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DocumentJobInternalService {
    private final DocumentJobRepository repository;
    private final DocumentVersionRepository versionRepository;

    @Transactional
    public void completeJob(String jobId, String userId, String docxUrl,
                            String pdfUrl, String latestGeneratedContent,
                            String refinementPrompt, String snapshotKey) {
        repository.findByJobIdAndUserId(jobId, userId).ifPresentOrElse(job -> {

            if (latestGeneratedContent != null && snapshotKey != null) {
                boolean isInitial = job.getVersion() == 1;

                versionRepository.save(DocumentVersion.builder()
                        .jobId(jobId)
                        .userId(userId)
                        .version(job.getVersion())
                        .source(isInitial ? "INITIAL" : "REFINEMENT")
                        .content(isInitial ? latestGeneratedContent : job.getGeneratedContent())
                        .docxKey(snapshotKey)
                        .refinementPrompt(isInitial ? null : refinementPrompt)
                        .build());

                job.setGeneratedContent(latestGeneratedContent);
                job.setVersion(job.getVersion() + 1);
            }

            job.setDocxUrl(docxUrl);
            job.setPdfUrl(pdfUrl);
            job.setStatus(JobStatus.COMPLETED);
            job.setLastEditedAt(LocalDateTime.now());
            job.setCompletedAt(LocalDateTime.now());
            repository.save(job);

        }, () -> {
            throw new EntityNotFoundException(GlobalErrorCode.NOT_FOUND,
                    "Attempted to complete job " + jobId + " but it no longer exists");
        });
    }

    @Transactional
    public void completeJobRestore(String jobId, String userId,
                                   String docxUrl, String pdfUrl,
                                   String restoredContent, String snapshotKey) {
        repository.findByJobIdAndUserId(jobId, userId).ifPresentOrElse(job -> {
            if (job.getGeneratedContent() != null && snapshotKey != null) {
                versionRepository.save(DocumentVersion.builder()
                        .jobId(jobId)
                        .userId(userId)
                        .version(job.getVersion())
                        .source("REFINEMENT")
                        .content(job.getGeneratedContent())
                        .docxKey(snapshotKey)
                        .refinementPrompt("(before restore)")
                        .build());
            }

            job.setDocxUrl(docxUrl);
            job.setPdfUrl(pdfUrl);
            job.setStatus(JobStatus.COMPLETED);
            job.setGeneratedContent(restoredContent);
            job.setVersion(job.getVersion() + 1);
            job.setLastEditedAt(LocalDateTime.now());
            job.setCompletedAt(LocalDateTime.now());
            repository.save(job);

        }, () -> {
            throw new EntityNotFoundException(GlobalErrorCode.NOT_FOUND,
                    "Job " + jobId + " not found for user " + userId);
        });
    }

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