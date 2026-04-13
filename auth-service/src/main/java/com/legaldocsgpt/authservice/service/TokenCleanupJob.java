package com.legaldocsgpt.authservice.service;

import com.legaldocsgpt.authservice.repository.AuthTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupJob {

    private final AuthTokenRepository tokenRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteExpiredTokens() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        tokenRepository.deleteOlderThan(cutoff);
        log.info("Cleaned up auth tokens older than 24h");
    }
}