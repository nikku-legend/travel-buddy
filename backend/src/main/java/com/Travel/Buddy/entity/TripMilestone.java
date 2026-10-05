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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * A checkpoint on the treasure map. (SRS 2.2 TP-11)
 *
 * <p>Section 15 is explicit: the map is driven by these rows
 * rather than hard-coded visual progress. A checkpoint is only
 * {@code completed} once something real happened, so the UI can
 * never animate a stay as finished because the traveller scrolled
 * past it.
 */
@Entity
@Table(
        name = "trip_milestones",
        indexes = {
                @Index(
                        name = "idx_trip_milestones_trip",
                        columnList = "trip_id, progress_percent"
                )
        }
)
public class TripMilestone {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long milestoneId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "trip_id",
            nullable = false
    )
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_city_id")
    private TripCity tripCity;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "milestone_type",
            nullable = false
    )
    private TripMilestoneType milestoneType;

    @Column(
            name = "progress_percent",
            nullable = false
    )
    private int progressPercent;

    @Column(nullable = false, length = 150)
    private String label;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    protected TripMilestone() {
    }

    public TripMilestone(
            Trip trip,
            TripCity tripCity,
            TripMilestoneType milestoneType,
            int progressPercent,
            String label
    ) {
        this.trip = trip;
        this.tripCity = tripCity;
        this.milestoneType = milestoneType;
        this.progressPercent = progressPercent;
        this.label = label;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now()
                .truncatedTo(ChronoUnit.MICROS);
    }

    @PreUpdate
    void onUpdate() {
        if (completed && completedAt == null) {
            this.completedAt = LocalDateTime.now()
                    .truncatedTo(ChronoUnit.MICROS);
        }
    }

    /**
     * Marks the checkpoint reached. Idempotent: the original
     * timestamp is kept, because re-running a completion sweep
     * must not rewrite when the traveller actually arrived.
     */
    public void complete() {
        if (!completed) {
            this.completed = true;
            this.completedAt = LocalDateTime.now()
                    .truncatedTo(ChronoUnit.MICROS);
        }
    }

    public Long getMilestoneId() {
        return milestoneId;
    }

    public Trip getTrip() {
        return trip;
    }

    public TripCity getTripCity() {
        return tripCity;
    }

    public TripMilestoneType getMilestoneType() {
        return milestoneType;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Marks this checkpoint reached.
     *
     * <p>Present because the class had no way to be completed at
     * all: the {@code completed} flag and its {@code @PreUpdate}
     * companion that stamps {@code completedAt} were both written
     * for a transition that no caller could make, so every
     * treasure map was permanently at zero progress.
     *
     * <p>Idempotent. A payment webhook and a booking confirmation
     * can both report the same event, and the second must not move
     * the timestamp.
     */
    public void markComplete() {
        if (completed) {
            return;
        }
        this.completed = true;
        /*
         * Stamped here as well as in onUpdate, so the pair is
         * consistent in memory for a caller that inspects the
         * entity before the flush. The database CHECK constraint
         * requires the two together.
         */
        this.completedAt = LocalDateTime.now()
                .truncatedTo(ChronoUnit.MICROS);
    }
    public boolean isCompleted() {
        return completed;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}