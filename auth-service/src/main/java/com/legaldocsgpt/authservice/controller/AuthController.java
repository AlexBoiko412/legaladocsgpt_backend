package com.legaldocsgpt.authservice.controller;

import com.legaldocsgpt.authservice.dto.UserInfoResponse;
import com.legaldocsgpt.authservice.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;



@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<TokenResponse> signup(@RequestBody AuthRequest request, HttpServletResponse response) {
        String token = authService.signup(request.getUsername(), request.getEmail(), request.getPassword());
        Cookie cookie = new Cookie("token", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(24 * 60 * 60);
        // cookie.setSecure(true);
        response.addCookie(cookie);
        return ResponseEntity.ok(new TokenResponse(token));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody AuthRequest request, HttpServletResponse response) {
        String token = authService.login(request.getUsername(), request.getEmail(), request.getPassword());
        Cookie cookie = new Cookie("token", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(24 * 60 * 60);
        // cookie.setSecure(true);
        response.addCookie(cookie);
        return ResponseEntity.ok(new TokenResponse(token));
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.replace("Bearer ", "");
        UserInfoResponse userInfo = authService.validateTokenAndGetUserInfo(token);

        if (userInfo != null) {
            return ResponseEntity.ok(userInfo);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @Data
    public static class AuthRequest {
        private String username;
        private String email;
        private String password;
    }


    public record TokenResponse(String token) {}
}
