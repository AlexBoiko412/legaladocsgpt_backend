package com.legaldocsgpt.authservice.security;

import com.legaldocsgpt.authservice.dto.UserTokenInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JwtUtil.
 *
 * JwtUtil takes its secret and expiration via constructor args (from @Value),
 * so we instantiate it directly with test values - no Spring needed.
 *
 * The secret must be a Base64-encoded string long enough for HS256 (≥ 32 bytes).
 *
 * What we test:
 *  - generateToken() produces a non-blank JWT
 *  - generateToken() embeds username, email, role correctly
 *  - validateToken() returns correct UserTokenInfo for a valid token
 *  - validateToken() returns null for an expired token
 *  - validateToken() returns null for a tampered token
 *  - validateToken() returns null for garbage input
 *  - validateToken() returns null for a token signed with a different secret
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    // 64 random bytes Base64-encoded - satisfies HS256 key length requirement
    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(
            "this-is-a-test-secret-key-that-is-long-enough-for-hs256-algorithm!".getBytes()
    );
    private static final long EXPIRATION_MS = 3_600_000L; // 1 hour

    private static final String USERNAME = "johndoe";
    private static final String EMAIL    = "john@example.com";
    private static final String ROLE     = "ROLE_USER";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET, EXPIRATION_MS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // generateToken()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("generateToken")
    class GenerateTokenTests {

        @Test
        void shouldGenerateNonNullToken() {
            String token = jwtUtil.generateToken("johndoe", "john@example.com", "ROLE_USER");
            assertThat(token).isNotNull().isNotBlank();
        }

        @Test
        void shouldGenerateValidJwtStructure() {
            String token = jwtUtil.generateToken("johndoe", "john@example.com", "ROLE_USER");
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        void shouldProduceDifferentTokensForDifferentUsers() {
            String token1 = jwtUtil.generateToken("alice", "alice@example.com", "ROLE_USER");
            String token2 = jwtUtil.generateToken("bob", "bob@example.com", "ROLE_USER");
            assertThat(token1).isNotEqualTo(token2);
        }

        @Test
        void shouldProduceDifferentTokensForDifferentRoles() {
            String token1 = jwtUtil.generateToken("johndoe", "john@example.com", "ROLE_USER");
            String token2 = jwtUtil.generateToken("johndoe", "john@example.com", "ROLE_ADMIN");
            assertThat(token1).isNotEqualTo(token2);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validateToken() - happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateToken() - valid token")
    class ValidateValidTokenTests {

        @Test
        @DisplayName("returns UserTokenInfo with correct username")
        void shouldReturnCorrectUsername() {
            String token = jwtUtil.generateToken(USERNAME, EMAIL, ROLE);
            UserTokenInfo info = jwtUtil.validateToken(token);

            assertThat(info).isNotNull();
            assertThat(info.getUsername()).isEqualTo(USERNAME);
        }

        @Test
        @DisplayName("returns UserTokenInfo with correct email")
        void shouldReturnCorrectEmail() {
            String token = jwtUtil.generateToken(USERNAME, EMAIL, ROLE);
            UserTokenInfo info = jwtUtil.validateToken(token);

            assertThat(info).isNotNull();
            assertThat(info.getEmail()).isEqualTo(EMAIL);
        }

        @Test
        @DisplayName("returns UserTokenInfo with correct role")
        void shouldReturnCorrectRole() {
            String token = jwtUtil.generateToken(USERNAME, EMAIL, ROLE);
            UserTokenInfo info = jwtUtil.validateToken(token);

            assertThat(info).isNotNull();
            assertThat(info.getRole()).isEqualTo(ROLE);
        }

        @Test
        @DisplayName("validate is the inverse of generate")
        void generateAndValidateShouldRoundTrip() {
            String token = jwtUtil.generateToken(USERNAME, EMAIL, ROLE);
            UserTokenInfo info = jwtUtil.validateToken(token);

            assertThat(info).isNotNull();
            assertThat(info.getUsername()).isEqualTo(USERNAME);
            assertThat(info.getEmail()).isEqualTo(EMAIL);
            assertThat(info.getRole()).isEqualTo(ROLE);
        }

        @Test
        @DisplayName("preserves ROLE_ADMIN role correctly")
        void shouldPreserveAdminRole() {
            String token = jwtUtil.generateToken("admin", "admin@example.com", "ROLE_ADMIN");
            UserTokenInfo info = jwtUtil.validateToken(token);

            assertThat(info).isNotNull();
            assertThat(info.getRole()).isEqualTo("ROLE_ADMIN");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validateToken() - rejection cases (all should return null, not throw)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateToken() - invalid token")
    class ValidateInvalidTokenTests {

        @Test
        @DisplayName("returns null for an expired token")
        void shouldReturnNullForExpiredToken() {
            // Create JwtUtil with -1ms expiration so token is already expired
            JwtUtil expiredJwtUtil = new JwtUtil(TEST_SECRET, -1L);
            String expiredToken = expiredJwtUtil.generateToken(USERNAME, EMAIL, ROLE);

            UserTokenInfo result = jwtUtil.validateToken(expiredToken);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null for a token signed with a different secret")
        void shouldReturnNullForDifferentSecret() {
            String differentSecret = Base64.getEncoder().encodeToString(
                    "different-secret-key-that-is-long-enough-for-hs256-algorithm!!".getBytes()
            );
            JwtUtil otherJwtUtil = new JwtUtil(differentSecret, EXPIRATION_MS);
            String foreignToken = otherJwtUtil.generateToken(USERNAME, EMAIL, ROLE);

            UserTokenInfo result = jwtUtil.validateToken(foreignToken);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null for a completely random string")
        void shouldReturnNullForGarbageToken() {
            UserTokenInfo result = jwtUtil.validateToken("not.a.jwt.token");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null for an empty string")
        void shouldReturnNullForEmptyString() {
            UserTokenInfo result = jwtUtil.validateToken("");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null for a structurally valid JWT with tampered payload")
        void shouldReturnNullForTamperedPayload() {
            String validToken = jwtUtil.generateToken(USERNAME, EMAIL, ROLE);

            // Split JWT into header.payload.signature
            String[] parts = validToken.split("\\.");

            // Modify the payload (Base64 decode, change content, re-encode)
            String decodedPayload = new String(Base64.getUrlDecoder().decode(parts[1]));
            String tamperedPayload = decodedPayload.replace(USERNAME, "hacker");
            String reEncodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(tamperedPayload.getBytes());

            // Reassemble with original signature - signature no longer matches
            String tamperedToken = parts[0] + "." + reEncodedPayload + "." + parts[2];

            UserTokenInfo result = jwtUtil.validateToken(tamperedToken);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null for a token with none algorithm (algorithm confusion attack)")
        void shouldReturnNullForNoneAlgorithm() {
            // Craft a JWT with "none" algorithm - a known JWT attack
            String header = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"sub\":\"hacker\",\"email\":\"hack@evil.com\",\"role\":\"ROLE_ADMIN\"}".getBytes());
            String noneAlgToken = header + "." + payload + ".";

            UserTokenInfo result = jwtUtil.validateToken(noneAlgToken);

            assertThat(result).isNull();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Security property tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security properties")
    class SecurityPropertyTests {

        @Test
        @DisplayName("token for ROLE_USER cannot be validated as ROLE_ADMIN after tampering")
        void tamperedRoleShouldBeRejected() {
            String userToken = jwtUtil.generateToken(USERNAME, EMAIL, "ROLE_USER");
            String[] parts = userToken.split("\\.");

            // Try to elevate role to ROLE_ADMIN by tampering with payload
            String decodedPayload = new String(Base64.getUrlDecoder().decode(parts[1]));
            String tamperedPayload = decodedPayload.replace("ROLE_USER", "ROLE_ADMIN");
            String reEncoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(tamperedPayload.getBytes());
            String tamperedToken = parts[0] + "." + reEncoded + "." + parts[2];

            // Must be rejected - signature no longer matches
            UserTokenInfo result = jwtUtil.validateToken(tamperedToken);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("null is not returned for a freshly generated token")
        void freshTokenShouldNeverBeNull() {
            // Regression guard: make sure we didn't break the happy path
            for (int i = 0; i < 5; i++) {
                String token = jwtUtil.generateToken(USERNAME + i, EMAIL, ROLE);
                assertThat(jwtUtil.validateToken(token))
                        .as("Token %d should be valid", i)
                        .isNotNull();
            }
        }
    }
}