package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.BookingStatus;
import com.Travel.Buddy.entity.HotelReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HotelReservationRepository
        extends JpaRepository<HotelReservation, Long> {

    Optional<HotelReservation> findByBooking_BookingId(
            Long bookingId
    );

    /**
     * Everything a partner still has to act on, soonest arrival first.
     *
     * <p>This is the query the front desk was missing. {@code
     * RoomStayRepository.findByPartnerAndStatuses} only returns stays
     * that already HAVE a physical room, so a partner could see
     * assigned guests but had no way to find out who was arriving
     * tomorrow and still needed a room — the single most important
     * question the front desk asks.
     *
     * <p>Scoped through the reservation's room type and property rather
     * than through the booking's user, because ownership of a booking
     * belongs to the traveller; the property belongs to the partner.
     */
    @Query("""
            SELECT reservation
            FROM HotelReservation reservation
            WHERE reservation.roomType.property.partner.userId = :partnerId
              AND reservation.booking.bookingStatus IN :statuses
            ORDER BY reservation.checkIn ASC, reservation.reservationId ASC
            """)
    List<HotelReservation> findForPartner(
            @Param("partnerId") Long partnerId,
            @Param("statuses") Collection<BookingStatus> statuses
    );

    List<HotelReservation> findByBooking_BookingIdIn(
            Collection<Long> bookingIds
    );
}