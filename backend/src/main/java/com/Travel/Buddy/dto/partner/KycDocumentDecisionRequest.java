package com.Travel.Buddy.dto.partner;

import com.Travel.Buddy.entity.KycDocumentStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Per-document verification decision made by an admin.
 *
 * <p>Verifying individual documents rather than the whole application
 * at once is what lets an admin approve a valid identity proof while
 * rejecting an unreadable licence in the same review session.
 */
public record KycDocumentDecisionRequest(

        @NotNull(message = "A document decision is required")
        KycDocumentStatus status,

        String reason
) {

    /**
     * Rejecting a document always requires a reason, for the same
     * reason a rejected application always requires one.
     */
    public String validatedReason() {
        if (status == KycDocumentStatus.REJECTED) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "A reason is required when rejecting a document"
                );
            }

            return reason.trim();
        }

        return reason == null || reason.isBlank()
                ? null
                : reason.trim();
    }
}
