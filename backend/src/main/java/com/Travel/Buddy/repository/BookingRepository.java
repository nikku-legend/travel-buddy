package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.Booking;
import com.Travel.Buddy.entity.BookingStatus;
import com.Travel.Buddy.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository
        extends JpaRepository<Booking, Long> {

    /*
     * ============================================================
     * BOOKING REFERENCE
     * ============================================================
     */

    boolean existsByBookingReference(
            String bookingReference
    );


    /*
     * ============================================================
     * USER BOOKING HISTORY
     * ============================================================
     */

    List<Booking> findByUser_UserIdOrderByCreatedAtDesc(
            Long userId
    );


    /*
     * ============================================================
     * LOCK SINGLE BOOKING
     * ============================================================
     *
     * Used whenever booking/payment state is changed.
     *
     * This is critical because these operations can happen
     * concurrently:
     *
     * 1. User payment request
     * 2. Payment verification
     * 3. Hold-expiry scheduler
     * 4. Cancellation request
     *
     * Only one transaction should be able to modify the booking
     * state at a time.
     */

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b
            FROM Booking b
            WHERE b.bookingId = :bookingId
            """)
    Optional<Booking> findByIdForUpdate(
            @Param("bookingId")
            Long bookingId
    );


    /*
     * ============================================================
     * FIND EXPIRED PAYMENT HOLDS
     * ============================================================
     *
     * Only bookings that are:
     *
     * PENDING
     * +
     * UNPAID
     * +
     * holdExpiresAt <= current time
     *
     * are returned.
     *
     * CANCELLED / CONFIRMED bookings are automatically excluded.
     *
     * Pessimistic locking prevents the scheduler from processing
     * the same booking concurrently with a payment request.
     */

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b
            FROM Booking b
            WHERE b.bookingStatus = :bookingStatus
              AND b.paymentStatus = :paymentStatus
              AND b.holdExpiresAt IS NOT NULL
              AND b.holdExpiresAt <= :now
            ORDER BY b.bookingId ASC
            """)
    List<Booking> findExpiredUnpaidHoldsForUpdate(
            @Param("bookingStatus")
            BookingStatus bookingStatus,

            @Param("paymentStatus")
            PaymentStatus paymentStatus,

            @Param("now")
            LocalDateTime now
    );
}