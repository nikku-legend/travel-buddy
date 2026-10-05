package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.BookingCancellation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookingCancellationRepository
        extends JpaRepository<BookingCancellation, Long> {

    /**
     * ============================================================
     * FIND CANCELLATION BY BOOKING
     * ============================================================
     */
    Optional<BookingCancellation> findByBooking_BookingId(
            Long bookingId
    );

    /**
     * ============================================================
     * FIND CANCELLATION BY BOOKING WITH WRITE LOCK
     *
     * Used when cancellation/refund state may be updated.
     * ============================================================
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT cancellation
            FROM BookingCancellation cancellation
            WHERE cancellation.booking.bookingId = :bookingId
            """)
    Optional<BookingCancellation> findByBookingIdForUpdate(
            @Param("bookingId") Long bookingId
    );

    /**
     * ============================================================
     * CHECK WHETHER BOOKING ALREADY HAS A CANCELLATION
     * ============================================================
     */
    boolean existsByBooking_BookingId(
            Long bookingId
    );
}