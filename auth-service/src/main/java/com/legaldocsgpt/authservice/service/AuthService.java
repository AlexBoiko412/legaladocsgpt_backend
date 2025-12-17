package com.legaldocsgpt.authservice.service;

import com.legaldocsgpt.authservice.dto.UserInfoResponseDto;
import com.legaldocsgpt.authservice.entity.User;
import com.legaldocsgpt.authservice.exception.InvalidCredentialsException;
import com.legaldocsgpt.authservice.exception.InvalidInputException;
import com.legaldocsgpt.authservice.exception.UserAlreadyExistsException;
import com.legaldocsgpt.authservice.exception.UserNotFoundException;
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


    public String signup(String username,  String email, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new InvalidInputException("Username is required");
        }
        if (email == null || !email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw new InvalidInputException("Invalid email format");
        }
        if (password == null || password.length() < 8) {
            throw new InvalidInputException("Password must be at least 8 characters");
        }
        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("Username already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email already exists");
        }


        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role("ROLE_USER")
                .build();
        userRepository.save(user);
        return jwtUtil.generateToken(username, user.getEmail(), user.getRole());
    }

    public String login(String username, String email, String password) {
        User user;
        if (email != null && !email.isBlank()) {
            user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        } else if (username != null && !username.isBlank()) {
            user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));
        } else {
            throw new InvalidInputException("Username or email must be provided");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        return jwtUtil.generateToken(user.getUsername(), user.getEmail(), user.getRole());
    }

    public UserInfoResponseDto validateTokenAndUserInDB(String token) {
        UserInfoResponseDto userInfo = jwtUtil.validateToken(token);
        if (userInfo == null) {
            return null;
        }
        User user = userRepository.findByEmail(userInfo.getEmail()).orElse(null);
        if (user == null) {
            return null;
        }
        return userInfo;
    }

    public UserInfoResponseDto getDecryptedToken(String token) {
        UserInfoResponseDto userInfo = jwtUtil.validateToken(token);
        if (userInfo == null) {
            return null;
        }

        return userInfo;
    }

}
