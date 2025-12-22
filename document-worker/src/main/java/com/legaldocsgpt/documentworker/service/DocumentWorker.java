package com.legaldocsgpt.documentworker.service;

import com.legaldocsgpt.documentworker.service.prompt.PromptBuilder;
import com.legaldocsgpt.documentworker.service.provider.AIProvider;
import com.legaldocsgpt.documentworker.service.provider.OpenAIProvider;
import com.legaldocsgpt.shared.dto.DocumentFinalizeEvent;
import com.legaldocsgpt.shared.entity.DocumentJob;
import com.legaldocsgpt.shared.entity.JobStatus;
import com.legaldocsgpt.shared.dto.DocumentGenerationEvent;
import com.legaldocsgpt.shared.repository.DocumentJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentWorker {

    private final DocumentJobRepository repository;
    private final OpenAIProvider aiProvider;
    private final PromptBuilder promptBuilder;
    private final PdfService pdfService;

    @RabbitListener(queuesToDeclare = @Queue(name = "document_generation_queue", durable = "true"))
    public void processInitialGeneration(DocumentGenerationEvent event) {
        log.info("Received job for processing: {}", event.getJobId());

        // 1. Fetch Job and set to IN_PROGRESS
        DocumentJob job = repository.findByJobId(event.getJobId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + event.getJobId()));

        job.setStatus(JobStatus.IN_PROGRESS);
        repository.save(job);

        try {
            // 2. Build the AI Prompt
            log.info("Building prompt for job: {}", event.getJobId());
            String prompt = promptBuilder.buildPrompt(event);

            // 3. Call OpenAI (The heavy work)
            log.info("Requesting legal text from AI provider...");
            String generatedContent = aiProvider.generateText(prompt);
            log.info("AI generated content. Creating PDF...");

            job.setGeneratedContent(generatedContent);
            repository.save(job);

            // 4. PDF Generation (Placeholder for now)
            log.info("Successfully generated legal text. Length: {} chars", generatedContent.length());

            String fileUrl = pdfService.generatePdf(event.getJobId(), generatedContent);
            job.setFileUrl(fileUrl);
            job.setStatus(JobStatus.COMPLETED);
            repository.save(job);

        } catch (Exception e) {
            log.error("Failed to process document job: {}", event.getJobId(), e);
            job.setStatus(JobStatus.FAILED);
            job.setErrorDetails(e.getMessage());
        } finally {
            repository.save(job);
        }
    }

    @RabbitHandler
    public void processFinalization(DocumentFinalizeEvent event) {
        log.info("Finalizing PDF for job: {}", event.getJobId());

        DocumentJob job = repository.findByJobId(event.getJobId()).orElseThrow();

        try {
            // Generate PDF from the EDITED content
            String fileUrl = pdfService.generatePdf(event.getJobId(), event.getEditedContent());

            job.setFileUrl(fileUrl);
            job.setStatus(JobStatus.COMPLETED);
            repository.save(job);
            log.info("Final PDF ready for job: {}", event.getJobId());
        } catch (Exception e) {
            job.setStatus(JobStatus.FAILED);
            repository.save(job);
        }
    }
}