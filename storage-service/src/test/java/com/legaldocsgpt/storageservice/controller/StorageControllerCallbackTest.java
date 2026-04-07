package com.legaldocsgpt.storageservice.controller;

import com.legaldocsgpt.shared.dto.EditTokenClaims;
import com.legaldocsgpt.shared.exception.UnauthorizedException;
import com.legaldocsgpt.shared.services.EditTokenService;
import com.legaldocsgpt.storageservice.service.S3StorageService;
import com.legaldocsgpt.shared.client.DocumentJobClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = StorageController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class
        }
)
@TestPropertySource(properties = {
        "aws.s3.endpoint=http://localhost:9999",
        "aws.s3.access-key=test",
        "aws.s3.secret-key=test",
        "aws.s3.region=us-east-1",
        "aws.s3.bucket-name=test-bucket",
        "edit.token.secret=test-secret-that-is-long-enough-for-hmac"
})
@DisplayName("StorageController - OnlyOffice callback")
class StorageControllerCallbackTest {

    @MockitoBean
    software.amazon.awssdk.services.s3.S3Client s3Client;

    @Autowired MockMvc mockMvc;

    @MockitoBean S3StorageService storageService;
    @MockitoBean EditTokenService editTokenService;
    @MockitoBean DocumentJobClient documentJobClient;

    private static final String VALID_TOKEN = "valid-token";
    private static final String JOB_ID = "test-job-123";

    @Nested
    @DisplayName("POST /callback")
    class CallbackTests {

        @Test
        void shouldReturn403ForInvalidToken() throws Exception {
            when(editTokenService.verify(VALID_TOKEN))
                    .thenThrow(new UnauthorizedException("Invalid token"));

            mockMvc.perform(post("/callback")
                            .param("token", VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": 2, \"url\": \"http://onlyoffice/file.docx\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldAckWithErrorZeroForStatus1() throws Exception {
            when(editTokenService.verify(VALID_TOKEN))
                    .thenReturn(new EditTokenClaims(JOB_ID, "user-1"));

            mockMvc.perform(post("/callback")
                            .param("token", VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": 1}"))
                    .andExpect(status().isOk())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").value(0));

            // Status 1 = being edited - no file save should happen
            verify(storageService, never()).uploadFile(any(), any(), any());
        }

        @Test
        void shouldAckWithErrorZeroForStatus3() throws Exception {
            when(editTokenService.verify(VALID_TOKEN))
                    .thenReturn(new EditTokenClaims(JOB_ID, "user-1"));

            mockMvc.perform(post("/callback")
                            .param("token", VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": 3}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").value(0));

            // Status 3 = closed without changes - no save
            verify(storageService, never()).uploadFile(any(), any(), any());
        }

        @Test
        void shouldAckWithErrorZeroWhenStatus2HasNoUrl() throws Exception {
            when(editTokenService.verify(VALID_TOKEN))
                    .thenReturn(new EditTokenClaims(JOB_ID, "user-1"));

            // Status 2 but no url - should not crash, just log warning
            mockMvc.perform(post("/callback")
                            .param("token", VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": 2}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").value(0));

            verify(storageService, never()).uploadFile(any(), any(), any());
        }

        @Test
        void shouldAlwaysReturnErrorZeroForUnknownStatus() throws Exception {
            when(editTokenService.verify(VALID_TOKEN))
                    .thenReturn(new EditTokenClaims(JOB_ID, "user-1"));

            mockMvc.perform(post("/callback")
                            .param("token", VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": 99}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").value(0));
        }
    }
}