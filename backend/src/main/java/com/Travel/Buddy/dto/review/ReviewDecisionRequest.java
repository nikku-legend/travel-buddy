package com.Travel.Buddy.dto.review;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A moderator's decision on a review. (FR-26, FR-29)
 */
public record ReviewDecisionRequest(

        @NotNull(message = "A decision is required")
        Boolean approved,

        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason
) {

    /**
     * A refusal must always explain itself, so the author knows
     * what to change rather than guessing.
     */
    public String validatedReason() {

        if (Boolean.TRUE.equals(approved)) {
            return reason == null || reason.isBlank()
                    ? null
                    : reason.trim();
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "A reason is required when rejecting a review"
            );
        }

        return reason.trim();
    }
}