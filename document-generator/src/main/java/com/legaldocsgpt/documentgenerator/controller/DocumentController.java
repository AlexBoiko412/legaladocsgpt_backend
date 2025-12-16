package com.legaldocsgpt.documentgenerator.controller;

import com.legaldocsgpt.documentgenerator.dto.GenerateRequest;
import com.legaldocsgpt.documentgenerator.dto.GenerateResponse;
import com.legaldocsgpt.documentgenerator.dto.TemplateInfo;
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

    @PostMapping("/generate")
    public ResponseEntity<GenerateResponse> generateDocument(@RequestBody GenerateRequest request) {
        return ResponseEntity.ok(documentService.generateDocument(request));
    }
    @GetMapping("/info")
    public ResponseEntity<String> generateDocumentInfo() {
        return ResponseEntity.ok("Document generator is on");
    }
    @GetMapping("/bebra")
    public ResponseEntity<String> bebra() {
        return ResponseEntity.ok("bebra on");
    }

    @GetMapping("/formats")
    public ResponseEntity<List<String>> getFormats() {
        return ResponseEntity.ok(List.of("PDF", "DOCX"));
    }

    @GetMapping("/templates")
    public ResponseEntity<List<TemplateInfo>> getTemplates() {
        return ResponseEntity.ok(documentService.getAvailableTemplates());
    }

    @PostMapping("/ai-generate")
    public ResponseEntity<GenerateResponse> aiGenerate(@RequestParam String description,
                                                       @RequestParam(defaultValue = "PDF") String format) {
        return ResponseEntity.ok(documentService.aiGenerate(description, format));
    }
}