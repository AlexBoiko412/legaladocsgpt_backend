package com.legaldocsgpt.documentworker.service;

import com.legaldocsgpt.documentworker.exception.AiProviderException;
import com.legaldocsgpt.documentworker.exception.PdfGenerationException;
import com.legaldocsgpt.documentworker.service.prompt.PromptBuilder;
import com.legaldocsgpt.documentworker.service.provider.OpenAIProvider;
import com.legaldocsgpt.shared.client.StorageClient;
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
    private final StorageClient storageClient;
    private final WordProcessingService wordService;
    private final WordToPdfService wordToPdfService;

    @RabbitHandler
    public void processInitialGeneration(DocumentGenerationEvent event) {
        String jobId = event.getJobId();
        String userId = event.getUserId();
        log.info("Starting generation for Job: {} User: {}", jobId, userId);

        try {
            jobService.updateStatus(jobId, userId, JobStatus.IN_PROGRESS);

            String finalPrompt = promptBuilder.buildFinalPrompt(event);
            String generatedContent = aiProvider.generateText(finalPrompt);

            byte[] shellBytes = storageClient.downloadGeneric(event.getDocxPath());

            byte[] assembledDocx = wordService.assembleDocument(shellBytes, generatedContent, event.getData());

            String fileName = event.getJobId() + ".docx";
            storageClient.uploadGeneric(fileName, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", assembledDocx);

            jobService.saveGeneratedContent(jobId, userId, generatedContent);
            jobService.completeJob(jobId, userId, fileName, null);
            log.info("Job {} completed successfully", jobId);

        } catch (AiProviderException | PdfGenerationException e) {
            log.error("Business error in job {}: {}", jobId, e.getMessage());
            jobService.failJob(jobId, userId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("CRITICAL error in job {}: ", jobId, e);
            jobService.failJob(jobId, userId, "System error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }


}