package com.Travel.Buddy.dto.admin;

import com.Travel.Buddy.entity.CancellationReason;
import com.Travel.Buddy.entity.RefundStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One refund on the desk, as the console shows it. (FR-33)
 */
public record AdminRefundResponse(

        Long cancellationId,

        Long bookingId,

        String bookingReference,

        String guestName,

        CancellationReason cancellationReason,

        BigDecimal refundAmount,

        String refundCurrency,

        RefundStatus refundStatus,

        LocalDateTime cancelledAt
) {
}