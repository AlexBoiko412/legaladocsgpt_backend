package com.legaldocsgpt.shared.repository;

import com.legaldocsgpt.shared.entity.DocumentJob;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentJobRepository extends JpaRepository<DocumentJob, Long> {
    Optional<DocumentJob> findByJobId(String jobId);
    List<DocumentJob> findAllByOrderByLastEditedAtDesc();

    List<DocumentJob> findByTitleContainingIgnoreCaseOrJobIdContainingIgnoreCaseOrderByLastEditedAtDesc(
            String title, String jobId
    );

    @Transactional
    void deleteByJobId(String jobId);
}
