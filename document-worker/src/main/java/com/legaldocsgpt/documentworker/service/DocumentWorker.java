package com.legaldocsgpt.documentworker.service;

import com.legaldocsgpt.documentworker.exception.AiProviderException;
import com.legaldocsgpt.documentworker.exception.PdfGenerationException;
import com.legaldocsgpt.documentworker.service.prompt.PromptBuilder;
import com.legaldocsgpt.documentworker.service.provider.OpenAIProvider;
import com.legaldocsgpt.shared.dto.DocumentFinalizeEvent;
import com.legaldocsgpt.shared.dto.DocumentGenerationEvent;
import com.legaldocsgpt.shared.entity.JobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@RabbitListener(queues = "document_generation_queue")
public class DocumentWorker {

    private final DocumentJobInternalService jobService;
    private final OpenAIProvider aiProvider;
    private final PromptBuilder promptBuilder;
    private final PdfService pdfService;

    @RabbitHandler
    public void processInitialGeneration(DocumentGenerationEvent event) {
        String jobId = event.getJobId();
        String userId = event.getUserId();
        log.info("Starting generation for Job: {} User: {}", jobId, userId);

        try {
            jobService.updateStatus(jobId, userId, JobStatus.IN_PROGRESS);

            String finalPrompt = promptBuilder.buildFinalPrompt(event);
            String generatedContent = aiProvider.generateText(finalPrompt);

            jobService.saveGeneratedContent(jobId, userId, generatedContent);

            String fileUrl = pdfService.generatePdf(jobId, generatedContent);

            jobService.completeJob(jobId, userId, fileUrl, generatedContent);
            log.info("Job {} completed successfully", jobId);

        } catch (AiProviderException | PdfGenerationException e) {
            log.error("Business error in job {}: {}", jobId, e.getMessage());
            jobService.failJob(jobId, userId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("CRITICAL error in job {}: ", jobId, e);
            jobService.failJob(jobId, userId, "System error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            throw e;
        }
    }

    @RabbitHandler
    public void processFinalizeEvent(DocumentFinalizeEvent event) {
        String jobId = event.getJobId();
        String userId = event.getUserId();

        log.info("Received Finalize Event for job: {}", jobId);

        try {
            String fileUrl = pdfService.generatePdf(jobId, event.getEditedContent());

            jobService.completeJob(jobId, userId, fileUrl, event.getEditedContent());

            log.info("Job {} re-finalized and updated successfully", jobId);

        } catch (Exception e) {
            log.error("Failed to finalize PDF for job {}: {}", jobId, e.getMessage());
            jobService.failJob(jobId, userId, "Finalization failed: " + e.getMessage());
            throw e;
        }
    }
}