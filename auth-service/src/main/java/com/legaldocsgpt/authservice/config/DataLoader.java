package com.legaldocsgpt.authservice.config;

import com.legaldocsgpt.authservice.entity.User;
import com.legaldocsgpt.authservice.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Seeds the admin account and an optional demo account (credentials from env).
 */
@Slf4j
@Configuration
public class DataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-password:admin123}")
    private String adminPassword;

    @Value("${app.seed.demo-email:}")
    private String demoEmail;

    @Value("${app.seed.demo-password:}")
    private String demoPassword;

    public DataLoader(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@admin.com");
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole("ROLE_ADMIN");
            admin.setProvider("LOCAL");
            userRepository.save(admin);
            log.info("Seed admin user created (username: admin)");
        }

        if (!demoEmail.isBlank() && !demoPassword.isBlank() && !userRepository.existsByEmail(demoEmail)) {
            User demo = new User();
            demo.setUsername("demo");
            demo.setEmail(demoEmail);
            demo.setPassword(passwordEncoder.encode(demoPassword));
            demo.setRole("ROLE_USER");
            demo.setProvider("LOCAL");
            userRepository.save(demo);
            log.info("Seed demo user created ({})", demoEmail);
        }
    }
}
