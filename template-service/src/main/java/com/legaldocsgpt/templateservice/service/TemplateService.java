package com.legaldocsgpt.templateservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legaldocsgpt.shared.client.StorageClient;
import com.legaldocsgpt.shared.exception.EntityNotFoundException;
import com.legaldocsgpt.templateservice.dto.TemplateRequest;
import com.legaldocsgpt.templateservice.model.Template;
import com.legaldocsgpt.templateservice.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateService {
    private final TemplateRepository repository;

    private final StorageClient storageClient;
    private final ObjectMapper objectMapper;

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
    public Template createTemplate(TemplateRequest request) throws Exception {
        String docxPath = uploadShell(request.getFile());
        List<Template.TemplateField> fields = parseFields(request.getFields());

        return repository.save(Template.builder()
                .name(request.getName())
                .description(request.getDescription())
                .systemPrompt(request.getSystemPrompt())
                .docxPath(docxPath)
                .fields(fields)
                .build());
    }

    @CacheEvict(value = {"templates", "template"}, allEntries = true)
    public Template updateTemplate(String id, TemplateRequest request) throws Exception {
        Template existing = getTemplateById(id);

        if (request.getFile() != null && !request.getFile().isEmpty()) {
            existing.setDocxPath(uploadShell(request.getFile()));
        }

        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.setSystemPrompt(request.getSystemPrompt());
        existing.setFields(parseFields(request.getFields()));

        return repository.save(existing);
    }

    @CacheEvict(value = {"templates", "template"}, allEntries = true)
    public void deleteTemplate(String id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("Template " + id + " not found");
        }
        repository.deleteById(id);
    }

    private String uploadShell(MultipartFile file) throws Exception {
        String docxPath = "templates/" + file.getOriginalFilename();
        storageClient.uploadGeneric(
                docxPath,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                file.getBytes());
        return docxPath;
    }

    private List<Template.TemplateField> parseFields(String fieldsJson) throws Exception {
        return objectMapper.readValue(fieldsJson, new TypeReference<>() {});
    }
}