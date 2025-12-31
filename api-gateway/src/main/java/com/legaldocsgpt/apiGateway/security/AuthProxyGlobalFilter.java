package com.legaldocsgpt.apiGateway.security;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class AuthProxyGlobalFilter implements GlobalFilter, Ordered {

    private final WebClient webClient;

    @Autowired
    public AuthProxyGlobalFilter(WebClient.Builder builder) {
        this.webClient = builder.baseUrl("http://auth-service:8081").build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (path.startsWith("/api/auth/oauth2/")
                || path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/logout")
                || path.startsWith("/api/auth/signup")
        ) {
            return chain.filter(exchange);
        }

        var cookie = exchange.getRequest().getCookies().getFirst("token");
        if (cookie == null || cookie.getValue().isEmpty()) {
            return handleUnauthorized(exchange, "Missing or empty authentication token");
        }

        String token = cookie.getValue();



        return webClient.get()
                .uri("/validate")
                .header(HttpHeaders.COOKIE, "token=" + token)
                .retrieve()
                .toBodilessEntity()
                .flatMap(resp -> {
                    if (resp.getStatusCode().is2xxSuccessful()) {
                        return chain.filter(exchange);
                    } else {
                        return handleUnauthorized(exchange, "Invalid session or token expired");
                    }
                })
                .onErrorResume(err -> {
                    log.error("Error validating token", err);
                    return handleUnauthorized(exchange, "Invalid session or token expired");
                });
    }

    @Override
    public int getOrder() {
        return -1;
    }



    private final ObjectMapper objectMapper = new ObjectMapper();

    private Mono<Void> handleUnauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");

        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("timestamp", java.time.LocalDateTime.now().toString());
        errorDetails.put("status", HttpStatus.UNAUTHORIZED.value());
        errorDetails.put("error", "Unauthorized");
        errorDetails.put("message", message);
        errorDetails.put("path", exchange.getRequest().getPath().value());

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorDetails);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return exchange.getResponse().setComplete();
        }
    }
}


