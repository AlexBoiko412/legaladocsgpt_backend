package com.legaldocsgpt.documentgenerator.controller;

import com.legaldocsgpt.documentgenerator.dto.GenerateResponse;
import com.legaldocsgpt.documentgenerator.exception.ValidationException;
import com.legaldocsgpt.documentgenerator.service.DocumentService;
import com.legaldocsgpt.shared.context.UserContextHolder;
import com.legaldocsgpt.shared.dto.*;
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


    @GetMapping("/{jobId}/editor-config")
    public ResponseEntity<EditorConfigResponse> getEditorConfig(@PathVariable String jobId) {
        String userId = UserContextHolder.getUserId();
        return ResponseEntity.ok(documentService.buildEditorConfig(jobId, userId));
    }
}