package com.legaldocsgpt.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legaldocsgpt.shared.dto.ErrorResponse;
import com.legaldocsgpt.shared.exception.BaseBusinessException;
import com.legaldocsgpt.shared.exception.EntityNotFoundException;
import com.legaldocsgpt.shared.exception.GlobalErrorCode;
import com.legaldocsgpt.shared.exception.UnauthorizedException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import java.io.InputStream;

@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public Exception decode(String methodKey, Response response) {
        try (InputStream body = response.body().asInputStream()) {
            ErrorResponse error = objectMapper.readValue(body, ErrorResponse.class);

            return switch (response.status()) {
                case 400 -> new BaseBusinessException(GlobalErrorCode.INVALID_INPUT, error.message()) {};
                case 401 -> new UnauthorizedException(error.message());
                case 404 -> new EntityNotFoundException(error.message());
                default -> new Exception(error.message());
            };
        } catch (Exception e) {
            log.error("Failed to decode Feign error from {}", methodKey);
            return new Exception("Generic inter-service communication error");
        }
    }
}