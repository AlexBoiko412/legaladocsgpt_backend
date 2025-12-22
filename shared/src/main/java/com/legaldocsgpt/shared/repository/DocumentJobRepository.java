package com.legaldocsgpt.shared.repository;

import com.legaldocsgpt.shared.entity.DocumentJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentJobRepository extends JpaRepository<DocumentJob, Long> {
    Optional<DocumentJob> findByJobId(String jobId);
}
