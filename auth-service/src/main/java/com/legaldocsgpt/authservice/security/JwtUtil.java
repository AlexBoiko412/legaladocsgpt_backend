package com.legaldocsgpt.authservice.security;

import com.legaldocsgpt.authservice.dto.UserInfoResponseDto;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expiration;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration}") long expiration) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expiration = expiration;
    }

    public String generateToken(String username, String email, String role ) {
        return Jwts.builder()
                .subject(username)
                .claims(Map.of(
                        "email", email,
                        "role", role
                ))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public UserInfoResponseDto validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String username = claims.getSubject();
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);

            return new UserInfoResponseDto(email, username, role);

        } catch (JwtException e) {
            return null;
        }
    }
}
