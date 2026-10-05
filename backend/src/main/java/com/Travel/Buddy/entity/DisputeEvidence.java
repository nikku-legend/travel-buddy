package com.Travel.Buddy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * A file attached to a dispute. (FR-28)
 */
@Entity
@Table(
        name = "dispute_evidence",
        indexes = {
                @Index(
                        name = "idx_dispute_evidence_dispute",
                        columnList = "dispute_id"
                )
        }
)
public class DisputeEvidence {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long evidenceId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "dispute_id",
            nullable = false
    )
    private Dispute dispute;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "uploaded_by_user_id",
            nullable = false
    )
    private User uploadedBy;

    @Column(length = 200)
    private String description;

    /**
     * Server-side path under a random filename. Never derived from
     * user input, so a crafted name cannot escape the directory
     * or overwrite another claimant's file.
     */
    @Column(
            name = "stored_path",
            nullable = false,
            length = 400
    )
    private String storedPath;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    protected DisputeEvidence() {
    }

    public DisputeEvidence(
            Dispute dispute,
            User uploadedBy,
            String storedPath,
            String originalFilename,
            String contentType,
            Long fileSizeBytes,
            String description
    ) {
        this.dispute = dispute;
        this.uploadedBy = uploadedBy;
        this.storedPath = storedPath;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.description = description;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now()
                .truncatedTo(ChronoUnit.MICROS);
    }

    public Long getEvidenceId() {
        return evidenceId;
    }

    public Dispute getDispute() {
        return dispute;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public String getDescription() {
        return description;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}