package com.legaldocsgpt.apiGateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legaldocsgpt.shared.dto.ErrorResponse;
import com.legaldocsgpt.shared.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        GlobalErrorCode errorCode = GlobalErrorCode.INTERNAL_ERROR;

        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND) errorCode = GlobalErrorCode.NOT_FOUND;
        }

        ErrorResponse errorBody = ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(ex.getMessage())
                .path(exchange.getRequest().getPath().value())
                .timestamp(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString().substring(0, 8))
                .build();

        log.error("Gateway Error: {} | Path: {} | TraceId: {}",
                ex.getMessage(), errorBody.path(), errorBody.traceId());

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        return response.writeWith(Mono.fromSupplier(() -> {
            DataBuffer buffer = response.bufferFactory().allocateBuffer();
            try {
                buffer.write(objectMapper.writeValueAsBytes(errorBody));
            } catch (JsonProcessingException e) {
                log.error("Error writing error response", e);
            }
            return buffer;
        }));
    }
}