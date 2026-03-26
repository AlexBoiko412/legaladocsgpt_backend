package com.legaldocsgpt.documentworker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class WordToPdfService {

    private final WebClient webClient;

    public WordToPdfService(@Value("${gotenberg.url}") String gotenbergUrl,
                            WebClient.Builder webClientBuilder) {

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        this.webClient = webClientBuilder
                .baseUrl(gotenbergUrl)
                .exchangeStrategies(strategies)
                .build();
    }

    /**
     * Converts a .docx file to .pdf using Gotenberg's LibreOffice engine.
     *
     * @param docxBytes Raw bytes of the Word document
     * @param fileName  The name of the file (e.g., "contract.docx")
     * @return Raw bytes of the converted PDF
     */
    public byte[] convertToPdf(byte[] docxBytes, String fileName) {
        log.info("Initiating PDF conversion for file: {}", fileName);

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("files", new ByteArrayResource(docxBytes))
                .filename(fileName);

        try {
            return webClient.post()
                    .uri("/forms/libreoffice/convert")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("Gotenberg API error: {}", errorBody);
                                        return Mono.error(new RuntimeException("Gotenberg conversion failed: " + errorBody));
                                    })
                    )
                    .bodyToMono(byte[].class)
                    .block();
        } catch (Exception e) {
            log.error("Unexpected error during PDF conversion for {}: {}", fileName, e.getMessage());
            throw new RuntimeException("Failed to convert document to PDF", e);
        }
    }
}