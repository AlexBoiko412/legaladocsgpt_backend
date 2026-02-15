package com.legaldocsgpt.documentgenerator.controller;

import com.legaldocsgpt.documentgenerator.dto.GenerateResponse;
import com.legaldocsgpt.documentgenerator.exception.ValidationException;
import com.legaldocsgpt.documentgenerator.service.DocumentService;
import com.legaldocsgpt.shared.dto.FinalizeRequest;
import com.legaldocsgpt.shared.dto.GenerateRequest;
import com.legaldocsgpt.shared.dto.JobStatusResponse;
import com.legaldocsgpt.shared.dto.TemplateDefinition;
import com.legaldocsgpt.shared.service.TemplateRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping("/info")
    public ResponseEntity<String> generateDocumentInfo() {
        return ResponseEntity.ok("Document generator is on");
    }

    @GetMapping("/formats")
    public ResponseEntity<List<String>> getFormats() {
        return ResponseEntity.ok(List.of("PDF", "DOCX"));
    }

    @GetMapping("/templates")
    public ResponseEntity<List<TemplateDefinition>> getTemplates() {
        return ResponseEntity.ok(documentService.getTemplates());
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateResponse> generateDocument(@RequestBody GenerateRequest request) {
        return ResponseEntity.ok(documentService.generateDocument(request));
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable String jobId) {
        return ResponseEntity.ok(documentService.getJobStatus(jobId));
    }

    @PostMapping("/{jobId}/finalize")
    public ResponseEntity<Void> finalizeDocument(
            @PathVariable String jobId,
            @RequestBody FinalizeRequest request
    ) {
        if (request.getEditedContent() == null || request.getEditedContent().isBlank()) {
            throw new ValidationException("Cannot finalize: Document content is missing or empty.");
        }
        documentService.sendFinalizeEvent(jobId, request.getEditedContent());
        return ResponseEntity.accepted().build();
    }

    @GetMapping
    public ResponseEntity<List<JobStatusResponse>> getAllDocuments(
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(documentService.getAllJobs(search));
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String jobId) {
        documentService.deleteDocument(jobId);
        return ResponseEntity.noContent().build();
    }
}