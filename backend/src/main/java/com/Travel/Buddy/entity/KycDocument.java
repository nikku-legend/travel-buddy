package com.Travel.Buddy.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A single KYC document belonging to a partner application.
 *
 * <p>Documents are verified independently, so an admin can approve the
 * identity proof and reject a blurry licence without discarding the rest
 * of the application.
 */
@Entity
@Table(
        name = "kyc_documents",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_kyc_document_application_code",
                        columnNames = {"application_id", "document_code"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_kyc_documents_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_kyc_documents_application",
                        columnList = "application_id"
                )
        }
)
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long documentId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "application_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_kyc_documents_application"
            )
    )
    private PartnerApplication application;

    /**
     * Stable code from {@code kyc_document_types}, e.g. {@code IDENTITY_PROOF}.
     * Stored as text because the catalogue is admin-configurable.
     */
    @Column(name = "document_code", nullable = false, length = 50)
    private String documentCode;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private KycDocumentStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "verified_by_user_id",
            foreignKey = @ForeignKey(
                    name = "fk_kyc_documents_verified_by"
            )
    )
    private User verifiedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;

        if (status == null) {
            status = KycDocumentStatus.NOT_UPLOADED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public PartnerApplication getApplication() {
        return application;
    }

    public void setApplication(PartnerApplication application) {
        this.application = application;
    }

    public String getDocumentCode() {
        return documentCode;
    }

    public void setDocumentCode(String documentCode) {
        this.documentCode = documentCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public KycDocumentStatus getStatus() {
        return status;
    }

    public void setStatus(KycDocumentStatus status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public User getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(User verifiedBy) {
        this.verifiedBy = verifiedBy;
    }
}
