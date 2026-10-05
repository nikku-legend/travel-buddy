package com.Travel.Buddy.dto.booking;

import jakarta.validation.constraints.NotNull;

/** Development-only payment-gateway result. */
public record MockPaymentRequest(

        @NotNull(message = "Payment result is required")
        Boolean paymentSuccessful

) {
}
