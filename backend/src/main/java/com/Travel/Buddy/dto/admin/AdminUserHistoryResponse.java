package com.Travel.Buddy.dto.admin;

import com.Travel.Buddy.entity.BookingStatus;
import com.Travel.Buddy.entity.BookingType;
import com.Travel.Buddy.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A user's booking history for the admin inspection panel.
 * (FR-31 "view user history")
 *
 * <p>Deliberately not {@code BookingResponse}: that record is
 * shaped around stays (property, room, dates), and most of it is
 * null for a ride or an activity. These rows describe any
 * booking equally.
 */
public record AdminUserHistoryResponse(

        AdminUserResponse user,

        List<Row> bookings
) {

    public record Row(
            Long bookingId,
            String bookingReference,
            BookingType bookingType,
            BigDecimal totalAmount,
            String currency,
            BookingStatus bookingStatus,
            PaymentStatus paymentStatus,
            LocalDateTime createdAt
    ) {
    }
}