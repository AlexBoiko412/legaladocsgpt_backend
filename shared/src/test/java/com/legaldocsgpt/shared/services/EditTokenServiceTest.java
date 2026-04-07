package com.legaldocsgpt.shared.services;

import com.legaldocsgpt.shared.dto.EditTokenClaims;
import com.legaldocsgpt.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for EditTokenService.
 *
 * What test:
 *  - generate() produces a valid Base64 token
 *  - verify() returns correct claims for a valid token
 *  - verify() rejects expired tokens
 *  - verify() rejects tampered tokens (wrong signature)
 *  - verify() rejects malformed / garbage input
 *  - tokens from different secrets are rejected
 */
class EditTokenServiceTest {

    private EditTokenService service;

    private static final String TEST_SECRET = "test-secret-key-for-unit-tests-only";
    private static final String JOB_ID      = "550e8400-e29b-41d4-a716-446655440000";
    private static final String USER_ID     = "user-123";

    @BeforeEach
    void setUp() {
        service = new EditTokenService();
        ReflectionTestUtils.setField(service, "secret", TEST_SECRET);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // generate()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("generate()")
    class GenerateTests {

        @Test
        @DisplayName("returns a non-null non-blank token")
        void shouldReturnNonBlankToken() {
            String token = service.generate(JOB_ID, USER_ID);
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("returns a valid Base64 URL-encoded string")
        void shouldReturnValidBase64UrlEncoding() {
            String token = service.generate(JOB_ID, USER_ID);

            // Should not throw - token must be valid Base64 URL
            assertThatCode(() -> Base64.getUrlDecoder().decode(token))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("decoded token contains jobId and userId")
        void shouldEncodeJobIdAndUserId() {
            String token = service.generate(JOB_ID, USER_ID);
            String decoded = new String(Base64.getUrlDecoder().decode(token));

            assertThat(decoded).contains(JOB_ID);
            assertThat(decoded).contains(USER_ID);
        }

        @Test
        @DisplayName("two tokens for the same input are different (different expiry timestamps)")
        void shouldProduceDifferentTokensForSameInput() throws InterruptedException {
            String token1 = service.generate(JOB_ID, USER_ID);
            Thread.sleep(2);
            String token2 = service.generate(JOB_ID, USER_ID);

            assertThat(token1).isNotEqualTo(token2);
        }

        @Test
        @DisplayName("generates tokens for different jobIds independently")
        void shouldGenerateIndependentTokensForDifferentJobIds() {
            String token1 = service.generate("job-aaa", USER_ID);
            String token2 = service.generate("job-bbb", USER_ID);

            assertThat(token1).isNotEqualTo(token2);

            // Each token should only decode to its own jobId
            String decoded1 = new String(Base64.getUrlDecoder().decode(token1));
            String decoded2 = new String(Base64.getUrlDecoder().decode(token2));

            assertThat(decoded1).contains("job-aaa").doesNotContain("job-bbb");
            assertThat(decoded2).contains("job-bbb").doesNotContain("job-aaa");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // verify() - happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("verify() - valid token")
    class VerifyValidTests {

        @Test
        @DisplayName("returns claims with correct jobId")
        void shouldReturnCorrectJobId() {
            String token = service.generate(JOB_ID, USER_ID);
            EditTokenClaims claims = service.verify(token);

            assertThat(claims.jobId()).isEqualTo(JOB_ID);
        }

        @Test
        @DisplayName("returns claims with correct userId")
        void shouldReturnCorrectUserId() {
            String token = service.generate(JOB_ID, USER_ID);
            EditTokenClaims claims = service.verify(token);

            assertThat(claims.userId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("verify is the inverse of generate")
        void generateAndVerifyShouldRoundTrip() {
            String token = service.generate(JOB_ID, USER_ID);
            EditTokenClaims claims = service.verify(token);

            assertThat(claims.jobId()).isEqualTo(JOB_ID);
            assertThat(claims.userId()).isEqualTo(USER_ID);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // verify() - rejection cases
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("verify() - invalid token")
    class VerifyInvalidTests {

        @Test
        @DisplayName("throws UnauthorizedException for expired token")
        void shouldRejectExpiredToken() throws Exception {
            // Build a token manually with an expiry in the past
            long expiredTimestamp = System.currentTimeMillis() - 1000; // 1 second ago
            String payload = JOB_ID + ":" + USER_ID + ":" + expiredTimestamp;

            // Sign it with the correct secret so the signature check passes -
            // it should still fail on the expiry check
            String sig = computeHmac(payload, TEST_SECRET);
            String rawToken = payload + ":" + sig;
            String token = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(rawToken.getBytes());

            assertThatThrownBy(() -> service.verify(token))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("throws UnauthorizedException for tampered payload")
        void shouldRejectTamperedPayload() {
            String token = service.generate(JOB_ID, USER_ID);

            // Decode, change the userId, re-encode without updating the signature
            String decoded = new String(Base64.getUrlDecoder().decode(token));
            String tampered = decoded.replace(USER_ID, "hacker-999");
            String tamperedToken = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(tampered.getBytes());

            assertThatThrownBy(() -> service.verify(tamperedToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("signature");
        }

        @Test
        @DisplayName("throws UnauthorizedException for tampered jobId")
        void shouldRejectTamperedJobId() {
            String token = service.generate(JOB_ID, USER_ID);

            String decoded = new String(Base64.getUrlDecoder().decode(token));
            String tampered = decoded.replace(JOB_ID, "different-job-id");
            String tamperedToken = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(tampered.getBytes());

            assertThatThrownBy(() -> service.verify(tamperedToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("signature");
        }

        @Test
        @DisplayName("throws UnauthorizedException for completely random garbage")
        void shouldRejectGarbageToken() {
            assertThatThrownBy(() -> service.verify("this.is.not.a.valid.token"))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("throws UnauthorizedException for empty string")
        void shouldRejectEmptyString() {
            assertThatThrownBy(() -> service.verify(""))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("throws UnauthorizedException for token signed with a different secret")
        void shouldRejectTokenSignedWithDifferentSecret() {
            // Create a second service instance with a different secret
            EditTokenService otherService = new EditTokenService();
            ReflectionTestUtils.setField(otherService, "secret", "completely-different-secret");

            String tokenFromOtherService = otherService.generate(JOB_ID, USER_ID);

            // Our service (with TEST_SECRET) should reject it
            assertThatThrownBy(() -> service.verify(tokenFromOtherService))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("signature");
        }

        @Test
        @DisplayName("throws UnauthorizedException for Base64 with missing signature section")
        void shouldRejectTokenWithMissingSignature() {
            String payloadOnly = JOB_ID + ":" + USER_ID + ":9999999999999";
            String token = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payloadOnly.getBytes());

            assertThatThrownBy(() -> service.verify(token))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("throws UnauthorizedException for non-numeric expiry")
        void shouldRejectNonNumericExpiry() {
            String corruptPayload = JOB_ID + ":" + USER_ID + ":not-a-number:somesig";
            String token = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(corruptPayload.getBytes());

            assertThatThrownBy(() -> service.verify(token))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper - replicates the HMAC logic so expired token test can build
    // a correctly-signed but expired token
    // ─────────────────────────────────────────────────────────────────────────

    private String computeHmac(String data, String secret) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(data.getBytes()));
    }
}