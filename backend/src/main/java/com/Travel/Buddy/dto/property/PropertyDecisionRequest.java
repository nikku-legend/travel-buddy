package com.Travel.Buddy.dto.property;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An admin's decision on a property submitted for approval.
 *
 * <p>A reason is mandatory for any outcome that is not an approval, so
 * the partner always knows what to fix.
 */
public record PropertyDecisionRequest(

        @NotNull(message = "A decision is required")
        Boolean approved,

        @Size(max = 500, message = "Reason must not exceed 500 characters")
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
                    "A reason is required when rejecting or suspending a property"
            );
        }

        return reason.trim();
    }
}