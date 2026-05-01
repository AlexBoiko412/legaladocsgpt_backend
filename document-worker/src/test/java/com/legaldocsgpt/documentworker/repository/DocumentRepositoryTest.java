package com.legaldocsgpt.documentworker.repository;

import com.legaldocsgpt.shared.entity.DocumentJob;
import com.legaldocsgpt.shared.entity.DocumentVersion;
import com.legaldocsgpt.shared.entity.JobStatus;
import com.legaldocsgpt.shared.repository.DocumentJobRepository;
import com.legaldocsgpt.shared.repository.DocumentVersionRepository;
import com.legaldocsgpt.documentworker.TestDocumentWorkerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TestDocumentWorkerConfig.class)
@DisplayName("Repository queries")
class DocumentRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired DocumentJobRepository jobRepository;
    @Autowired DocumentVersionRepository versionRepository;

    @BeforeEach
    void setUp() {
        versionRepository.deleteAll();
        jobRepository.deleteAll();
    }

    // ── DocumentJobRepository ─────────────────────────────────────────────────

    @Nested
    @DisplayName("DocumentJobRepository")
    class DocumentJobRepositoryTests {

        @Test
        void shouldFindByJobId() {
            jobRepository.save(buildJob("job-1", "user-1", "Contract"));

            Optional<DocumentJob> found = jobRepository.findByJobId("job-1");

            assertThat(found).isPresent();
            assertThat(found.get().getTitle()).isEqualTo("Contract");
        }

        @Test
        void shouldReturnEmptyWhenJobIdNotFound() {
            assertThat(jobRepository.findByJobId("nonexistent")).isEmpty();
        }

        @Test
        void shouldFindByJobIdAndUserId() {
            jobRepository.save(buildJob("job-1", "user-1", "Contract"));

            assertThat(jobRepository.findByJobIdAndUserId("job-1", "user-1")).isPresent();
            assertThat(jobRepository.findByJobIdAndUserId("job-1", "wrong-user")).isEmpty();
        }

        @Test
        void shouldFindAllByUserIdOrderedByLastEditedAtDesc() throws InterruptedException {
            jobRepository.save(buildJob("job-1", "user-1", "First"));
            Thread.sleep(10); // ensure different timestamps
            jobRepository.save(buildJob("job-2", "user-1", "Second"));
            jobRepository.save(buildJob("job-3", "user-2", "Other user"));

            List<DocumentJob> results = jobRepository
                    .findAllByUserIdOrderByLastEditedAtDesc("user-1");

            assertThat(results).hasSize(2);
            // Most recently created should be first
            assertThat(results.get(0).getTitle()).isEqualTo("Second");
            assertThat(results.get(1).getTitle()).isEqualTo("First");
        }

        @Test
        void shouldNotReturnOtherUsersDocuments() {
            jobRepository.save(buildJob("job-1", "user-1", "Mine"));
            jobRepository.save(buildJob("job-2", "user-2", "Theirs"));

            List<DocumentJob> results = jobRepository
                    .findAllByUserIdOrderByLastEditedAtDesc("user-1");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getTitle()).isEqualTo("Mine");
        }

        @Test
        void shouldSearchByTitleCaseInsensitive() {
            jobRepository.save(buildJob("job-1", "user-1", "Employment Contract"));
            jobRepository.save(buildJob("job-2", "user-1", "NDA Agreement"));
            jobRepository.save(buildJob("job-3", "user-1", "Service contract"));

            List<DocumentJob> results = jobRepository
                    .findByUserIdAndTitleContainingIgnoreCaseOrderByLastEditedAtDesc(
                            "user-1", "contract");

            assertThat(results).hasSize(2);
            assertThat(results).extracting(DocumentJob::getTitle)
                    .containsExactlyInAnyOrder("Employment Contract", "Service contract");
        }

        @Test
        void shouldReturnEmptyWhenSearchMatchesNothing() {
            jobRepository.save(buildJob("job-1", "user-1", "Employment Contract"));

            List<DocumentJob> results = jobRepository
                    .findByUserIdAndTitleContainingIgnoreCaseOrderByLastEditedAtDesc(
                            "user-1", "invoice");

            assertThat(results).isEmpty();
        }

        @Test
        void shouldNotReturnOtherUsersResultsInSearch() {
            jobRepository.save(buildJob("job-1", "user-1", "Contract"));
            jobRepository.save(buildJob("job-2", "user-2", "Contract"));

            List<DocumentJob> results = jobRepository
                    .findByUserIdAndTitleContainingIgnoreCaseOrderByLastEditedAtDesc(
                            "user-1", "contract");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getUserId()).isEqualTo("user-1");
        }

        @Test
        void shouldDeleteByJobIdAndUserId() {
            jobRepository.save(buildJob("job-1", "user-1", "To delete"));
            jobRepository.save(buildJob("job-2", "user-1", "Keep this"));

            jobRepository.deleteByJobIdAndUserId("job-1", "user-1");

            assertThat(jobRepository.findByJobId("job-1")).isEmpty();
            assertThat(jobRepository.findByJobId("job-2")).isPresent();
        }

        @Test
        void shouldNotDeleteJobOwnedByDifferentUser() {
            jobRepository.save(buildJob("job-1", "user-1", "Contract"));

            jobRepository.deleteByJobIdAndUserId("job-1", "wrong-user");

            // Job should still exist - wrong user can't delete it
            assertThat(jobRepository.findByJobId("job-1")).isPresent();
        }

        @Test
        void shouldCheckExistenceByJobIdAndUserId() {
            jobRepository.save(buildJob("job-1", "user-1", "Contract"));

            assertThat(jobRepository.existsByJobIdAndUserId("job-1", "user-1")).isTrue();
            assertThat(jobRepository.existsByJobIdAndUserId("job-1", "wrong-user")).isFalse();
            assertThat(jobRepository.existsByJobIdAndUserId("nonexistent", "user-1")).isFalse();
        }
    }

    // ── DocumentVersionRepository ─────────────────────────────────────────────

    @Nested
    @DisplayName("DocumentVersionRepository")
    class DocumentVersionRepositoryTests {

        private DocumentJob savedJob;

        @BeforeEach
        void setUpJob() {
            savedJob = jobRepository.save(buildJob("job-1", "user-1", "Contract"));
        }

        @Test
        void shouldReturnVersionsOrderedByVersionDesc() {
            versionRepository.save(buildVersion("job-1", "user-1", 1, "INITIAL"));
            versionRepository.save(buildVersion("job-1", "user-1", 2, "REFINEMENT"));
            versionRepository.save(buildVersion("job-1", "user-1", 3, "REFINEMENT"));

            List<DocumentVersion> versions = versionRepository
                    .findByJobIdAndUserIdOrderByVersionDesc("job-1", "user-1");

            assertThat(versions).hasSize(3);
            assertThat(versions.get(0).getVersion()).isEqualTo(3);
            assertThat(versions.get(1).getVersion()).isEqualTo(2);
            assertThat(versions.get(2).getVersion()).isEqualTo(1);
        }

        @Test
        void shouldReturnEmptyWhenNoVersionsExist() {
            List<DocumentVersion> versions = versionRepository
                    .findByJobIdAndUserIdOrderByVersionDesc("job-1", "user-1");

            assertThat(versions).isEmpty();
        }

        @Test
        void shouldNotReturnVersionsForDifferentJob() {
            versionRepository.save(buildVersion("job-1", "user-1", 1, "INITIAL"));

            List<DocumentVersion> versions = versionRepository
                    .findByJobIdAndUserIdOrderByVersionDesc("job-2", "user-1");

            assertThat(versions).isEmpty();
        }

        @Test
        void shouldNotReturnVersionsForDifferentUser() {
            versionRepository.save(buildVersion("job-1", "user-1", 1, "INITIAL"));

            List<DocumentVersion> versions = versionRepository
                    .findByJobIdAndUserIdOrderByVersionDesc("job-1", "user-2");

            assertThat(versions).isEmpty();
        }

        @Test
        void shouldPreserveSourceAndContentAccurately() {
            versionRepository.save(buildVersion("job-1", "user-1", 1, "INITIAL"));

            DocumentVersion v = versionRepository
                    .findByJobIdAndUserIdOrderByVersionDesc("job-1", "user-1")
                    .get(0);

            assertThat(v.getSource()).isEqualTo("INITIAL");
            assertThat(v.getContent()).isEqualTo("Content for version 1");
            assertThat(v.getDocxKey()).isEqualTo("versions/job-1/v1.docx");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private DocumentJob buildJob(String jobId, String userId, String title) {
        return DocumentJob.builder()
                .jobId(jobId)
                .userId(userId)
                .title(title)
                .status(JobStatus.COMPLETED)
                .version(1)
                .build();
    }

    private DocumentVersion buildVersion(String jobId, String userId,
                                         int version, String source) {
        return DocumentVersion.builder()
                .jobId(jobId)
                .userId(userId)
                .version(version)
                .source(source)
                .content("Content for version " + version)
                .docxKey("versions/" + jobId + "/v" + version + ".docx")
                .build();
    }
}