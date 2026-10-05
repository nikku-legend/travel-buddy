package com.Travel.Buddy.dto.trip;

import jakarta.validation.constraints.NotNull;

/**
 * Simulates a gateway result for a trip checkout. (TP-10)
 *
 * <p>Named for what it is. A real payment confirmation would
 * arrive from Razorpay over a signed webhook, and that path must
 * not be reachable by posting to an endpoint the browser calls.
 */
public record PayTripCheckoutRequest(

        @NotNull(message = "Checkout id is required")
        Long checkoutId,

        /**
         * False exercises the decline path, which is the one
         * worth being able to see without waiting for a card to
         * be declined in real life.
         */
        Boolean paymentSuccessful
) {
}