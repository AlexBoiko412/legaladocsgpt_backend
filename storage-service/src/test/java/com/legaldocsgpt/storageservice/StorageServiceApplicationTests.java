package com.legaldocsgpt.storageservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@TestPropertySource(properties = {
        "aws.s3.endpoint=http://localhost:9999",
        "aws.s3.access-key=test",
        "aws.s3.secret-key=test",
        "aws.s3.region=us-east-1",
        "aws.s3.bucket-name=test-bucket",
        "edit.token.secret=test-secret-that-is-long-enough-for-hmac"
})
class StorageServiceApplicationTests {

    @MockitoBean
    software.amazon.awssdk.services.s3.S3Client s3Client;

    @Test
    void contextLoads() {
    }
}