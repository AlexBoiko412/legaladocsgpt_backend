package com.legaldocsgpt.documentgenerator.service;

import com.legaldocsgpt.documentgenerator.client.TemplateClient;
import com.legaldocsgpt.shared.client.StorageClient;
import com.legaldocsgpt.shared.config.SharedRabbitConfig;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class DocumentService {

    private final DocumentJobRepository documentJobRepository;
    private final RabbitTemplate rabbitTemplate;
    private final TemplateClient templateClient;
    private final EditTokenService editTokenService;
    private final OnlyOfficeJwtService onlyOfficeJwtService;
    private final StorageClient storageClient;
    private final WebClient webClient;

    @Value("${onlyoffice.internal-url}")
    private String onlyOfficeInternalUrl;

    @Value("${storage.service.url:http://storage-service:8084}")
    private String storageServiceUrl;

    public DocumentService(DocumentJobRepository documentJobRepository,
                           RabbitTemplate rabbitTemplate,
                           TemplateClient templateClient,
                           EditTokenService editTokenService,
                           OnlyOfficeJwtService onlyOfficeJwtService,
                           StorageClient storageClient,
                           WebClient.Builder webClientBuilder) {
        this.documentJobRepository = documentJobRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.templateClient = templateClient;
        this.editTokenService = editTokenService;
        this.onlyOfficeJwtService = onlyOfficeJwtService;
        this.storageClient = storageClient;
        this.webClient = webClientBuilder.build();
    }

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
                .userId(userId)
                .title("New " + template.getName())
                .status(JobStatus.PENDING)
                .templatePath(template.getDocxPath())
                .documentData(request.getData())
                .build();
        documentJobRepository.save(job);

        DocumentGenerationEvent event = new DocumentGenerationEvent(
                jobId,
                userId,
                request.getTemplateId(),
                request.getData()
        );

        rabbitTemplate.convertAndSend(
                SharedRabbitConfig.EXCHANGE_NAME,
                SharedRabbitConfig.ROUTING_KEY,
                event
        );

        log.info("Job {} published to RabbitMQ exchange", jobId);
        return new GenerateResponse(jobId);
    }

    public void sendFinalizeEvent(String jobId, String refinementPrompt) {
        String userId = UserContextHolder.getUserId();
        DocumentJob job = documentJobRepository.findByJobIdAndUserId(jobId, userId)
                .orElseThrow(() -> new DocumentJobNotFoundException(jobId));
        job.setStatus(JobStatus.IN_PROGRESS);
        documentJobRepository.save(job);

        triggerForceSaveAndPublishFinalize(jobId, userId, refinementPrompt);
    }

    public void convertToPdf(String jobId) {
        String userId = UserContextHolder.getUserId();

        DocumentJob job = documentJobRepository.findByJobIdAndUserId(jobId, userId)
                .orElseThrow(() -> new DocumentJobNotFoundException(jobId));
        job.setStatus(JobStatus.IN_PROGRESS);
        documentJobRepository.save(job);

        triggerForceSaveAndPublishConvert(jobId, userId);
    }

    private void requestOnlyOfficeForceSave(String jobId) {
        try {
            DocumentJob job = documentJobRepository.findByJobId(jobId).orElse(null);
            if (job == null) return;

            Map<String, Object> body = Map.of(
                    "c", "forcesave",
                    "key", jobId + "_v" + job.getVersion()
            );

            webClient.post()
                    .uri(onlyOfficeInternalUrl + "/coauthoring/CommandService.ashx")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(5))
                    .doOnSuccess(resp -> log.info("Force save requested for job {}: {}", jobId, resp))
                    .doOnError(err -> log.warn("Force save request failed for job {}: {}", jobId, err.getMessage()))
                    .subscribe();

        } catch (Exception e) {
            log.warn("Could not request OnlyOffice force save for job {}: {}", jobId, e.getMessage());
        }
    }

    @Async
    public void triggerForceSaveAndPublishFinalize(String jobId, String userId, String refinementPrompt) {
        requestOnlyOfficeForceSave(jobId);
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
        DocumentFinalizeEvent event = new DocumentFinalizeEvent(jobId, userId, refinementPrompt);
        rabbitTemplate.convertAndSend(SharedRabbitConfig.EXCHANGE_NAME,
                SharedRabbitConfig.ROUTING_KEY, event);
    }

    @Async
    public void triggerForceSaveAndPublishConvert(String jobId, String userId) {
        requestOnlyOfficeForceSave(jobId);
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
        DocumentConvertEvent event = new DocumentConvertEvent(jobId, userId);
        rabbitTemplate.convertAndSend(SharedRabbitConfig.EXCHANGE_NAME,
                SharedRabbitConfig.CONVERT_ROUTING_KEY, event);
    }

    public JobStatusResponse getJobStatus(String jobId) {
        return documentJobRepository.findByJobIdAndUserId(jobId, UserContextHolder.getUserId())
                .map(this::mapToResponse)
                .orElseThrow(() -> new DocumentJobNotFoundException(jobId));
    }

    public List<JobStatusResponse> getAllJobs(String search) {
        List<DocumentJob> jobs = (search == null || search.isEmpty())
                ? documentJobRepository.findAllByUserIdOrderByLastEditedAtDesc(UserContextHolder.getUserId())
                : documentJobRepository.findByUserIdAndTitleContainingIgnoreCaseOrderByLastEditedAtDesc(
                UserContextHolder.getUserId(), search);

        return jobs.stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public void deleteDocument(String jobId) {
        documentJobRepository.findByJobIdAndUserId(jobId, UserContextHolder.getUserId())
                .orElseThrow(() -> new DocumentJobNotFoundException(jobId));

        documentJobRepository.deleteByJobIdAndUserId(jobId, UserContextHolder.getUserId());

        storageClient.deleteGeneric(jobId + ".docx");
        storageClient.deleteGeneric(jobId + ".pdf");

        log.info("Document job {} and associated files deleted", jobId);
    }

    public EditorConfigResponse buildEditorConfig(String jobId, String userId) {
        DocumentJob job = documentJobRepository.findByJobIdAndUserId(jobId, userId)
                .orElseThrow(() -> new UnauthorizedException("Document not found"));

        if (job.getStatus() != JobStatus.COMPLETED) {
            throw new ValidationException("Document is not ready for editing");
        }

        String editToken = editTokenService.generate(jobId, userId);

        String baseUrl = storageServiceUrl.endsWith("/")
                ? storageServiceUrl.substring(0, storageServiceUrl.length() - 1)
                : storageServiceUrl;

        String docxUrl = baseUrl + "/download-editing?token=" + editToken;
        String callbackUrl = baseUrl + "/callback?token=" + editToken;

        EditorConfigResponse config = EditorConfigResponse.builder()
                .document(EditorConfigResponse.DocumentConfig.builder()
                        .key(jobId + "_v" + job.getVersion())
                        .title(job.getTitle())
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
                        .mode("edit")
                        .lang("en-US")
                        .user(EditorConfigResponse.User.builder()
                                .id(userId)
                                .name("Legal Editor")
                                .build())
                        .customization(EditorConfigResponse.Customization.builder()
                                .forcesave(true)
                                .build())
                        .build())
                .build();

        config.setToken(onlyOfficeJwtService.buildToken(config));
        return config;
    }

    public boolean checkOwnership(String jobId, String userId) {
        return documentJobRepository.existsByJobIdAndUserId(jobId, userId);
    }

    private JobStatusResponse mapToResponse(DocumentJob job) {
        return JobStatusResponse.builder()
                .jobId(job.getJobId())
                .title(job.getTitle())
                .status(job.getStatus().name())
                .docxUrl(job.getDocxUrl())
                .pdfUrl(job.getPdfUrl())
                .errorDetails(job.getErrorDetails())
                .generatedContent(job.getGeneratedContent())
                .createdAt(job.getCreatedAt())
                .lastEditedAt(job.getLastEditedAt())
                .build();
    }
}