package com.Travel.Buddy.dto.trip;

public record TripPaymentOrderResponse(
        Long checkoutId,
        String checkoutReference,
        String orderId,
        long amount,
        String currency,
        String keyId
) {
}
