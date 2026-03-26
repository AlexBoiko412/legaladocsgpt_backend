package com.legaldocsgpt.documentworker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legaldocsgpt.documentworker.client.TemplateClient;
import com.legaldocsgpt.documentworker.exception.AiProviderException;
import com.legaldocsgpt.documentworker.service.prompt.PromptBuilder;
import com.legaldocsgpt.documentworker.service.provider.OpenAIProvider;
import com.legaldocsgpt.shared.client.StorageClient;
import com.legaldocsgpt.shared.config.SharedRabbitConfig;
import com.legaldocsgpt.shared.dto.DocumentConvertEvent;
import com.legaldocsgpt.shared.dto.DocumentFinalizeEvent;
import com.legaldocsgpt.shared.dto.DocumentGenerationEvent;
import com.legaldocsgpt.shared.dto.TemplateDefinition;
import com.legaldocsgpt.shared.entity.DocumentJob;
import com.legaldocsgpt.shared.entity.JobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@RabbitListener(queues = "document_generation_queue")
public class DocumentWorker {

    private final DocumentJobInternalService jobService;
    private final OpenAIProvider aiProvider;
    private final PromptBuilder promptBuilder;
    private final StorageClient storageClient;
    private final TemplateClient templateClient;
    private final WordProcessingService wordService;
    private final WordToPdfService wordToPdfService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = SharedRabbitConfig.DLQ_NAME)
    public void handleDeadLetter(Message message) {
        try {
            String body = new String(message.getBody());
            log.error("Message exhausted all retries, moved to DLQ: {}", body);

            try {
                JsonNode node = objectMapper.readTree(body);

                String jobId = node.has("jobId") ? node.get("jobId").asText() : null;
                String userId = node.has("userId") ? node.get("userId").asText() : null;

                if (jobId != null && userId != null) {
                    jobService.failJob(jobId, userId, "Processing failed after multiple retries");
                    log.error("Marked job {} as FAILED after DLQ", jobId);
                }
            } catch (Exception parseEx) {
                log.error("Could not parse DLQ message body to extract jobId: {}", parseEx.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to process DLQ message: {}", e.getMessage());
        }
    }

    @RabbitHandler
    public void processInitialGeneration(DocumentGenerationEvent event) {
        String jobId = event.getJobId();
        String userId = event.getUserId();
        log.info("Starting generation for Job: {} User: {}", jobId, userId);

        try {
            jobService.updateStatus(jobId, userId, JobStatus.IN_PROGRESS);

            TemplateDefinition template = templateClient.getTemplateById(event.getTemplateId());
            String templatePath = template.getDocxPath();


            String finalPrompt = promptBuilder.buildFinalPrompt(event);
            String generatedContent = aiProvider.generateText(finalPrompt);
            log.info("AI generated {} chars for job {}",
                    generatedContent != null ? generatedContent.length() : 0, jobId);

            byte[] shellBytes = storageClient.downloadInternal(templatePath);
            byte[] assembledDocx = wordService.assembleDocument(shellBytes, generatedContent, event.getData());
            String docxUrl = jobId + ".docx";
            storageClient.uploadGeneric(docxUrl,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    assembledDocx);

            byte[] pdfBytes = wordToPdfService.convertToPdf(assembledDocx, docxUrl);

            String pdfUrl = jobId + ".pdf";
            storageClient.uploadGeneric(pdfUrl, "application/pdf", pdfBytes);

            jobService.completeJob(jobId, userId, docxUrl, pdfUrl, generatedContent);
            log.info("Job {} completed successfully", jobId);

        } catch (AiProviderException e) {
            log.error("AI provider failed for job {}: {}", jobId, e.getMessage());
            jobService.failJob(jobId, userId, "AI generation failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Transient error processing job {}, will retry: {}", jobId, e.getMessage());
            throw new RuntimeException("Job processing failed: " + e.getMessage(), e);
        }
    }

    @RabbitHandler
    public void processFinalizeEvent(DocumentFinalizeEvent event) {
        String jobId = event.getJobId();
        String userId = event.getUserId();
        log.info("AI REFINEMENT STARTED for job: {} ", jobId);

        try {
            DocumentJob job = jobService.getJob(jobId, event.getUserId());
            String docxName = jobId + ".docx";

            byte[] shellBytes = storageClient.downloadInternal(job.getTemplatePath());
            log.info("Downloaded clean template shell. Size: {} bytes", shellBytes.length);

            byte[] currentDocx = storageClient.downloadInternal(docxName);
            String currentBody = wordService.extractContent(currentDocx);
            log.info("Extracted current body for AI context. Length: {} chars", currentBody.length());

            String aiPrompt = buildRefinementPrompt(currentBody, event.getRefinementPrompt());
            String newBody = aiProvider.generateText(aiPrompt);
            log.info("AI returned new body. Length: {} chars", newBody != null ? newBody.length() : 0);

            if (newBody == null || newBody.trim().isEmpty()) {
                throw new RuntimeException("AI returned empty body");
            }

            Map<String, String> data = new HashMap<>(job.getDocumentData() != null ? job.getDocumentData() : new HashMap<>());

            byte[] refinedDocx = wordService.assembleDocument(shellBytes, newBody, data);
            log.info("Assembled refined document using clean shell. New size: {} bytes", refinedDocx.length);

            storageClient.uploadGeneric(docxName,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    refinedDocx);
            log.info("Upload completed for {}", docxName);

            byte[] pdfBytes = wordToPdfService.convertToPdf(refinedDocx, docxName);
            String pdfName = jobId + ".pdf";
            storageClient.uploadGeneric(pdfName, "application/pdf", pdfBytes);

            jobService.completeJob(jobId, event.getUserId(), docxName, job.getPdfUrl(), newBody);

            log.info("REFINEMENT COMPLETED SUCCESSFULLY for job {}", jobId);

        } catch (AiProviderException e) {
            log.error("AI refinement failed for job {}: {}", jobId, e.getMessage());
            jobService.failJob(jobId, userId, "AI refinement failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Transient error in refinement for job {}, will retry: {}", jobId, e.getMessage());
            throw new RuntimeException("Refinement failed: " + e.getMessage(), e);
        }
    }

    @RabbitHandler
    public void handleConvert(DocumentConvertEvent event) {
        String jobId = event.getJobId();
        String userId = event.getUserId();
        log.info("Converting current editor state to PDF for job: {}", jobId);

        try {
            String docxName = jobId + ".docx";
            String pdfName = jobId + ".pdf";

            byte[] currentDocx = storageClient.downloadInternal(docxName);
            byte[] pdfBytes = wordToPdfService.convertToPdf(currentDocx, docxName);
            storageClient.uploadGeneric(pdfName, "application/pdf", pdfBytes);

            jobService.completeJob(jobId, userId, docxName, pdfName, null);
            log.info("PDF sync complete for job: {}", jobId);

        } catch (Exception e) {
            log.error("Conversion failed for job {}: {}", jobId, e.getMessage());
            jobService.failJob(jobId, userId, "PDF conversion failed: " + e.getMessage());
        }
    }

    private String buildRefinementPrompt(String currentBody, String userPrompt) {
        return String.format(
                "You are a senior legal document editor.\n\n" +
                        "### CURRENT DOCUMENT BODY ###\n%s\n\n" +
                        "### USER REFINEMENT REQUEST ###\n%s\n\n" +
                        "### STRICT INSTRUCTIONS ###\n" +
                        "- Return ONLY the revised body content (the clauses between parties and signatures).\n" +
                        "- Do NOT include document title, parties section, or signature block.\n" +
                        "- Do NOT repeat any template headings like 'TERMS AND CONDITIONS'.\n" +
                        "- Start directly with the first article or clause.\n" +
                        "- Use markdown: ## for headings, **bold**, * for bullets.\n" +
                        "- Preserve existing meaning unless the user explicitly asks to change it.\n" +
                        "- Return clean, well-structured legal text only.",
                currentBody, userPrompt
        );
    }
}