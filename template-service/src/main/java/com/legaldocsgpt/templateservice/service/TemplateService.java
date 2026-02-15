package com.legaldocsgpt.templateservice.service;

import com.legaldocsgpt.shared.exception.EntityNotFoundException;
import com.legaldocsgpt.templateservice.model.Template;
import com.legaldocsgpt.templateservice.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateService {
    private final TemplateRepository repository;

    @Cacheable(value = "templates")
    public List<Template> getAllTemplates() {
        return repository.findAll();
    }

    @Cacheable(value = "template", key = "#id")
    public Template getTemplateById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Template " + id + " not found"));
    }

    @CacheEvict(value = {"templates", "template"}, allEntries = true)
    public Template saveTemplate(Template template) {
        return repository.save(template);
    }
}