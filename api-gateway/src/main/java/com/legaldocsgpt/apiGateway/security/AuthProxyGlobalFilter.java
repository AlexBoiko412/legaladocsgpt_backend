package com.legaldocsgpt.apiGateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.legaldocsgpt.apiGateway.dto.UserInfoResponseDto;
import com.legaldocsgpt.shared.dto.ErrorResponse;
import com.legaldocsgpt.shared.exception.GlobalErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class AuthProxyGlobalFilter implements GlobalFilter, Ordered {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private final Cache<String, UserInfoResponseDto> tokenCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    public AuthProxyGlobalFilter(WebClient.Builder builder, ObjectMapper objectMapper) {
        this.webClient = builder.baseUrl("http://auth-service:8081").build();
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        var cookie = exchange.getRequest().getCookies().getFirst("token");
        if (cookie == null || cookie.getValue().isEmpty()) {
            return handleUnauthorized(exchange, GlobalErrorCode.UNAUTHORIZED);
        }

        String token = cookie.getValue();

        UserInfoResponseDto cachedUser = tokenCache.getIfPresent(token);
        if (cachedUser != null) {
            log.debug("Token cache hit for user {}", cachedUser.getId());
            if (isAdminPath(path) && !isAdmin(cachedUser)) {
                return handleForbidden(exchange);
            }

            return chain.filter(withUserHeader(exchange, cachedUser));
        }

        return webClient.get()
                .uri("/validate")
                .header(HttpHeaders.COOKIE, "token=" + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> Mono.error(new RuntimeException("Invalid Token")))
                .toEntity(UserInfoResponseDto.class)
                .flatMap(response -> {
                    UserInfoResponseDto user = response.getBody();
                    if (user == null || user.getId() == null) {
                        return handleUnauthorized(exchange, GlobalErrorCode.INVALID_TOKEN);
                    }
                    tokenCache.put(token, user);
                    if (isAdminPath(path) && !isAdmin(user)) {
                        return handleForbidden(exchange);
                    }
                    return chain.filter(withUserHeader(exchange, user));
                })
                .onErrorResume(err -> {
                    log.warn("Token validation failed: {}", err.getMessage());
                    tokenCache.invalidate(token);
                    return handleUnauthorized(exchange, GlobalErrorCode.INVALID_TOKEN);
                });
    }

    private ServerWebExchange withUserHeader(ServerWebExchange exchange, UserInfoResponseDto user) {
        return exchange.mutate()
                .request(r -> r
                        .header("X-User-Id", String.valueOf(user.getId()))
                        .header("X-User-Role", user.getRole()))
                .build();
    }

    private boolean isWhitelisted(String path) {
        return path.contains("/api/auth/login") ||
                path.contains("/api/auth/signup") ||
                path.contains("/api/auth/oauth2/") ||
                path.contains("/api/auth/logout") ||
                path.contains("/api/auth/forgot-password") ||
                path.contains("/api/auth/reset-password") ||
                path.contains("/api/storage/download-editing") ||
                path.contains("/api/storage/callback");
    }

    private boolean isAdminPath(String path) {
        return path.startsWith("/api/templates/admin");
    }

    private boolean isAdmin(UserInfoResponseDto user) {
        return "ROLE_ADMIN".equals(user.getRole());
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private Mono<Void> handleUnauthorized(ServerWebExchange exchange, GlobalErrorCode errorCode) {
        exchange.getResponse().setStatusCode(errorCode.getStatus());
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getDefaultMessage())
                .timestamp(LocalDateTime.now())
                .path(exchange.getRequest().getPath().value())
                .traceId(UUID.randomUUID().toString().substring(0, 8))
                .build();

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Error serializing error response", e);
            return exchange.getResponse().setComplete();
        }
    }

    private Mono<Void> handleForbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        var body = "{\"code\":\"AUTH_103\",\"message\":\"Admin access required\"}";
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes());
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
