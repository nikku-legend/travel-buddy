package com.Travel.Buddy.dto.review;

import com.Travel.Buddy.entity.ReviewStatus;
import com.Travel.Buddy.entity.ReviewTargetType;

import java.time.LocalDateTime;

/**
 * A review as the reader sees it.
 *
 * <p>{@code authorName} is a display name only. Contact details are
 * never returned with a public review.
 */
public record ReviewResponse(
        Long reviewId,
        ReviewTargetType targetType,
        Long targetId,
        String targetName,
        Integer rating,
        String title,
        String comment,
        ReviewStatus status,
        String authorName,
        String verifiedStay,
        Integer helpfulCount,
        Integer flaggedCount,
        LocalDateTime createdAt,
        LocalDateTime moderatedAt,
        String moderationReason,
        boolean canEdit,
        boolean canDelete
) {
}