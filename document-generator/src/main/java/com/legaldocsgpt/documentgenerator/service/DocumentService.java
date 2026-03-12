package com.legaldocsgpt.documentgenerator.service;

import com.legaldocsgpt.documentgenerator.client.TemplateClient;
import com.legaldocsgpt.documentgenerator.config.RabbitMQConfig;
import com.legaldocsgpt.documentgenerator.dto.*;
import com.legaldocsgpt.documentgenerator.exception.DocumentJobNotFoundException;
import com.legaldocsgpt.documentgenerator.exception.TemplateRequiredException;
import com.legaldocsgpt.documentgenerator.exception.ValidationException;
import com.legaldocsgpt.shared.context.UserContextHolder;
import com.legaldocsgpt.shared.dto.*;
import com.legaldocsgpt.shared.entity.DocumentJob;
import com.legaldocsgpt.shared.entity.JobStatus;
import com.legaldocsgpt.shared.exception.UnauthorizedException;
import com.legaldocsgpt.shared.repository.DocumentJobRepository;
import com.legaldocsgpt.shared.services.EditTokenService;
import com.legaldocsgpt.shared.services.OnlyOfficeJwtService;
import jakarta.transaction.Transactional;
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
    private final TemplateClient templateClient;
    private final EditTokenService editTokenService;
    private final OnlyOfficeJwtService onlyOfficeJwtService;

    public List<TemplateDefinition> getTemplates() {
        return templateClient.getAllTemplates();
    }

    public GenerateResponse generateDocument(GenerateRequest request) {
        if (request.getTemplateId() == null || request.getTemplateId().isBlank()) {
            throw new TemplateRequiredException();
        }

        log.info("Validating template {} via Template Service", request.getTemplateId());
        TemplateDefinition template = templateClient.getTemplateById(request.getTemplateId());

        String jobId = UUID.randomUUID().toString();


        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            log.error("Security Context Error: No User ID found in UserContextHolder for request!");
            throw new UnauthorizedException("User identity could not be verified. Please log in again.");
        }

        log.info("Generating document for user: {}", userId);

        DocumentJob job = DocumentJob.builder()
                .jobId(jobId)
                .userId(UserContextHolder.getUserId())
                .title("New " + template.getName())
                .status(JobStatus.PENDING)
                .build();

        documentJobRepository.save(job);

        DocumentGenerationEvent event = new DocumentGenerationEvent(
                jobId,
                userId,
                request.getTemplateId(),
                template.getDocxPath(),
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
        return documentJobRepository.findByJobIdAndUserId(jobId, UserContextHolder.getUserId())
                .map(this::mapToResponse)
                .orElseThrow(() -> new DocumentJobNotFoundException(jobId));
    }

    public List<JobStatusResponse> getAllJobs(String search) {
        List<DocumentJob> jobs = (search == null || search.isEmpty())
                ? documentJobRepository.findAllByUserIdOrderByLastEditedAtDesc(UserContextHolder.getUserId())
                : documentJobRepository.findByUserIdAndTitleContainingIgnoreCaseOrderByLastEditedAtDesc(UserContextHolder.getUserId(), search);

        return jobs.stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public void deleteDocument(String jobId) {
        DocumentJob job = documentJobRepository.findByJobIdAndUserId(jobId, UserContextHolder.getUserId())
                .orElseThrow(() -> new DocumentJobNotFoundException(jobId));

        documentJobRepository.deleteByJobIdAndUserId(jobId, UserContextHolder.getUserId());
        log.info("Document job {} was deleted from the system", jobId);
    }

    public EditorConfigResponse buildEditorConfig(String jobId, String userId) {
        DocumentJob job = documentJobRepository.findByJobIdAndUserId(jobId, userId)
                .orElseThrow(() -> new UnauthorizedException("Document not found"));

        if (job.getStatus() != JobStatus.COMPLETED) {
            throw new ValidationException("Document is not ready for editing");
        }

        String editToken = editTokenService.generate(jobId, userId);

        String docxUrl = "http://storage-service:8084/download-editing?token=" + editToken;
        String callbackUrl = "http://storage-service:8084/callback?token=" + editToken;

        EditorConfigResponse config = EditorConfigResponse.builder()
                .document(EditorConfigResponse.DocumentConfig.builder()
                        .key(jobId)
                        .title(job.getTitle() != null ? job.getTitle() : "Document_" + jobId)
                        .url(docxUrl)
                        .fileType("docx")
                        .permissions(EditorConfigResponse.Permissions.builder()
                                .edit(true)
                                .download(true)
                                .print(false)
                                .build())
                        .build())
                .editorConfig(EditorConfigResponse.EditorConfig.builder()
                        .callbackUrl(callbackUrl)
                        .lang("en-US")
                        .mode("edit")
                        .user(EditorConfigResponse.User.builder()
                                .id(userId)
                                .name("Legal Editor")
                                .build())
                        .build())
                .build();

        config.setToken(onlyOfficeJwtService.buildToken(config));
        return config;
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
