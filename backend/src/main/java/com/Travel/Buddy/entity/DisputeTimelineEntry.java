package com.Travel.Buddy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One immutable entry on a dispute's audit trail. (FR-28)
 *
 * <p>No setters and no status columns that can be edited after
 * the fact. This is the record that answers "why was this money
 * paid out", so it is written once and left alone.
 */
@Entity
@Table(
        name = "dispute_timeline",
        indexes = {
                @Index(
                        name = "idx_dispute_timeline_dispute",
                        columnList = "dispute_id, created_at"
                )
        }
)
public class DisputeTimelineEntry {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long timelineId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "dispute_id",
            nullable = false
    )
    private Dispute dispute;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "event_type",
            nullable = false
    )
    private DisputeEventType eventType;

    /**
     * Nullable on purpose. An automated entry has no human actor,
     * and an erased account must not erase the trail, so this
     * column is allowed to point at nothing.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", length = 30)
    private String toStatus;

    @Column(length = 1000)
    private String note;

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    protected DisputeTimelineEntry() {
    }

    public DisputeTimelineEntry(
            Dispute dispute,
            DisputeEventType eventType,
            User actor,
            DisputeStatus fromStatus,
            DisputeStatus toStatus,
            String note
    ) {
        this.dispute = dispute;
        this.eventType = eventType;
        this.actor = actor;
        this.fromStatus = fromStatus == null
                ? null
                : fromStatus.name();
        this.toStatus = toStatus == null
                ? null
                : toStatus.name();
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now()
                .truncatedTo(ChronoUnit.MICROS);
    }

    public Long getTimelineId() {
        return timelineId;
    }

    public Dispute getDispute() {
        return dispute;
    }

    public DisputeEventType getEventType() {
        return eventType;
    }

    public User getActor() {
        return actor;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}