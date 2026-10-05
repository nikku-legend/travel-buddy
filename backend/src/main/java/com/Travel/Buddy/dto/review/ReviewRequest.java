package com.Travel.Buddy.dto.review;

import com.Travel.Buddy.entity.ReviewTargetType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A review submitted by a traveller. (FR-26)
 *
 * <p>No booking id is accepted from the client on purpose. The
 * service picks the booking that proves entitlement, so a caller
 * cannot attach someone else's stay to their own review.
 */
public record ReviewRequest(

        @NotNull(message = "A review target type is required")
        ReviewTargetType targetType,

        @NotNull(message = "A review target is required")
        Long targetId,

        @NotNull(message = "A rating is required")
        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating must be at most 5")
        Integer rating,

        @Size(max = 150, message = "Title must not exceed 150 characters")
        String title,

        @Size(max = 2000, message = "Comment must not exceed 2000 characters")
        String comment
) {
}