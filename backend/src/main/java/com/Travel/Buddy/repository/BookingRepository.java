package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.Booking;
import com.Travel.Buddy.entity.BookingStatus;
import com.Travel.Buddy.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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

    long countByUser_UserId(Long userId);


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


    /*
     * ============================================================
     * ADMIN CONSOLE  (FR-30, FR-33)
     * ============================================================
     */

    /**
     * Newest sales for the command centre's live feed.
     */
    List<Booking> findTop8ByOrderByCreatedAtDesc();

    /**
     * The payment ledger: every booking, newest first, paged.
     */
    List<Booking> findAllByOrderByCreatedAtDesc(
            Pageable pageable
    );

    List<Booking> findAllByPaymentStatusOrderByCreatedAtDesc(
            PaymentStatus paymentStatus,
            Pageable pageable
    );

    long countByPaymentStatus(PaymentStatus paymentStatus);

    long countByBookingStatus(BookingStatus bookingStatus);

    /**
     * Gross platform revenue. Only PAID money counts: an unpaid
     * hold is not revenue, and counting it would let the tile
     * drift from the ledger the finance desk reconciles against.
     */
    @Query("""
            SELECT COALESCE(SUM(b.totalAmount), 0)
            FROM Booking b
            WHERE b.paymentStatus = :paymentStatus
            """)
    BigDecimal sumTotalAmountByPaymentStatus(
            @Param("paymentStatus") PaymentStatus paymentStatus
    );

    /**
     * Booking counts per user, for the users table -- one query
     * for the whole page rather than one per row.
     */
    @Query("""
            SELECT b.user.userId, COUNT(b)
            FROM Booking b
            GROUP BY b.user.userId
            """)
    List<Object[]> countBookingsPerUser();
}