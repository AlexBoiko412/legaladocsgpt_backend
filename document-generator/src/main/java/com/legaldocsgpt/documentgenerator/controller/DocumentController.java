package com.legaldocsgpt.documentgenerator.controller;

import com.legaldocsgpt.documentgenerator.dto.FinalizeRequest;
import com.legaldocsgpt.documentgenerator.dto.GenerateResponse;
import com.legaldocsgpt.documentgenerator.dto.RenameRequest;
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

    @GetMapping("/ownership")
    public ResponseEntity<Boolean> checkOwnership(@RequestParam String jobId, @RequestParam String userId) {
        return ResponseEntity.ok(documentService.checkOwnership(jobId, userId));
    }

    @PatchMapping("/{jobId}/title")
    public ResponseEntity<Void> renameDocument(
            @PathVariable String jobId,
            @RequestBody RenameRequest request) {
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new ValidationException("Title cannot be empty");
        }
        documentService.renameDocument(jobId, request.getTitle().trim());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/templates")
    public ResponseEntity<List<TemplateDefinition>> getTemplates() {
        return ResponseEntity.ok(documentService.getTemplates());
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateResponse> generateDocument(@RequestBody GenerateRequest request) {
        return ResponseEntity.ok(documentService.generateDocument(request));
    }

    @PostMapping("/{jobId}/finalize")
    public ResponseEntity<Void> finalizeDocument(
            @PathVariable String jobId,
            @RequestBody(required = false) FinalizeRequest request) {
        String prompt = (request != null) ? request.getRefinementPrompt() : null;
        documentService.sendFinalizeEvent(jobId, prompt);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{jobId}/convert")
    public ResponseEntity<Void> finalizeDocument(
            @PathVariable String jobId
    ) {
        documentService.convertToPdf(jobId);
        return ResponseEntity.accepted().build();
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