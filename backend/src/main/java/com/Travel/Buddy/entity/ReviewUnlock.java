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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * One reviewable service on one trip. (SRS 2.3 TP-12, section 8)
 *
 * <p>Section 10: "the system evaluates which bookings are eligible
 * for review. Review Center becomes available. Each eligible
 * hotel/guide/transport service receives a review card." This row is
 * that card.
 *
 * <p><b>Eligibility is derived, never granted.</b> A row is only ever
 * created by TripReviewService from a booking that reached COMPLETED
 * or a ride that reached COMPLETED, and never from anything the
 * client sends. The client cannot ask to be given a review card; it
 * can only ask to see the ones that already exist.
 *
 * <p>{@code targetId} is polymorphic for the same reason
 * {@link ReviewTargetType} documents it: one column cannot reference
 * four tables, and four nullable columns would let a row claim to be
 * about a hotel while citing a guide.
 */
@Entity
@Table(
        name = "review_unlocks",
        indexes = {
                @Index(
                        name = "idx_review_unlocks_trip_status",
                        columnList = "trip_id, status"
                ),
                @Index(
                        name = "idx_review_unlocks_target",
                        columnList = "target_type, target_id"
                )
        }
)
public class ReviewUnlock {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long unlockId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "trip_id",
            nullable = false
    )
    private Trip trip;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "target_type",
            nullable = false
    )
    private ReviewTargetType targetType;

    @Column(
            name = "target_id",
            nullable = false
    )
    private Long targetId;

    /**
     * The completed booking that proves entitlement. Null for cabs,
     * which are proven by a completed ride instead -- the same
     * asymmetry ReviewService documents in findEntitlement.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewUnlockStatus status = ReviewUnlockStatus.LOCKED;

    /** Shown to the traveller so the card explains itself. */
    @Column(
            name = "unlock_reason",
            length = 255
    )
    private String unlockReason;

    /**
     * Stay context, snapshotted at creation.
     *
     * <p>Deliberately not re-derived on read. The Review Center is a
     * pure read over one trip, and reconstructing the window would
     * mean three different queries with three different date shapes
     * -- hotel check-in/check-out, a single tour date, a ride pickup
     * time. Frozen for the same reason TripSelection freezes
     * quoted_amount: the card must keep describing the stay that
     * actually happened.
     */
    @Column(name = "check_in")
    private LocalDate checkIn;

    @Column(name = "check_out")
    private LocalDate checkOut;

    @Column(
            name = "city_name",
            length = 120
    )
    private String cityName;

    @Column(name = "eligible_at")
    private LocalDateTime eligibleAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id")
    private Review review;

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private LocalDateTime updatedAt;

    protected ReviewUnlock() {
    }

    public ReviewUnlock(
            Trip trip,
            ReviewTargetType targetType,
            Long targetId,
            Booking booking,
            String unlockReason
    ) {
        this.trip = trip;
        this.targetType = targetType;
        this.targetId = targetId;
        this.booking = booking;
        this.unlockReason = unlockReason;
    }
/**
     * Opens the window now that the service has completed.
     *
     * <p>Stamps {@code eligibleAt} rather than relying on a
     * listener, because the column carries a CHECK constraint that a
     * non-LOCKED status always has one. Doing it here keeps the
     * invariant true for the lifetime of the row.
     */
    public void markEligible() {
        if (this.status != ReviewUnlockStatus.LOCKED) {
            return;
        }

        this.status = ReviewUnlockStatus.ELIGIBLE;
        this.eligibleAt = now();
    }

    /**
     * Links a submitted review.
     *
     * <p>Returns without change if this card has moved on. A traveller
     * cannot resubmit a card that is already PUBLISHED, and silently
     * rewinding one would break the moderation trail.
     */
    public void markSubmitted(Review review) {
        if (!status.acceptsReview()) {
            return;
        }

        this.status = ReviewUnlockStatus.SUBMITTED;
        this.review = review;

        if (this.eligibleAt == null) {
            this.eligibleAt = now();
        }
    }

    /** Mirrors the review's own moderation outcome. */
    public void markPublished(Review review) {
        if (review == null) {
            return;
        }

        this.review = review;
        this.status = ReviewUnlockStatus.PUBLISHED;
    }

    public void markFlagged(Review review) {
        if (review == null) {
            return;
        }

        this.review = review;
        this.status = ReviewUnlockStatus.FLAGGED;
    }

    /**
     * Reopens a card whose review was rejected outright.
     *
     * <p>ReviewService withdraws a rejected review and the traveller
     * is entitled to write another. Without this the card would sit in
     * SUBMITTED forever pointing at a review that no longer exists,
     * and the service could never be reviewed at all.
     */
    public void reopen() {
        this.status = ReviewUnlockStatus.ELIGIBLE;
        this.review = null;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime stamp = now();

        this.createdAt = stamp;
        this.updatedAt = stamp;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = now();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now()
                .truncatedTo(ChronoUnit.MICROS);
    }

    /* ============================================================
     * ACCESSORS
     * ============================================================ */

    public Long getUnlockId() {
        return unlockId;
    }

    public Trip getTrip() {
        return trip;
    }

    public ReviewTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public Booking getBooking() {
        return booking;
    }

    public ReviewUnlockStatus getStatus() {
        return status;
    }

    public String getUnlockReason() {
        return unlockReason;
    }

    public LocalDateTime getEligibleAt() {
        return eligibleAt;
    }

    public LocalDate getCheckIn() {
        return checkIn;
    }

    public LocalDate getCheckOut() {
        return checkOut;
    }

    public String getCityName() {
        return cityName;
    }

    /**
     * Attaches the stay this card describes.
     *
     * <p>Only on a freshly created row, so a card cannot be rewritten
     * by a later derivation. Idempotent when re-applied, because
     * re-deriving an existing card must not disturb it.
     */
    public void attachStay(
            LocalDate checkIn,
            LocalDate checkOut,
            String cityName
    ) {
        if (this.checkIn != null || this.checkOut != null) {
            return;
        }

        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.cityName = cityName;
    }

    public Review getReview() {
        return review;
    }
}
