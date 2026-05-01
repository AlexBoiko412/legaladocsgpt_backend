package com.legaldocsgpt.authservice.service;

import com.legaldocsgpt.authservice.entity.AuthToken;
import com.legaldocsgpt.authservice.repository.AuthTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final int TOKEN_BYTES = 32;
    private static final int RESET_TTL_HOURS = 1;

    private final AuthTokenRepository tokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a raw token, store its hash, return the raw token for emailing.
     * Any existing unused tokens of the same type for this user are invalidated first.
     */
    @Transactional
    public String createToken(Long userId, AuthToken.TokenType type) {
        tokenRepository.invalidateAllForUser(userId, type, LocalDateTime.now());

        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = HexFormat.of().formatHex(bytes);

        AuthToken token = AuthToken.builder()
                .userId(userId)
                .tokenHash(hash(rawToken))
                .type(type)
                .expiresAt(LocalDateTime.now().plusHours(RESET_TTL_HOURS))
                .build();

        tokenRepository.save(token);
        log.debug("Created {} token for userId={}", type, userId);
        return rawToken;
    }

    /**
     * Find and validate a token by its raw value.
     * Returns the AuthToken if valid, empty if not found or expired or already used.
     */
    public Optional<AuthToken> validateToken(String rawToken, AuthToken.TokenType expectedType) {
        return tokenRepository.findByTokenHash(hash(rawToken))
                .filter(t -> t.getType() == expectedType)
                .filter(t -> {
                    boolean valid = t.isValid();
                    log.debug("Token valid={}, usedAt={}, expiresAt={}", valid, t.getUsedAt(), t.getExpiresAt());
                    return valid;
                });
    }

    /**
     * Mark a token as used (single-use enforcement).
     */
    @Transactional
    public void markUsed(AuthToken token) {
        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}