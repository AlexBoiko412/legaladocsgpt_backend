package com.legaldocsgpt.shared.repository;

import com.legaldocsgpt.shared.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
    List<DocumentVersion> findByJobIdAndUserIdOrderByVersionDesc(String jobId, String userId);
}