package com.legaldocsgpt.documentgenerator.controller;

import com.legaldocsgpt.shared.dto.JobStatusResponse;
import com.legaldocsgpt.shared.dto.GenerateRequest;
import com.legaldocsgpt.documentgenerator.dto.GenerateResponse;
import com.legaldocsgpt.shared.dto.TemplateInfo;
import com.legaldocsgpt.documentgenerator.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
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
    public ResponseEntity<List<TemplateInfo>> getTemplates() {
        return ResponseEntity.ok(documentService.getAvailableTemplates());
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateResponse> generateDocument(@RequestBody GenerateRequest request) {
        // Later, you can extract this from the JWT token via @RequestHeader
        String mockUserId = "user-123";
        return ResponseEntity.ok(documentService.generateDocument(request, mockUserId));
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable String jobId) {
        return ResponseEntity.ok(documentService.getJobStatus(jobId));
    }

    @PostMapping("/{jobId}/finalize")
    public ResponseEntity<Void> finalizeDocument(@PathVariable String jobId, @RequestBody String editedContent) {
        documentService.sendFinalizeEvent(jobId, editedContent);
        return ResponseEntity.accepted().build(); // 202 Accepted means "we are working on it"
    }
}