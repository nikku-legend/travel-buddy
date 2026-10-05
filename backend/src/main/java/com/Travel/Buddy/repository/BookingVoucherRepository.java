package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.BookingVoucher;
import com.Travel.Buddy.entity.VoucherStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingVoucherRepository
        extends JpaRepository<BookingVoucher, Long> {

    Optional<BookingVoucher> findByBooking_BookingId(
            Long bookingId
    );

    Optional<BookingVoucher> findByVoucherCodeIgnoreCase(
            String voucherCode
    );

    /**
     * All vouchers belonging to one guest, newest first.
     *
     * <p>Used by the traveler's wallet so a voucher can be found
     * without scanning.
     */
    @Query("""
            SELECT voucher
            FROM BookingVoucher voucher
            WHERE voucher.booking.user.userId = :userId
            ORDER BY voucher.issuedAt DESC
            """)
    List<BookingVoucher> findByUser(
            @Param("userId") Long userId
    );

    /**
     * Vouchers a front desk should look at, soonest expiry first.
     *
     * <p>A booking links to a property only indirectly, so this uses
     * an EXISTS over the reservation rather than a direct path that
     * {@code Booking} does not expose.
     */
    @Query("""
            SELECT voucher
            FROM BookingVoucher voucher
            WHERE EXISTS (
                SELECT 1
                FROM HotelReservation reservation
                WHERE reservation.booking = voucher.booking
                  AND reservation.roomType.property.partner.userId = :partnerId
            )
            ORDER BY voucher.validUntil ASC
            """)
    List<BookingVoucher> findByPartner(
            @Param("partnerId") Long partnerId
    );

    /**
     * Locks the voucher row.
     *
     * <p>Vouchers are single use, so two staff scanning the same QR
     * at the same moment must not both succeed. The row lock is what
     * makes consumption atomic.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT voucher
            FROM BookingVoucher voucher
            WHERE voucher.voucherId = :voucherId
            """)
    Optional<BookingVoucher> findByIdForUpdate(
            @Param("voucherId") Long voucherId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT voucher
            FROM BookingVoucher voucher
            WHERE UPPER(voucher.voucherCode) = UPPER(:voucherCode)
            """)
    Optional<BookingVoucher> findByCodeForUpdate(
            @Param("voucherCode") String voucherCode
    );

    long countByStatus(VoucherStatus status);

    List<BookingVoucher> findByStatusAndValidUntilBefore(
            VoucherStatus status,
            LocalDate date
    );

    /**
     * Vouchers for a set of bookings, so the partner's reservation list
     * can show the code the guest will present without an N+1.
     *
     * <p>Returns every status, including voided ones: the front desk
     * seeing a voided code is information, not noise.
     */
    List<BookingVoucher> findByBooking_BookingIdIn(
            java.util.Collection<Long> bookingIds
    );
}