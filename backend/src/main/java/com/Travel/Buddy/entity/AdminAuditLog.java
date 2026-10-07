package com.Travel.Buddy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * One immutable entry in the admin audit trail. (FR-30)
 *
 * <p>Append-only by contract and by construction: every field is
 * final, there is no setter, and there is no "updated at". An
 * audit trail that can be rewritten is a rumour -- money moves,
 * accounts are suspended and listings are pulled on the strength
 * of these rows, so what was recorded must be what happened.
 *
 * <p>The action is a stable machine string ({@code USER_SUSPENDED},
 * {@code PROPERTY_DECIDED}, ...) rather than prose, because the
 * console filters on it and prose drifts with whoever wrote it.
 */
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    /**
     * Who did it. Lazy, so listing the log does not load a user
     * graph per row; the response joins names within the owning
     * transaction.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private User actor;

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    /**
     * Deliberately a plain id, not a foreign key: audit entries
     * must outlive the rows they point at. A deleted destination
     * still deserves a record of who deleted it.
     */
    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "detail", length = 1000)
    private String detail;

    /**
     * Where the action came from. Nullable: an entry written
     * from a background thread has no request to take one from,
     * and a missing IP must never block recording the action.
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AdminAuditLog() {
    }

    public AdminAuditLog(
            User actor,
            String action,
            String entityType,
            Long entityId,
            String detail,
            String ipAddress
    ) {
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.detail = detail;
        this.ipAddress = ipAddress;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getAuditId() {
        return auditId;
    }

    public User getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getDetail() {
        return detail;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}