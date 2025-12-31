package com.legaldocsgpt.documentgenerator.service;

import com.legaldocsgpt.documentgenerator.config.RabbitMQConfig;
import com.legaldocsgpt.documentgenerator.dto.*;
import com.legaldocsgpt.shared.dto.*;
import com.legaldocsgpt.shared.entity.DocumentJob;
import com.legaldocsgpt.shared.entity.JobStatus;
import com.legaldocsgpt.shared.repository.DocumentJobRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentJobRepository documentJobRepository;
    private final RabbitTemplate rabbitTemplate;

    public GenerateResponse generateDocument(GenerateRequest request, String userId) {
        String jobId = UUID.randomUUID().toString();

        DocumentJob job = DocumentJob.builder()
                .jobId(jobId)
                .userId(userId)
                .title("New " + request.getTemplateId())
                .status(JobStatus.PENDING)
                .build();
        documentJobRepository.save(job);

        DocumentGenerationEvent event = new DocumentGenerationEvent(
                jobId,
                request.getTemplateId(),
                request.getFormat(),
                request.getData()
        );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                event
        );

        log.info("Job {} published to RabbitMQ exchange", jobId);

        return new GenerateResponse(jobId);
    }

    public void sendFinalizeEvent(String jobId, String editedContent) {
        DocumentJob job = documentJobRepository.findByJobId(jobId).orElseThrow();
        job.setGeneratedContent(editedContent);
        job.setLastEditedAt(LocalDateTime.now());
        job.setStatus(JobStatus.IN_PROGRESS);
        documentJobRepository.save(job);

        DocumentFinalizeEvent event = new DocumentFinalizeEvent(jobId, editedContent);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY, event);
    }

    public JobStatusResponse getJobStatus(String jobId) {
        return documentJobRepository.findByJobId(jobId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RuntimeException("Job not found"));
    }

    public List<JobStatusResponse> getAllJobs(String search) {
        List<DocumentJob> jobs = (search == null || search.isEmpty())
                ? documentJobRepository.findAllByOrderByLastEditedAtDesc()
                : documentJobRepository.findByTitleContainingIgnoreCaseOrJobIdContainingIgnoreCaseOrderByLastEditedAtDesc(search, search);

        return jobs.stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public void deleteDocument(String jobId) {
        DocumentJob job = documentJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new RuntimeException("Document not found with ID: " + jobId));

        documentJobRepository.deleteByJobId(jobId);
        log.info("Document job {} was deleted from the system", jobId);
    }

    private JobStatusResponse mapToResponse(DocumentJob job) {
        return JobStatusResponse.builder()
                .jobId(job.getJobId())
                .title(job.getTitle())
                .status(job.getStatus().name())
                .fileUrl(job.getFileUrl())
                .errorDetails(job.getErrorDetails())
                .generatedContent(job.getGeneratedContent())
                .createdAt(job.getCreatedAt())
                .lastEditedAt(job.getLastEditedAt())
                .build();
    }
}
