package com.Travel.Buddy.dto.cancellation;

import com.Travel.Buddy.entity.CancellationReason;
import com.Travel.Buddy.entity.RefundStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingCancellationResponse(

        Long cancellationId,

        Long bookingId,

        String bookingReference,

        CancellationReason cancellationReason,

        String cancellationNote,

        LocalDateTime cancelledAt,

        RefundStatus refundStatus,

        BigDecimal refundAmount,

        String refundCurrency,

        String refundReference,

        LocalDateTime refundProcessedAt,

        LocalDateTime createdAt

) {
}