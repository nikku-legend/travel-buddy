package com.Travel.Buddy.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Admin-configurable catalogue of which KYC documents each partner type
 * must submit, so the requirement set can change without a code deploy.
 */
@Entity
@Table(
        name = "kyc_document_types",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_kyc_document_type",
                        columnNames = {"partner_type", "document_code"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_kyc_document_types_partner_type",
                        columnList = "partner_type"
                )
        }
)
public class KycDocumentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_type_id")
    private Long documentTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_type", nullable = false)
    private PartnerType partnerType;

    @Column(name = "document_code", nullable = false, length = 50)
    private String documentCode;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "is_mandatory", nullable = false)
    private Boolean mandatory;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (mandatory == null) {
            mandatory = Boolean.TRUE;
        }

        if (displayOrder == null) {
            displayOrder = 0;
        }
    }

    public Long getDocumentTypeId() {
        return documentTypeId;
    }

    public void setDocumentTypeId(Long documentTypeId) {
        this.documentTypeId = documentTypeId;
    }

    public PartnerType getPartnerType() {
        return partnerType;
    }

    public void setPartnerType(PartnerType partnerType) {
        this.partnerType = partnerType;
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

    public Boolean getMandatory() {
        return mandatory;
    }

    public void setMandatory(Boolean mandatory) {
        this.mandatory = mandatory;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
