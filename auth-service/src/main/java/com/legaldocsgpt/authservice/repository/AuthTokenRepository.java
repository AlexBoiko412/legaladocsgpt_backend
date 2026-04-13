package com.legaldocsgpt.authservice.repository;

import com.legaldocsgpt.authservice.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE AuthToken t SET t.usedAt = :now WHERE t.userId = :userId AND t.type = :type AND t.usedAt IS NULL")
    void invalidateAllForUser(Long userId, AuthToken.TokenType type, LocalDateTime now);

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.createdAt < :cutoff")
    void deleteOlderThan(LocalDateTime cutoff);
}