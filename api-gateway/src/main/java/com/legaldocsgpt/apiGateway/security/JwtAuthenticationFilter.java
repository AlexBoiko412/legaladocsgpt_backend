package com.legaldocsgpt.apiGateway.security;


import com.legaldocsgpt.apiGateway.dto.UserInfoResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.List;

@Component
public class JwtAuthenticationFilter implements WebFilter {

    private final WebClient webClient;

    public JwtAuthenticationFilter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("http://auth-service:8081").build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = exchange.getRequest().getQueryParams().getFirst("token");

        if (token == null) {
            return chain.filter(exchange);
        }

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/validate")
                        .queryParam("token", token)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException(body)))
                )
                .bodyToMono(UserInfoResponse.class)
                .flatMap(userInfo -> {
                    var auth = new UsernamePasswordAuthenticationToken(
                            userInfo.getUsername(),
                            null,
                            List.of(() -> userInfo.getRole())
                    );
                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                                    Mono.just(new SecurityContextImpl(auth))
                            ));
                })
                .onErrorResume(e -> {
                    exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                });
    }
}
