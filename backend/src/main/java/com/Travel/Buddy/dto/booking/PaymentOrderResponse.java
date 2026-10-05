package com.Travel.Buddy.dto.booking;

/**
 * Details the browser needs to open Razorpay Checkout.
 * {@code amount} is in the currency's smallest unit (paise for INR).
 */
public record PaymentOrderResponse(

        String orderId,

        long amount,

        String currency,

        String keyId,

        Long bookingId,

        String bookingReference

) {
}
