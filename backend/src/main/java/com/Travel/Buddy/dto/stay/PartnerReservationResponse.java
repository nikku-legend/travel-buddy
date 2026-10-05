package com.Travel.Buddy.dto.stay;

import com.Travel.Buddy.entity.BookingStatus;
import com.Travel.Buddy.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One reservation as the front desk needs to see it. (FR-22, FR-23)
 *
 * <p>Deliberately carries the <em>action</em> alongside the facts:
 * {@code needsRoom}, {@code canAssign} and {@code canCheckIn} are
 * computed on the server from the real stay rows, so the portal never
 * re-derives the workflow rules in the browser and cannot drift out of
 * step with them.
 *
 * <p>Guest contact details are included because a hotel genuinely has to
 * reach an arriving guest. They are scoped to the property owner by the
 * query that produces this, so one partner cannot read another's guests.
 */
public record PartnerReservationResponse(
        Long bookingId,
        String bookingReference,
        Long propertyId,
        String propertyName,
        Long roomTypeId,
        String roomCategory,
        LocalDate checkIn,
        LocalDate checkOut,
        long nights,
        int roomsBooked,
        int roomsAssigned,
        String assignedRoomNumbers,

        String guestName,
        Integer guestCount,
        String guestPhone,
        String specialRequests,

        BookingStatus bookingStatus,
        PaymentStatus paymentStatus,
        BigDecimal totalAmount,
        String currency,

        String voucherCode,

        /* ============================================================
         * WHAT THE PARTNER CAN DO NEXT
         * ============================================================ */

        /**
         * More rooms are still owed to this guest. The number, not a
         * boolean: a booking of three rooms with one assigned needs two
         * more, and "yes" would not tell the front desk how much work
         * is outstanding.
         */
        int roomsStillToAssign,

        boolean canAssign,
        boolean canCheckIn,
        boolean canCheckOut,
        boolean canMarkNoShow,

        /**
         * True once every promised room has a real room number. Until
         * then the guest has a reservation but not a bed.
         */
        boolean fullyAssigned
) {
}
