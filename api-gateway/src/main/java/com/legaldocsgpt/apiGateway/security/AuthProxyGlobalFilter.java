package com.legaldocsgpt.apiGateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AuthProxyGlobalFilter implements GlobalFilter, Ordered {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private final Map<String, Boolean> tokenCache = new ConcurrentHashMap<>();

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

                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(r -> r.header("X-User-Id", String.valueOf(user.getId())))
                            .build();

                    return chain.filter(mutatedExchange);
                })
                .onErrorResume(err -> {
                    log.error("Error validating token: {}", err.getMessage());
                    return handleUnauthorized(exchange, GlobalErrorCode.INVALID_TOKEN);
                });
    }

    private boolean isWhitelisted(String path) {
        return path.contains("/api/auth/login") ||
                path.contains("/api/auth/signup") ||
                path.contains("/api/auth/oauth2/");
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
}
