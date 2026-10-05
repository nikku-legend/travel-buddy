package com.Travel.Buddy.dto.voucher;

import com.Travel.Buddy.entity.VoucherStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The result of scanning a voucher at the front desk. (FR-24)
 *
 * <p>Guest details appear only on a successful verification, so an
 * invalid scan reveals nothing about who the guest is.
 */
public record VoucherVerificationResponse(
        boolean valid,
        String outcome,
        String message,

        /* Only populated when valid. */
        VoucherResponse voucher,

        String bookingReference,
        Long propertyId,
        String propertyName,
        String roomCategory,
        String guestName,
        LocalDate checkIn,
        LocalDate checkOut,
        Integer roomsBooked,
        String assignedRoomNumbers,

        /* Only populated when valid. */
        Boolean stayCheckedIn,
        Long stayId
) {

    public static VoucherVerificationResponse rejected(
            String message
    ) {
        return new VoucherVerificationResponse(
                false,
                "REJECTED",
                message,

                /*
                 * Every guest-facing field stays null on a failed
                 * scan, so probing random codes is not a way to
                 * discover who is staying or in which room.
                 */
                null, null, null, null, null, null,
                null, null, null, null, null, null
        );
    }
}