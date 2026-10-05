package com.Travel.Buddy.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A traveller's review of a hotel, guide, cab or destination.
 * (FR-26)
 *
 * <p>Entitlement is the point of this class: a review carries the
 * {@link Booking} that proves the reviewer actually stayed,
 * travelled or was driven. {@code ReviewService} refuses to create
 * one without that evidence, so a marketplace cannot be flooded by
 * accounts reviewing places they never visited.
 */
@Entity
@Table(
        name = "reviews",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_reviews_user_target",
                        columnNames = {"user_id", "target_type", "target_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_reviews_target",
                        columnList = "target_type, target_id, status, created_at"
                ),
                @Index(
                        name = "idx_reviews_user",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_reviews_status",
                        columnList = "status"
                )
        }
)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_reviews_user")
    )
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReviewTargetType targetType;

    /**
     * Polymorphic reference. See {@link ReviewTargetType}.
     */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /**
     * The completed booking that entitles the reviewer to review.
     * NULL only for destination reviews, which need no stay.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "booking_id",
            foreignKey = @ForeignKey(name = "fk_reviews_booking")
    )
    private Booking booking;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "title", length = 150)
    private String title;

    @Column(name = "comment", length = 2000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReviewStatus status = ReviewStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "moderated_by_user_id",
            foreignKey = @ForeignKey(name = "fk_reviews_moderated_by")
    )
    private User moderatedBy;

    @Column(name = "moderated_at")
    private LocalDateTime moderatedAt;

    @Column(name = "moderation_reason", length = 500)
    private String moderationReason;

    @Column(name = "flagged_count", nullable = false)
    private Integer flaggedCount = 0;

    @Column(name = "helpful_count", nullable = false)
    private Integer helpfulCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    public Long getReviewId() {
        return reviewId;
    }

    public void setReviewId(Long reviewId) {
        this.reviewId = reviewId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public ReviewTargetType getTargetType() {
        return targetType;
    }

    public void setTargetType(ReviewTargetType targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewStatus status) {
        this.status = status;
    }

    public User getModeratedBy() {
        return moderatedBy;
    }

    public void setModeratedBy(User moderatedBy) {
        this.moderatedBy = moderatedBy;
    }

    public LocalDateTime getModeratedAt() {
        return moderatedAt;
    }

    public void setModeratedAt(LocalDateTime moderatedAt) {
        this.moderatedAt = moderatedAt;
    }

    public String getModerationReason() {
        return moderationReason;
    }

    public void setModerationReason(String moderationReason) {
        this.moderationReason = moderationReason;
    }

    public Integer getFlaggedCount() {
        return flaggedCount;
    }

    public void setFlaggedCount(Integer flaggedCount) {
        this.flaggedCount = flaggedCount;
    }

    public Integer getHelpfulCount() {
        return helpfulCount;
    }

    public void setHelpfulCount(Integer helpfulCount) {
        this.helpfulCount = helpfulCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}