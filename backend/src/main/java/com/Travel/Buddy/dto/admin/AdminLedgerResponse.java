package com.Travel.Buddy.dto.admin;

import com.Travel.Buddy.entity.BookingStatus;
import com.Travel.Buddy.entity.BookingType;
import com.Travel.Buddy.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The payment ledger for the financial desk. (FR-33)
 *
 * <p>Bookings are the ledger: every row that moved (or failed to
 * move) money is a booking with a payment status. There is no
 * separate payments table to reconcile against, so the console
 * shows exactly what the booking system knows.
 */
public record AdminLedgerResponse(

        int page,

        int size,

        long totalElements,

        int totalPages,

        List<Row> rows
) {

    public record Row(
            Long bookingId,
            String bookingReference,
            String guestName,
            BookingType bookingType,
            BigDecimal totalAmount,
            String currency,
            PaymentStatus paymentStatus,
            BookingStatus bookingStatus,
            LocalDateTime createdAt
    ) {
    }
}