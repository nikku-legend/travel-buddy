package com.Travel.Buddy.dto.trip;

import com.Travel.Buddy.entity.TripCheckoutStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The result of a checkout revalidation. (SRS 2.2 section 6)
 *
 * <p>Section 6 requires that when something changed, the exact
 * affected line item is shown and confirmation is required before
 * charging. {@code requiresConfirmation} is that decision, and
 * {@code changedLines} is the evidence for it.
 */
public record TripCheckoutPreviewResponse(

        Long checkoutId,

        String checkoutReference,

        TripCheckoutStatus status,

        BigDecimal quotedTotal,

        BigDecimal revalidatedTotal,

        boolean priceChanged,

        boolean requiresConfirmation,

        /*
         * Selections that failed revalidation, with the reason.
         * A failed line is never silently substituted, which is
         * what makes this list the traveller's route to a
         * decision rather than a surprise.
         */
        List<UnavailableLine> changedLines,

        TripBillResponse bill,

        LocalDateTime createdAt
) {
    public record UnavailableLine(
            Long selectionId,
            String label,
            String reason
    ) {
    }
}