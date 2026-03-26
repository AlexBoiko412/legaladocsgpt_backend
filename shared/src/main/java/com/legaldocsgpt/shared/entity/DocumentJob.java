package com.legaldocsgpt.shared.entity;

import com.legaldocsgpt.shared.services.MapToJsonConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String jobId;
    private String title;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    @Builder.Default
    private int version = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
    private LocalDateTime lastEditedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastEditedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastEditedAt = LocalDateTime.now();
    }



    @Column(columnDefinition = "TEXT")
    private String generatedContent;

    private String docxUrl;
    private String pdfUrl;

    private String errorDetails;

    @Column(name = "template_path")
    private String templatePath;

    @Column(name = "document_data", columnDefinition = "TEXT")
    @Convert(converter = MapToJsonConverter.class)
    private Map<String, String> documentData;


}
