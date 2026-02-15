package com.legaldocsgpt.templateservice.config;

import com.legaldocsgpt.templateservice.model.Template;
import com.legaldocsgpt.templateservice.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final TemplateRepository repository;

    @Override
    public void run(String... args) {
        if (repository.count() == 0) {
            log.info("Seed data: MongoDB is empty. Populating legal templates...");
            seedTemplates();
        } else {
            log.info("Seed data: Templates already exist in MongoDB. Skipping.");
        }
    }

    private void seedTemplates() {
        Template employmentContract = Template.builder()
                .id("tpl_employment_001")
                .name("Employment Agreement")
                .description("Standard full-time employment contract for general staff.")
                .systemPrompt("Generate a professional employment contract based on the following details. Ensure clauses for termination, confidentiality, and IP are included.")
                .fields(List.of(
                        Template.TemplateField.builder().key("employeeName").label("Employee Full Name").type("text").required(true).build(),
                        Template.TemplateField.builder().key("jobTitle").label("Job Title").type("text").required(true).build(),
                        Template.TemplateField.builder().key("salary").label("Annual Salary").type("number").required(true).build(),
                        Template.TemplateField.builder().key("startDate").label("Start Date").type("date").required(true).build()
                ))
                .build();

        Template nda = Template.builder()
                .id("tpl_nda_001")
                .name("Non-Disclosure Agreement (NDA)")
                .description("Mutual NDA for protecting business secrets during negotiations.")
                .systemPrompt("Generate a mutual NDA. Focus on the definition of Confidential Information and a 3-year survival period.")
                .fields(List.of(
                        Template.TemplateField.builder().key("partyA").label("First Party Name").type("text").required(true).build(),
                        Template.TemplateField.builder().key("partyB").label("Second Party Name").type("text").required(true).build(),
                        Template.TemplateField.builder().key("purpose").label("Purpose of Discussion").type("text").placeholder("e.g. Potential Partnership").required(true).build()
                ))
                .build();

        repository.saveAll(List.of(employmentContract, nda));
        log.info("Successfully seeded {} templates.", repository.count());
    }
}