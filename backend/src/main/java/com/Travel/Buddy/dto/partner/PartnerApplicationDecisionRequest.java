package com.Travel.Buddy.dto.partner;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An admin's decision on a pending partner application.
 *
 * <p>A rejection reason is mandatory whenever the decision is not an
 * approval, so the applicant is always told exactly what to fix.
 */
public record PartnerApplicationDecisionRequest(

        @NotNull(message = "A decision is required")
        Boolean approved,

        @Size(max = 500, message = "Rejection reason must not exceed 500 characters")
        String reason
) {

    public String validatedReason() {
        if (Boolean.TRUE.equals(approved)) {
            return reason == null || reason.isBlank()
                    ? null
                    : reason.trim();
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "A reason is required when rejecting an application"
            );
        }

        return reason.trim();
    }
}
