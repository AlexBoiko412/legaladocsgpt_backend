package com.legaldocsgpt.authservice.service;

import com.legaldocsgpt.authservice.dto.UserTokenInfo;
import com.legaldocsgpt.authservice.entity.User;
import com.legaldocsgpt.authservice.exception.InvalidCredentialsException;
import com.legaldocsgpt.authservice.exception.InvalidInputException;
import com.legaldocsgpt.authservice.exception.UserAlreadyExistsException;
import com.legaldocsgpt.authservice.exception.UserNotFoundException;
import com.legaldocsgpt.authservice.repository.UserRepository;
import com.legaldocsgpt.authservice.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;

    @InjectMocks AuthService authService;

    // ── Signup ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("signup")
    class SignupTests {

        @Test
        void shouldReturnTokenOnSuccessfulSignup() {
            when(userRepository.existsByUsername("alice")).thenReturn(false);
            when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtUtil.generateToken("alice", "alice@example.com", "ROLE_USER"))
                    .thenReturn("mock-token");

            String token = authService.signup("alice", "alice@example.com", "password123");

            assertThat(token).isEqualTo("mock-token");
        }

        @Test
        void shouldThrowWhenUsernameTooShort() {
            assertThatThrownBy(() -> authService.signup("ab", "a@b.com", "password123"))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("too short");
        }

        @Test
        void shouldThrowWhenUsernameAlreadyExists() {
            when(userRepository.existsByUsername("alice")).thenReturn(true);

            assertThatThrownBy(() -> authService.signup("alice", "alice@example.com", "password123"))
                    .isInstanceOf(UserAlreadyExistsException.class);
        }

        @Test
        void shouldThrowWhenEmailAlreadyExists() {
            when(userRepository.existsByUsername("alice")).thenReturn(false);
            when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.signup("alice", "alice@example.com", "password123"))
                    .isInstanceOf(UserAlreadyExistsException.class);
        }

        @Test
        void shouldAssignRoleUser() {
            when(userRepository.existsByUsername(any())).thenReturn(false);
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtUtil.generateToken(any(), any(), eq("ROLE_USER"))).thenReturn("token");

            authService.signup("alice", "alice@example.com", "password123");

            // Verify ROLE_USER was passed to generateToken, not ROLE_ADMIN
            verify(jwtUtil).generateToken("alice", "alice@example.com", "ROLE_USER");
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login")
    class LoginTests {

        private final User existingUser = User.builder()
                .username("alice")
                .email("alice@example.com")
                .password("hashed-password")
                .role("ROLE_USER")
                .provider("LOCAL")
                .build();

        @Test
        void shouldReturnTokenOnSuccessfulLogin() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
            when(jwtUtil.generateToken("alice", "alice@example.com", "ROLE_USER"))
                    .thenReturn("mock-token");

            String token = authService.login("alice", null, "password123");

            assertThat(token).isEqualTo("mock-token");
        }

        @Test
        void shouldLoginByEmail() {
            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
            when(jwtUtil.generateToken(any(), any(), any())).thenReturn("mock-token");

            String token = authService.login(null, "alice@example.com", "password123");

            assertThat(token).isEqualTo("mock-token");
        }

        @Test
        void shouldThrowOnWrongPassword() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

            assertThatThrownBy(() -> authService.login("alice", null, "wrong"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login("nobody", null, "password123"))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        void shouldThrowWhenNeitherUsernameNorEmailProvided() {
            assertThatThrownBy(() -> authService.login(null, null, "password123"))
                    .isInstanceOf(InvalidInputException.class);
        }
    }

    // ── changePassword ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("changePassword")
    class ChangePasswordTests {

        private final User localUser = User.builder()
                .username("alice")
                .email("alice@example.com")
                .password("old-hashed")
                .role("ROLE_USER")
                .provider("LOCAL")
                .build();

        @BeforeEach
        void setupValidToken() {
            lenient().when(jwtUtil.validateToken("valid-token"))
                    .thenReturn(new UserTokenInfo("alice@example.com", "alice", "ROLE_USER"));
            lenient().when(userRepository.findByEmail("alice@example.com"))
                    .thenReturn(Optional.of(localUser));
        }

        @Test
        void shouldChangePasswordSuccessfully() {
            when(passwordEncoder.matches("oldpassword", "old-hashed")).thenReturn(true);
            when(passwordEncoder.encode("newpassword123")).thenReturn("new-hashed");

            authService.changePassword("valid-token", "oldpassword", "newpassword123");

            verify(userRepository).save(argThat(u -> u.getPassword().equals("new-hashed")));
        }

        @Test
        void shouldThrowOnWrongCurrentPassword() {
            when(passwordEncoder.matches("wrongpassword", "old-hashed")).thenReturn(false);

            assertThatThrownBy(() ->
                    authService.changePassword("valid-token", "wrongpassword", "newpassword123"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        void shouldThrowWhenNewPasswordTooShort() {
            when(passwordEncoder.matches("oldpassword", "old-hashed")).thenReturn(true);

            assertThatThrownBy(() ->
                    authService.changePassword("valid-token", "oldpassword", "short"))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("8 characters");
        }

        @Test
        void shouldThrowForGoogleAccount() {
            User googleUser = User.builder()
                    .username("alice")
                    .email("alice@example.com")
                    .password("")
                    .role("ROLE_USER")
                    .provider("GOOGLE")
                    .build();
            when(userRepository.findByEmail("alice@example.com"))
                    .thenReturn(Optional.of(googleUser));

            assertThatThrownBy(() ->
                    authService.changePassword("valid-token", "anything", "newpassword123"))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("Google");
        }

        @Test
        void shouldThrowOnInvalidToken() {
            when(jwtUtil.validateToken("bad-token")).thenReturn(null);

            assertThatThrownBy(() ->
                    authService.changePassword("bad-token", "oldpassword", "newpassword123"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }
}