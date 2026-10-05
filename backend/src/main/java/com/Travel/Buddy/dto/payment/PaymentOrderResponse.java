package com.Travel.Buddy.dto.payment;

import java.math.BigDecimal;

public record PaymentOrderResponse(

        String orderId,

        BigDecimal amount,

        String currency,

        String keyId,

        Long bookingId,

        String bookingReference

) {
}