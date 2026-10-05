package com.Travel.Buddy.dto.booking;

import jakarta.validation.constraints.NotBlank;

/** Checkout.js success payload, verified on the server before a booking is confirmed. */
public record VerifyPaymentRequest(

        @NotBlank(message = "Razorpay order id is required")
        String razorpayOrderId,

        @NotBlank(message = "Razorpay payment id is required")
        String razorpayPaymentId,

        @NotBlank(message = "Razorpay signature is required")
        String razorpaySignature

) {
}
