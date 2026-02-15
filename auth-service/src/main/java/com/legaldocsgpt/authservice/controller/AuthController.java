package com.legaldocsgpt.authservice.controller;

import com.legaldocsgpt.authservice.dto.UserInfoResponseDto;
import com.legaldocsgpt.authservice.dto.UserTokenInfo;
import com.legaldocsgpt.authservice.exception.InvalidCredentialsException;
import com.legaldocsgpt.authservice.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody AuthRequest request, HttpServletResponse response) {
        String token = authService.signup(request.getUsername(), request.getEmail(), request.getPassword());
        addTokenCookie(response, token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request, HttpServletResponse response) {
        String token = authService.login(request.getUsername(), request.getEmail(), request.getPassword());
        addTokenCookie(response, token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("token", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setSecure(false);
        cookie.setAttribute("SameSite", "Strict");

        response.addCookie(cookie);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/validate")
    public ResponseEntity<UserInfoResponseDto> validateToken(
            @CookieValue(name = "token", required = false) String token) {

        if (token == null || token.isEmpty()) {
            throw new InvalidCredentialsException();
        }

        return ResponseEntity.ok(authService.validateTokenAndUserInDB(token));
    }

    @GetMapping("/me")
    public ResponseEntity<UserTokenInfo> getUserInfo(
            @CookieValue(name = "token", required = false) String token) {

        if (token == null || token.isEmpty()) {
            throw new InvalidCredentialsException();
        }

        return ResponseEntity.ok(authService.getDecryptedToken(token));
    }


    private void addTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("token", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(24000);
        //
        cookie.setSecure(false);
        //
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }




    @Data
    public static class AuthRequest {
        private String username;
        private String email;
        private String password;
    }
}
