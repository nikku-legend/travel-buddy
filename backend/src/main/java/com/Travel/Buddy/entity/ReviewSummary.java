package com.Travel.Buddy.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Denormalised rating aggregate for one reviewable subject. (FR-26)
 *
 * <p>Recomputing an average over the review table on every read of a
 * property, guide or cab is a query that gets slower as the
 * marketplace grows. This row is rewritten whenever a review is
 * published, rejected or edited, so a public page costs one lookup.
 *
 * <p>The star histogram is kept as well as the average, because a
 * 4.2 average means something very different when it is 4.2 from a
 * hundred reviews than from three.
 */
@Entity
@Table(name = "review_summaries")
@IdClass(ReviewSummaryId.class)
public class ReviewSummary {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReviewTargetType targetType;

    @Id
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Column(name = "review_count", nullable = false)
    private Integer reviewCount = 0;

    @Column(name = "five_star_count", nullable = false)
    private Integer fiveStarCount = 0;

    @Column(name = "four_star_count", nullable = false)
    private Integer fourStarCount = 0;

    @Column(name = "three_star_count", nullable = false)
    private Integer threeStarCount = 0;

    @Column(name = "two_star_count", nullable = false)
    private Integer twoStarCount = 0;

    @Column(name = "one_star_count", nullable = false)
    private Integer oneStarCount = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * The star count for one rating, used when rebuilding the
     * histogram.
     */
    public Integer bucketFor(int rating) {
        return switch (rating) {
            case 5 -> fiveStarCount;
            case 4 -> fourStarCount;
            case 3 -> threeStarCount;
            case 2 -> twoStarCount;
            case 1 -> oneStarCount;
            default -> 0;
        };
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

    public BigDecimal getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(BigDecimal averageRating) {
        this.averageRating = averageRating;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }

    public Integer getFiveStarCount() {
        return fiveStarCount;
    }

    public void setFiveStarCount(Integer fiveStarCount) {
        this.fiveStarCount = fiveStarCount;
    }

    public Integer getFourStarCount() {
        return fourStarCount;
    }

    public void setFourStarCount(Integer fourStarCount) {
        this.fourStarCount = fourStarCount;
    }

    public Integer getThreeStarCount() {
        return threeStarCount;
    }

    public void setThreeStarCount(Integer threeStarCount) {
        this.threeStarCount = threeStarCount;
    }

    public Integer getTwoStarCount() {
        return twoStarCount;
    }

    public void setTwoStarCount(Integer twoStarCount) {
        this.twoStarCount = twoStarCount;
    }

    public Integer getOneStarCount() {
        return oneStarCount;
    }

    public void setOneStarCount(Integer oneStarCount) {
        this.oneStarCount = oneStarCount;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}