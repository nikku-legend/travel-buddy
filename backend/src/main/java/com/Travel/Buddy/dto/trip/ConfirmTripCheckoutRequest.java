package com.Travel.Buddy.dto.trip;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Confirms that the traveller has seen a price change during
 * revalidation. (SRS 2.2 section 6)
 *
 * <p>Section 6 requires the affected line item to be shown and
 * explicit confirmation given before the new total is charged.
 * This is that confirmation, and it carries the total the
 * traveller is agreeing to so a changed quote cannot slip
 * through between the review screen and the payment call.
 */
public record ConfirmTripCheckoutRequest(

        @NotNull
        Long checkoutId,

        /*
         * Must match the revalidated total. A mismatch means the
         * bill moved again and the traveller has not seen it.
         */
        @NotNull
        java.math.BigDecimal acceptedTotal,

        String currency
) {
}