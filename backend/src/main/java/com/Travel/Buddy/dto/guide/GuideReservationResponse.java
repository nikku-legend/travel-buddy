package com.Travel.Buddy.dto.guide;

import com.Travel.Buddy.entity.BookingStatus;
import com.Travel.Buddy.entity.GuideReservation;
import com.Travel.Buddy.entity.GuideReservationStatus;
import com.Travel.Buddy.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One tour booked with a guide. (SRS 2.2 FR-16, FR-23)
 *
 * <p>Exists because the partner portal had no way to see work it
 * had actually been given, and was showing invented numbers
 * instead.
 *
 * <p>The traveller is named but not emailed or phoned: a guide
 * needs to know who is arriving, not how to contact them through
 * the platform, and every other surface here behaves the same way.
 */
public record GuideReservationResponse(

        Long reservationId,

        Long guideId,

        Long bookingId,

        String bookingReference,

        BookingStatus bookingStatus,

        PaymentStatus paymentStatus,

        LocalDate tourDate,

        /**
         * How far the guide has reported this tour getting, added in
         * V54. Shown beside the tour date so the partner can tell an
         * outstanding tour from one they have already closed out.
         */
        GuideReservationStatus status,

        /*
         * What the traveller paid for this tour, and what the guide
         * is owed before commission. Commission itself is FR-34
         * and is not deducted here, so this figure cannot silently
         * become a net number that disagrees with the booking.
         */
        BigDecimal amount,

        String currency,

        String travellerName,

        Integer partySize
) {
    public static GuideReservationResponse of(
            GuideReservation reservation
    ) {
        var booking = reservation.getBooking();

        return new GuideReservationResponse(
                reservation.getGuideReservationId(),
                reservation.getGuide() == null
                        ? null
                        : reservation.getGuide().getGuideId(),
                booking == null ? null : booking.getBookingId(),
                booking == null ? null : booking.getBookingReference(),
                booking == null ? null : booking.getBookingStatus(),
                booking == null ? null : booking.getPaymentStatus(),
                reservation.getTourDate(),
                reservation.getStatus(),
                booking == null ? null : booking.getTotalAmount(),
                booking == null ? null : booking.getCurrency(),
                booking == null ? null : booking.getGuestName(),
                booking == null ? null : booking.getGuestCount()
        );
    }
}