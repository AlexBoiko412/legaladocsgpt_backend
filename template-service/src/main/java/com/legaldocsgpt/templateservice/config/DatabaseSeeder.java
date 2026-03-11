package com.legaldocsgpt.templateservice.config;

import com.legaldocsgpt.shared.client.StorageClient;
import com.legaldocsgpt.templateservice.model.Template;
import com.legaldocsgpt.templateservice.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final TemplateRepository repository;
    private final StorageClient storageClient;
    @Qualifier("webApplicationContext")
    private final ResourceLoader resourceLoader;

    @Override
    public void run(String... args) {
        if (repository.count() == 0) {
            log.info("Starting template seeding process...");
            seedTemplates();
        }
    }

    private void seedTemplates() {
        seedSingleTemplate(
                "tpl_employment_001",
                "Employment Agreement",
                "employment_agreement.docx",
                "You are a senior HR lawyer. Draft the body of an employment contract.",
                "Standard full-time employment contract for general staff.",
                List.of(
                        Template.TemplateField.builder().key("employeeName").label("Employee Full Name").type("text").required(true).build(),
                        Template.TemplateField.builder().key("jobTitle").label("Job Title").type("text").required(true).build(),
                        Template.TemplateField.builder().key("salary").label("Annual Salary (e.g. $50,000)").type("text").required(true).build(),
                        Template.TemplateField.builder().key("startDate").label("Start Date").type("date").required(true).build(),
                        Template.TemplateField.builder().key("companyName").label("Company Name").type("text").required(true).build(),
                        Template.TemplateField.builder().key("contractTerm").label("Contract Duration (e.g. 1 year, indefinite)").type("text").required(true).build(),
                        Template.TemplateField.builder().key("noticePeriod").label("Notice Period (e.g. 30 days)").type("text").required(true).build(),
                        Template.TemplateField.builder().key("jurisdiction").label("Governing Jurisdiction (e.g. New York, USA)").type("text").required(true).build(),
                        Template.TemplateField.builder().key("nonCompetePeriod").label("Non-Compete Period After Termination (e.g. 1 year)").type("text").required(true).build()
                )
        );

        seedSingleTemplate(
                "tpl_nda_001",
                "Non-Disclosure Agreement",
                "nda.docx",
                "You are a corporate attorney. Draft the body of a mutual NDA.",
                "Mutual NDA for protecting business secrets during negotiations.",
                List.of(
                        Template.TemplateField.builder().key("partyA").label("First Party Name").type("text").required(true).build(),
                        Template.TemplateField.builder().key("partyB").label("Second Party Name").type("text").required(true).build(),
                        Template.TemplateField.builder().key("purpose").label("Purpose of Discussion").type("text").placeholder("e.g. Potential Partnership").required(true).build(),
                        Template.TemplateField.builder().key("effectiveDate").label("Effective Date").type("date").required(true).build(),
                        Template.TemplateField.builder().key("confidentialityTerm").label("Confidentiality Period (e.g. 2 years)").type("text").required(true).build(),
                        Template.TemplateField.builder().key("jurisdiction").label("Governing Jurisdiction (e.g. California, USA)").type("text").required(true).build(),
                        Template.TemplateField.builder().key("disputeResolution").label("Dispute Resolution Method (e.g. arbitration, litigation)").type("text").required(true).build()
                )
        );
    }

    private void seedSingleTemplate(String id, String name, String fileName, String prompt, String description, List<Template.TemplateField> fields) {
        try {
            Resource resource = resourceLoader.getResource("classpath:templates/legal-shells/" + fileName);
            byte[] fileContent = resource.getInputStream().readAllBytes();

            String minioPath = "templates/" + fileName;
            String docxMimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

            storageClient.uploadGeneric(minioPath, docxMimeType, fileContent);

            Template template = Template.builder()
                    .id(id)
                    .name(name)
                    .description(description)
                    .docxPath(minioPath)
                    .systemPrompt(prompt)
                    .fields(fields)
                    .build();

            repository.save(template);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            log.error("Failed to seed template {}: {}", name, e.getMessage());
        }

        log.info("Successfully seeded {} templates.", repository.count());
    }
}