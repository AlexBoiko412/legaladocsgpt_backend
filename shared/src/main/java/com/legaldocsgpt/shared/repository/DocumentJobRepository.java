package com.legaldocsgpt.shared.repository;

import com.legaldocsgpt.shared.entity.DocumentJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentJobRepository extends JpaRepository<DocumentJob, Long> {

    Optional<DocumentJob> findByJobId(String jobId);

    Optional<DocumentJob> findByJobIdAndUserId(String jobId, String userId);

    List<DocumentJob> findAllByUserIdOrderByLastEditedAtDesc(String userId);

    List<DocumentJob> findByUserIdAndTitleContainingIgnoreCaseOrderByLastEditedAtDesc(String userId, String title);

    void deleteByJobIdAndUserId(String jobId, String userId);
}