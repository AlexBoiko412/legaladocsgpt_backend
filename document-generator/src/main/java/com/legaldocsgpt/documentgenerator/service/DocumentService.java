package com.legaldocsgpt.documentgenerator.service;

import com.legaldocsgpt.documentgenerator.config.RabbitMQConfig;
import com.legaldocsgpt.documentgenerator.dto.*;
import com.legaldocsgpt.shared.dto.*;
import com.legaldocsgpt.shared.entity.DocumentJob;
import com.legaldocsgpt.shared.entity.JobStatus;
import com.legaldocsgpt.shared.repository.DocumentJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

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

    public JobStatusResponse getJobStatus(String jobId) {
        DocumentJob job = documentJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found with ID: " + jobId)); // Later, a proper exception

        return JobStatusResponse.builder()
                .jobId(job.getJobId())
                .status(job.getStatus().name())
                .fileUrl(job.getFileUrl())
                .errorDetails(job.getErrorDetails())
                .build();
    }

    public List<TemplateInfo> getAvailableTemplates() {
        // This could be fetched from a database or another service in the future
        return List.of(
                new TemplateInfo("contract_sale_v1", "Contract of Sale",
                        List.of("sellerName", "buyerName", "price", "date")),
                new TemplateInfo("nda_v1", "Non-Disclosure Agreement",
                        List.of("partyA", "partyB", "effectiveDate"))
        );
    }

    public void sendFinalizeEvent(String jobId, String editedContent) {
        // 1. Update DB with the edited text so it's not lost
        DocumentJob job = documentJobRepository.findByJobId(jobId).orElseThrow();
        job.setGeneratedContent(editedContent);
        job.setStatus(JobStatus.IN_PROGRESS); // Mark as processing again
        documentJobRepository.save(job);

        // 2. Send to RabbitMQ
        DocumentFinalizeEvent event = new DocumentFinalizeEvent(jobId, editedContent);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY, event);
    }
}
