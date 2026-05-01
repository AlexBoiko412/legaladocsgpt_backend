package com.legaldocsgpt.documentworker.service;

import com.legaldocsgpt.documentworker.TestDocumentWorkerConfig;
import com.legaldocsgpt.shared.entity.DocumentJob;
import com.legaldocsgpt.shared.entity.DocumentVersion;
import com.legaldocsgpt.shared.entity.JobStatus;
import com.legaldocsgpt.shared.exception.EntityNotFoundException;
import com.legaldocsgpt.shared.repository.DocumentJobRepository;
import com.legaldocsgpt.shared.repository.DocumentVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DocumentJobInternalService.class)
@ContextConfiguration(classes = TestDocumentWorkerConfig.class)
@DisplayName("DocumentJobInternalService")
class DocumentJobInternalServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
    @Autowired DocumentJobInternalService service;
    @Autowired DocumentJobRepository jobRepository;
    @Autowired DocumentVersionRepository versionRepository;

    private DocumentJob savedJob;

    @BeforeEach
    void setUp() {
        versionRepository.deleteAll();
        jobRepository.deleteAll();

        savedJob = jobRepository.save(DocumentJob.builder()
                .jobId("job-123")
                .userId("user-456")
                .title("Test Contract")
                .status(JobStatus.IN_PROGRESS)
                .version(1)
                .generatedContent("Original content")
                .docxUrl("job-123.docx")
                .pdfUrl("job-123.pdf")
                .build());
    }

    // ── completeJob ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("completeJob")
    class CompleteJobTests {

        @Test
        void shouldSetStatusToCompleted() {
            service.completeJob("job-123", "user-456",
                    "new.docx", "new.pdf",
                    "New content", null, "versions/job-123/v1.docx");

            DocumentJob job = jobRepository.findByJobIdAndUserId("job-123", "user-456").orElseThrow();
            assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        }

        @Test
        void shouldUpdateDocxAndPdfUrls() {
            service.completeJob("job-123", "user-456",
                    "updated.docx", "updated.pdf",
                    "New content", null, "versions/job-123/v1.docx");

            DocumentJob job = jobRepository.findByJobIdAndUserId("job-123", "user-456").orElseThrow();
            assertThat(job.getDocxUrl()).isEqualTo("updated.docx");
            assertThat(job.getPdfUrl()).isEqualTo("updated.pdf");
        }

        @Test
        void shouldIncrementVersionAfterCompletion() {
            // version starts at 1
            service.completeJob("job-123", "user-456",
                    "new.docx", "new.pdf",
                    "New content", null, "versions/job-123/v1.docx");

            DocumentJob job = jobRepository.findByJobIdAndUserId("job-123", "user-456").orElseThrow();
            assertThat(job.getVersion()).isEqualTo(2);
        }

        @Test
        void shouldCreateVersionSnapshotOnInitialCompletion() {
            // version == 1 means initial generation
            service.completeJob("job-123", "user-456",
                    "new.docx", "new.pdf",
                    "Generated content", null, "versions/job-123/v1.docx");

            List<DocumentVersion> versions = versionRepository
                    .findByJobIdAndUserIdOrderByVersionDesc("job-123", "user-456");

            assertThat(versions).hasSize(1);
            DocumentVersion v = versions.get(0);
            assertThat(v.getSource()).isEqualTo("INITIAL");
            assertThat(v.getVersion()).isEqualTo(1);
            assertThat(v.getDocxKey()).isEqualTo("versions/job-123/v1.docx");
            assertThat(v.getRefinementPrompt()).isNull();
        }

        @Test
        void shouldCreateRefinementSnapshotWithCorrectSourceAndPrompt() {
            // First complete (initial) - version becomes 2
            service.completeJob("job-123", "user-456",
                    "v1.docx", "v1.pdf",
                    "V1 content", null, "versions/job-123/v1.docx");

            // Second complete (refinement) - version is now 2
            service.completeJob("job-123", "user-456",
                    "v2.docx", "v2.pdf",
                    "V2 content", "Make it shorter", "versions/job-123/v2.docx");

            List<DocumentVersion> versions = versionRepository
                    .findByJobIdAndUserIdOrderByVersionDesc("job-123", "user-456");

            assertThat(versions).hasSize(2);

            // Most recent version (v2) is a refinement
            DocumentVersion refinement = versions.get(0);
            assertThat(refinement.getSource()).isEqualTo("REFINEMENT");
            assertThat(refinement.getRefinementPrompt()).isEqualTo("Make it shorter");
            assertThat(refinement.getVersion()).isEqualTo(2);

            // First version is the initial
            DocumentVersion initial = versions.get(1);
            assertThat(initial.getSource()).isEqualTo("INITIAL");
            assertThat(initial.getVersion()).isEqualTo(1);
        }

        @Test
        void shouldNotCreateVersionWhenContentIsNull() {
            service.completeJob("job-123", "user-456",
                    "new.docx", "new.pdf",
                    null, null, null);

            List<DocumentVersion> versions = versionRepository
                    .findByJobIdAndUserIdOrderByVersionDesc("job-123", "user-456");

            assertThat(versions).isEmpty();
        }

        @Test
        void shouldThrowWhenJobNotFound() {
            assertThatThrownBy(() ->
                    service.completeJob("nonexistent", "user-456",
                            "new.docx", "new.pdf",
                            "content", null, "snapshot.docx"))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        void shouldThrowWhenUserDoesNotOwnJob() {
            assertThatThrownBy(() ->
                    service.completeJob("job-123", "wrong-user",
                            "new.docx", "new.pdf",
                            "content", null, "snapshot.docx"))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ── completeJobRestore ────────────────────────────────────────────────────

    @Nested
    @DisplayName("completeJobRestore")
    class CompleteJobRestoreTests {

        @BeforeEach
        void advanceToVersion2() {
            // Get the job to version 2 before testing restores
            service.completeJob("job-123", "user-456",
                    "v1.docx", "v1.pdf",
                    "V1 content", null, "versions/job-123/v1.docx");
        }

        @Test
        void shouldRestoreContentAndSetStatusCompleted() {
            service.completeJobRestore("job-123", "user-456",
                    "restored.docx", "restored.pdf",
                    "Restored content", "versions/job-123/pre_restore.docx");

            DocumentJob job = jobRepository.findByJobIdAndUserId("job-123", "user-456").orElseThrow();
            assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
            assertThat(job.getGeneratedContent()).isEqualTo("Restored content");
        }

        @Test
        void shouldIncrementVersionOnRestore() {
            int versionBefore = jobRepository
                    .findByJobIdAndUserId("job-123", "user-456")
                    .orElseThrow().getVersion();

            service.completeJobRestore("job-123", "user-456",
                    "restored.docx", "restored.pdf",
                    "Restored content", "versions/job-123/pre_restore.docx");

            int versionAfter = jobRepository
                    .findByJobIdAndUserId("job-123", "user-456")
                    .orElseThrow().getVersion();

            assertThat(versionAfter).isEqualTo(versionBefore + 1);
        }

        @Test
        void shouldSnapshotCurrentContentBeforeOverwriting() {
            // Current content before restore
            String contentBeforeRestore = jobRepository
                    .findByJobIdAndUserId("job-123", "user-456")
                    .orElseThrow().getGeneratedContent();

            service.completeJobRestore("job-123", "user-456",
                    "restored.docx", "restored.pdf",
                    "Restored content", "versions/job-123/pre_restore.docx");

            List<DocumentVersion> versions = versionRepository
                    .findByJobIdAndUserIdOrderByVersionDesc("job-123", "user-456");

            // Should have: INITIAL (v1) + pre-restore snapshot (v2)
            assertThat(versions).hasSize(2);

            // Most recent is the pre-restore snapshot
            DocumentVersion preRestoreSnapshot = versions.get(0);
            assertThat(preRestoreSnapshot.getSource()).isEqualTo("REFINEMENT");
            assertThat(preRestoreSnapshot.getRefinementPrompt()).isEqualTo("(before restore)");
            assertThat(preRestoreSnapshot.getContent()).isEqualTo(contentBeforeRestore);
        }

        @Test
        void shouldThrowWhenJobNotFound() {
            assertThatThrownBy(() ->
                    service.completeJobRestore("nonexistent", "user-456",
                            "r.docx", "r.pdf",
                            "Restored", "snapshot.docx"))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ── failJob ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("failJob")
    class FailJobTests {

        @Test
        void shouldSetStatusToFailed() {
            service.failJob("job-123", "user-456", "AI service timeout");

            DocumentJob job = jobRepository.findByJobIdAndUserId("job-123", "user-456").orElseThrow();
            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
            assertThat(job.getErrorDetails()).isEqualTo("AI service timeout");
        }

        @Test
        void shouldTruncateLongErrorMessage() {
            String longError = "x".repeat(300);

            service.failJob("job-123", "user-456", longError);

            DocumentJob job = jobRepository.findByJobIdAndUserId("job-123", "user-456").orElseThrow();
            assertThat(job.getErrorDetails()).hasSizeLessThanOrEqualTo(250);
            assertThat(job.getErrorDetails()).endsWith("...");
        }

        @Test
        void shouldSilentlyIgnoreWhenJobNotFound() {
            // failJob uses ifPresent - should not throw
            assertThatCode(() ->
                    service.failJob("nonexistent", "user-456", "error"))
                    .doesNotThrowAnyException();
        }
    }
}