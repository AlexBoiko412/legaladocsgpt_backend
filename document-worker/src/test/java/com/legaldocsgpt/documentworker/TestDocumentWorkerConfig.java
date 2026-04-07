package com.legaldocsgpt.documentworker;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {
        "com.legaldocsgpt.shared.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.legaldocsgpt.shared.repository"
})
public class TestDocumentWorkerConfig {
}