package com.legaldocsgpt.templateservice.controller;

import com.legaldocsgpt.shared.dto.TemplateDefinition;
import com.legaldocsgpt.templateservice.mapper.TemplateMapper;
import com.legaldocsgpt.templateservice.service.TemplateService;
import lombok.RequiredArgsConstructor;
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
}