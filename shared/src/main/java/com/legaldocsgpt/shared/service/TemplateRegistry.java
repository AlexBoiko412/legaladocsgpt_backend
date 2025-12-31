package com.legaldocsgpt.shared.service;

import com.legaldocsgpt.shared.dto.TemplateDefinition;
import com.legaldocsgpt.shared.dto.TemplateDefinition.TemplateField;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class TemplateRegistry {

    private final List<TemplateDefinition> templates = Arrays.asList(
            TemplateDefinition.builder()
                    .id("service-agreement")
                    .name("Professional Service Agreement")
                    .description("Standard contract for freelance or B2B services.")
                    .systemPrompt("You are a senior legal counsel. Draft a formal agreement. " +
                            "IMPORTANT: Output the content in CLEAN HTML format using only <h3>, <p>, <strong>, and <ul>/<li> tags. " +
                            "Do not include <html> or <body> tags. Output ONLY the fragment. " +
                            "Ensure there are no conversational fillers.")
                    .fields(Arrays.asList(
                            TemplateField.builder().key("clientName").label("Client Name").type("text").placeholder("Bebra Corp").required(true).build(),
                            TemplateField.builder().key("serviceProvider").label("Service Provider").type("text").placeholder("Your Company").required(true).build(),
                            TemplateField.builder().key("amount").label("Contract Amount ($)").type("number").placeholder("5000").required(true).build(),
                            TemplateField.builder().key("scope").label("Scope of Work").type("textarea").placeholder("Describe services...").required(true).build()
                    ))
                    .build(),

            TemplateDefinition.builder()
                    .id("nda")
                    .name("Non-Disclosure Agreement (NDA)")
                    .description("Protect confidential information between two parties.")
                    .systemPrompt("You are a senior legal counsel. Draft a formal agreement. " +
                            "IMPORTANT: Output the content in CLEAN HTML format using only <h3>, <p>, <strong>, and <ul>/<li> tags. " +
                            "Do not include <html> or <body> tags. Output ONLY the fragment. " +
                            "Ensure there are no conversational fillers.")
                    .fields(Arrays.asList(
                            TemplateField.builder().key("partyA").label("Disclosing Party").type("text").placeholder("Company A").required(true).build(),
                            TemplateField.builder().key("partyB").label("Receiving Party").type("text").placeholder("Company B").required(true).build(),
                            TemplateField.builder().key("duration").label("Protection Period (Years)").type("number").placeholder("5").required(true).build()
                    ))
                    .build()
    );

    public List<TemplateDefinition> getAllTemplates() { return templates; }

    public Optional<TemplateDefinition> getTemplate(String id) {
        return templates.stream().filter(t -> t.getId().equals(id)).findFirst();
    }
}