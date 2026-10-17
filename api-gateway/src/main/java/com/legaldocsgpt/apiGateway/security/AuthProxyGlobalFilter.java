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

        if (isInternalOnly(path)) {
            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }

        // identity headers may only be set by the gateway
        ServerWebExchange sanitized = exchange.mutate()
                .request(r -> r.headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Role");
                }))
                .build();

        if (isWhitelisted(path)) {
            return chain.filter(sanitized);
        }

        var cookie = sanitized.getRequest().getCookies().getFirst("token");
        if (cookie == null || cookie.getValue().isEmpty()) {
            return handleUnauthorized(sanitized, GlobalErrorCode.UNAUTHORIZED);
        }

        String token = cookie.getValue();

        UserInfoResponseDto cachedUser = tokenCache.getIfPresent(token);
        if (cachedUser != null) {
            log.debug("Token cache hit for user {}", cachedUser.getId());
            return proceed(sanitized, chain, path, cachedUser);
        }

        // only a failed /validate call means 401; downstream errors pass through as-is
        return webClient.get()
                .uri("/validate")
                .header(HttpHeaders.COOKIE, "token=" + token)
                .retrieve()
                .bodyToMono(UserInfoResponseDto.class)
                .map(u -> java.util.Optional.of(u))
                .onErrorResume(err -> {
                    log.warn("Token validation failed: {}", err.getMessage());
                    tokenCache.invalidate(token);
                    return Mono.just(java.util.Optional.<UserInfoResponseDto>empty());
                })
                .defaultIfEmpty(java.util.Optional.<UserInfoResponseDto>empty())
                .flatMap(maybeUser -> {
                    UserInfoResponseDto user = maybeUser.orElse(null);
                    if (user == null || user.getId() == null) {
                        return handleUnauthorized(sanitized, GlobalErrorCode.INVALID_TOKEN);
                    }
                    tokenCache.put(token, user);
                    return proceed(sanitized, chain, path, user);
                });
    }

    private Mono<Void> proceed(ServerWebExchange exchange, GatewayFilterChain chain,
                               String path, UserInfoResponseDto user) {
        if (isAdminPath(path) && !isAdmin(user)) {
            return handleForbidden(exchange);
        }
        return chain.filter(withUserHeader(exchange, user));
    }

    private ServerWebExchange withUserHeader(ServerWebExchange exchange, UserInfoResponseDto user) {
        return exchange.mutate()
                .request(r -> r
                        .header("X-User-Id", String.valueOf(user.getId()))
                        .header("X-User-Role", user.getRole()))
                .build();
    }

    private boolean isWhitelisted(String path) {
        return path.equals("/api/auth/login") ||
                path.equals("/api/auth/signup") ||
                path.equals("/api/auth/logout") ||
                path.equals("/api/auth/forgot-password") ||
                path.equals("/api/auth/reset-password") ||
                path.startsWith("/api/auth/oauth2/") ||
                path.startsWith("/api/auth/login/oauth2/") ||
                path.startsWith("/login/oauth2/") ||
                path.equals("/api/storage/download-editing") ||
                path.equals("/api/storage/callback") ||
                path.startsWith("/actuator/health") ||
                path.equals("/ping");
    }

    /** Endpoints meant only for calls between backend services. */
    private boolean isInternalOnly(String path) {
        return path.equals("/api/storage/upload-raw") ||
                path.equals("/api/storage/delete-raw") ||
                path.equals("/api/storage/download-internal") ||
                (path.startsWith("/api/storage/") && path.endsWith(".pdf")) ||
                path.equals("/api/documents/ownership") ||
                path.endsWith("/status-silent");
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
