package com.legaldocsgpt.documentworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.legaldocsgpt")
@EnableJpaRepositories(basePackages = {
        "com.legaldocsgpt.shared.repository"
})
@EntityScan(basePackages = {
        "com.legaldocsgpt.documentworker.entity",
        "com.legaldocsgpt.shared.entity"
})
@EnableFeignClients(basePackages = "com.legaldocsgpt.shared.client")
public class DocumentWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocumentWorkerApplication.class, args);
    }
}