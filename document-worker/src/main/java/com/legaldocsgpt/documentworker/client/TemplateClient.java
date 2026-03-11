package com.legaldocsgpt.documentworker.client;

import com.legaldocsgpt.shared.dto.TemplateDefinition;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "template-service", url = "http://template-service:8083")
public interface TemplateClient {

    @GetMapping("")
    List<TemplateDefinition> getAllTemplates();

    @GetMapping("/{id}")
    TemplateDefinition getTemplateById(@PathVariable("id") String id);
}