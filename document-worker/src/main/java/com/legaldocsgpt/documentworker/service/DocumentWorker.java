package com.legaldocsgpt.documentworker.service;

import com.legaldocsgpt.documentworker.service.prompt.PromptBuilder;
import com.legaldocsgpt.documentworker.service.provider.OpenAIProvider;
import com.legaldocsgpt.shared.dto.DocumentFinalizeEvent;
import com.legaldocsgpt.shared.entity.DocumentJob;
import com.legaldocsgpt.shared.entity.JobStatus;
import com.legaldocsgpt.shared.dto.DocumentGenerationEvent;
import com.legaldocsgpt.shared.repository.DocumentJobRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@RabbitListener(queuesToDeclare = @Queue(name = "document_generation_queue", durable = "true"))
@Transactional
public class DocumentWorker {

    private final DocumentJobRepository repository;
    private final OpenAIProvider aiProvider;
    private final PromptBuilder promptBuilder;
    private final PdfService pdfService;

    @RabbitHandler
    public void processInitialGeneration(DocumentGenerationEvent event) {
        log.info("Received job for processing: {}", event.getJobId());

        DocumentJob job = repository.findByJobId(event.getJobId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + event.getJobId()));

        job.setStatus(JobStatus.IN_PROGRESS);
        repository.save(job);

        try {
            log.info("Building prompt for job: {}", event.getJobId());
            String finalPrompt = promptBuilder.buildFinalPrompt(event);

            log.info("Requesting legal text from AI for template: {}", event.getTemplateId());
            String generatedContent = aiProvider.generateText(finalPrompt);

            job.setGeneratedContent(generatedContent);
            repository.save(job);

            log.info("Successfully generated legal text. Creating PDF...");
            String fileUrl = pdfService.generatePdf(event.getJobId(), generatedContent);

            job.setFileUrl(fileUrl);
            job.setStatus(JobStatus.COMPLETED);
            repository.save(job);
            log.info("Pdf saved.");
        } catch (Exception e) {
            log.error("Failed to process document job: {}", event.getJobId(), e);
            job.setStatus(JobStatus.FAILED);
            job.setErrorDetails(e.getMessage());
            repository.save(job);
        }
    }

    @RabbitHandler
    public void processFinalization(DocumentFinalizeEvent event) {
        log.info("Finalizing PDF for job: {}", event.getJobId());

        DocumentJob job = repository.findByJobId(event.getJobId()).orElseThrow();

        try {
            job.setGeneratedContent(event.getEditedContent());
            job.setStatus(JobStatus.IN_PROGRESS);
            repository.save(job);

            String fileUrl = pdfService.generatePdf(event.getJobId(), event.getEditedContent());

            job.setFileUrl(fileUrl);
            job.setStatus(JobStatus.COMPLETED);
            repository.save(job);
            log.info("Final PDF ready for job: {}", event.getJobId());
        } catch (Exception e) {
            log.error("Failed to finalize job: {}", event.getJobId(), e);
            job.setStatus(JobStatus.FAILED);
            repository.save(job);
        }
    }
}