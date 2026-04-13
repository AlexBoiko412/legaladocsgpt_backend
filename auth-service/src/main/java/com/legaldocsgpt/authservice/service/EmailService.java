package com.legaldocsgpt.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.site-url}")
    private String siteUrl;

    @Async
    public void sendPasswordResetEmail(String toEmail, String username, String rawToken) {
        String resetLink = siteUrl + "/reset-password?token=" + rawToken;

        String body = """
                Hi %s,
                
                You requested a password reset for your LegaldocsGPT account.
                
                Click the link below to choose a new password. This link expires in 1 hour.
                
                  %s
                
                If you didn't request this, you can safely ignore this email.
                
                - The LegaldocsGPT Team
                """.formatted(username, resetLink);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject("Reset your LegaldocsGPT password");
            message.setText(body);
            mailSender.send(message);
            log.info("Password reset email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }
}