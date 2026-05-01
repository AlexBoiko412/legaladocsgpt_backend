package com.legaldocsgpt.templateservice.controller;

import com.legaldocsgpt.shared.dto.TemplateDefinition;
import com.legaldocsgpt.templateservice.dto.TemplateRequest;
import com.legaldocsgpt.templateservice.mapper.TemplateMapper;
import com.legaldocsgpt.templateservice.model.Template;
import com.legaldocsgpt.templateservice.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequiredArgsConstructor
public class TemplateController {
    private final TemplateService templateService;
    private final TemplateMapper templateMapper;


    @GetMapping
    public List<TemplateDefinition> getTemplates() {
        return templateMapper.toDtoList(templateService.getAllTemplates());
    }

    @GetMapping("/{id}")
    public TemplateDefinition getTemplate(@PathVariable String id) {
        return templateMapper.toDto(templateService.getTemplateById(id));
    }


    @PostMapping(value = "/admin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TemplateDefinition> createTemplate(
            @ModelAttribute TemplateRequest request) throws Exception {
        Template saved = templateService.createTemplate(request);
        return ResponseEntity.status(201).body(templateMapper.toDto(saved));
    }

    @PutMapping(value = "/admin/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TemplateDefinition> updateTemplate(
            @PathVariable String id,
            @ModelAttribute TemplateRequest request) throws Exception {
        return ResponseEntity.ok(templateMapper.toDto(templateService.updateTemplate(id, request)));
    }

    @DeleteMapping("/admin/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }
}