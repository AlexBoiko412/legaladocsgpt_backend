package com.legaldocsgpt.apiGateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthProxyGlobalFilter")
class AuthProxyGlobalFilterTest {

    private final AuthProxyGlobalFilter filter =
            new AuthProxyGlobalFilter(WebClient.builder(), new ObjectMapper().findAndRegisterModules());

    /** Chain that records the exchange it was called with. */
    private static class RecordingChain implements GatewayFilterChain {
        final AtomicReference<ServerWebExchange> seen = new AtomicReference<>();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            seen.set(exchange);
            return Mono.empty();
        }
    }

    @Test
    void internalStorageEndpointsAreNotReachableFromOutside() {
        for (String path : new String[]{
                "/api/storage/upload-raw", "/api/storage/delete-raw",
                "/api/storage/download-internal", "/api/storage/some-job.pdf",
                "/api/documents/ownership"}) {
            RecordingChain chain = new RecordingChain();
            MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.post(path));

            filter.filter(ex, chain).block();

            assertThat(ex.getResponse().getStatusCode()).as(path).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(chain.seen.get()).as(path).isNull();
        }
    }

    @Test
    void whitelistedPathPassesThroughWithForgedIdentityHeadersRemoved() {
        RecordingChain chain = new RecordingChain();
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ROLE_ADMIN"));

        filter.filter(ex, chain).block();

        assertThat(chain.seen.get()).isNotNull();
        assertThat(chain.seen.get().getRequest().getHeaders().containsKey("X-User-Id")).isFalse();
        assertThat(chain.seen.get().getRequest().getHeaders().containsKey("X-User-Role")).isFalse();
    }

    @Test
    void protectedPathWithoutCookieIsUnauthorized() {
        RecordingChain chain = new RecordingChain();
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get("/api/documents"));

        filter.filter(ex, chain).block();

        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chain.seen.get()).isNull();
    }

    @Test
    void whitelistMatchIsExactAndCannotBeSmuggledInsideAnotherPath() {
        RecordingChain chain = new RecordingChain();
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/documents/x/api/auth/login"));

        filter.filter(ex, chain).block();

        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chain.seen.get()).isNull();
    }
}
