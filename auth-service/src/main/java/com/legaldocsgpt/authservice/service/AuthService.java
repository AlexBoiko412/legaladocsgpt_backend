package com.legaldocsgpt.authservice.service;

import com.legaldocsgpt.authservice.dto.UserInfoResponseDto;
import com.legaldocsgpt.authservice.dto.UserProfileResponse;
import com.legaldocsgpt.authservice.dto.UserTokenInfo;
import com.legaldocsgpt.authservice.entity.AuthToken;
import com.legaldocsgpt.authservice.entity.User;
import com.legaldocsgpt.authservice.exception.InvalidCredentialsException;
import com.legaldocsgpt.authservice.exception.InvalidInputException;
import com.legaldocsgpt.authservice.exception.UserAlreadyExistsException;
import com.legaldocsgpt.authservice.exception.UserNotFoundException;
import com.legaldocsgpt.authservice.repository.UserRepository;
import com.legaldocsgpt.authservice.security.JwtUtil;
import com.legaldocsgpt.shared.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenService tokenService;
    private final EmailService emailService;

    public String signup(String username, String email, String password) {
        if (username == null || username.trim().isEmpty() || username.length() < 3) {
            throw new InvalidInputException(GlobalErrorCode.INVALID_INPUT, "Username too short");
        }

        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("A user with this username already exists");
        }

        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("A user with this email already exists");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role("ROLE_USER")
                .build();

        user = userRepository.save(user);
        return jwtUtil.generateToken(user.getUsername(), user.getEmail(), user.getRole());
    }

    public String login(String username, String email, String password) {
        User user = findByUsernameOrEmail(username, email);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return jwtUtil.generateToken(user.getUsername(), user.getEmail(), user.getRole());
    }

    public UserInfoResponseDto validateTokenAndUserInDB(String token) {
        UserTokenInfo userInfo = jwtUtil.validateToken(token);
        if (userInfo == null) {
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findByEmail(userInfo.getEmail())
                .orElseThrow(UserNotFoundException::new);

        return new UserInfoResponseDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }

    public UserTokenInfo getDecryptedToken(String token) {
        UserTokenInfo userInfo = jwtUtil.validateToken(token);

        if (userInfo == null) {
            throw new InvalidCredentialsException();
        }

        return userInfo;
    }

    public UserProfileResponse getUserProfile(String token) {
        UserTokenInfo info = jwtUtil.validateToken(token);
        if (info == null) throw new InvalidCredentialsException();

        User user = userRepository.findByEmail(info.getEmail())
                .orElseThrow(UserNotFoundException::new);

        return new UserProfileResponse(
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.getProvider() != null ? user.getProvider() : "LOCAL"
        );
    }

    public void changePassword(String token, String currentPassword, String newPassword) {
        UserTokenInfo info = jwtUtil.validateToken(token);
        if (info == null) throw new InvalidCredentialsException();

        User user = userRepository.findByEmail(info.getEmail())
                .orElseThrow(UserNotFoundException::new);

        if (!"LOCAL".equals(user.getProvider())) {
            throw new InvalidInputException(GlobalErrorCode.INVALID_INPUT,
                    "Password cannot be changed for Google accounts");
        }

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        if (newPassword == null || newPassword.length() < 8) {
            throw new InvalidInputException(GlobalErrorCode.INVALID_INPUT,
                    "New password must be at least 8 characters");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void forgotPassword(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            log.debug("Forgot password requested for unknown email: {}", email);
            return;
        }

        User user = userOpt.get();

        if (!"LOCAL".equals(user.getProvider()) && user.getProvider() != null) {
            log.debug("Forgot password requested for OAuth user: {}", email);
            return;
        }

        String rawToken = tokenService.createToken(user.getId(), AuthToken.TokenType.PASSWORD_RESET);
        emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), rawToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new InvalidInputException(GlobalErrorCode.INVALID_INPUT,
                    "New password must be at least 8 characters");
        }

        AuthToken authToken = tokenService
                .validateToken(rawToken, AuthToken.TokenType.PASSWORD_RESET)
                .orElseThrow(() -> new InvalidInputException(
                        GlobalErrorCode.INVALID_INPUT,
                        "Reset token is invalid or has expired"));

        User user = userRepository.findById(authToken.getUserId())
                .orElseThrow(UserNotFoundException::new);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenService.markUsed(authToken);

        log.info("Password reset completed for userId={}", user.getId());
    }

    private User findByUsernameOrEmail(String username, String email) {
        if (email != null && !email.isBlank()) {
            return userRepository.findByEmail(email).orElseThrow(UserNotFoundException::new);
        } else if (username != null && !username.isBlank()) {
            return userRepository.findByUsername(username).orElseThrow(UserNotFoundException::new);
        }
        throw new InvalidInputException(GlobalErrorCode.INVALID_INPUT, "Please provide email or username");
    }
}