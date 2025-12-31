package com.legaldocsgpt.documentgenerator.controller;

import com.legaldocsgpt.shared.dto.FinalizeRequest;
import com.legaldocsgpt.shared.dto.JobStatusResponse;
import com.legaldocsgpt.shared.dto.GenerateRequest;
import com.legaldocsgpt.documentgenerator.dto.GenerateResponse;
import com.legaldocsgpt.shared.dto.TemplateDefinition;
import com.legaldocsgpt.documentgenerator.service.DocumentService;
import com.legaldocsgpt.shared.service.TemplateRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class DocumentController {

    private final DocumentService documentService;
    private final TemplateRegistry templateRegistry;

    public DocumentController(DocumentService documentService, TemplateRegistry templateRegistry) {

        this.documentService = documentService;
        this.templateRegistry = templateRegistry;
    }

    @GetMapping("/info")
    public ResponseEntity<String> generateDocumentInfo() {
        return ResponseEntity.ok("Document generator is on");
    }

    @GetMapping("/formats")
    public ResponseEntity<List<String>> getFormats() {
        return ResponseEntity.ok(List.of("PDF", "DOCX"));
    }

    @GetMapping("/templates")
    public List<TemplateDefinition> getTemplates() {
        return templateRegistry.getAllTemplates();
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateResponse> generateDocument(@RequestBody GenerateRequest request) {
        String mockUserId = "user-123";
        return ResponseEntity.ok(documentService.generateDocument(request, mockUserId));
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
        if (request.getEditedContent() == null) {
            return ResponseEntity.badRequest().build();
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