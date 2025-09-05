package com.legaldocsgpt.authservice.security;

import com.legaldocsgpt.authservice.entity.User;
import com.legaldocsgpt.authservice.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final String redirectUri;

    public CustomOAuth2SuccessHandler(JwtUtil jwtUtil,
                                      UserRepository userRepository,
                                      @Value("${app.oauth2.redirectUri}") String redirectUri) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.redirectUri = redirectUri;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        String email = oauthUser.getAttribute("email");

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .username(email)
                        .email(email)
                        .password("")
                        .role("ROLE_USER")
                        .build()));

        String token = jwtUtil.generateToken(user.getUsername());

        response.addCookie(new Cookie("auth_token", token));
        response.sendRedirect(redirectUri);
    }
}


