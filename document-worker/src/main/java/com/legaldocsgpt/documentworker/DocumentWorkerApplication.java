package com.legaldocsgpt.documentworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = "com.legaldocsgpt")
@EnableFeignClients(basePackages = {
        "com.legaldocsgpt.documentworker.client",
        "com.legaldocsgpt.shared.client"
})
@EnableJpaRepositories(basePackages = "com.legaldocsgpt.shared.repository")
@EntityScan(basePackages = "com.legaldocsgpt.shared.entity")
public class DocumentWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocumentWorkerApplication.class, args);
    }
}