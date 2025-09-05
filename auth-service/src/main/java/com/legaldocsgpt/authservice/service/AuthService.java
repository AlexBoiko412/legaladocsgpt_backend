package com.legaldocsgpt.authservice.service;

import com.legaldocsgpt.authservice.dto.UserInfoResponse;
import com.legaldocsgpt.authservice.entity.User;
import com.legaldocsgpt.authservice.repository.UserRepository;
import com.legaldocsgpt.authservice.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public String signup(String email, String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new IllegalArgumentException("Invalid email format");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }


        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role("ROLE_USER")
                .build();
        userRepository.save(user);
        return jwtUtil.generateToken(username);
    }

    public String login(String username, String email, String password) {
        User user;
        if(email != null) {
            user = userRepository.findByUsername(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
        } else if(username != null) {
            user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
        } else {
            throw new RuntimeException("Invalid username or email");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }
        return jwtUtil.generateToken(username);
    }

    public UserInfoResponse validateTokenAndGetUserInfo(String token) {
        String username = jwtUtil.validateToken(token);
        if (username == null) {
            return null;
        }
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return null;
        }
        return new UserInfoResponse(user.getId(), user.getUsername(), user.getRole());
    }
}
