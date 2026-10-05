package com.Travel.Buddy.dto.voucher;

import com.Travel.Buddy.entity.VoucherStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A guest's voucher wallet entry. (FR-24)
 *
 * <p>{@code qrPayload} is the exact string a QR encoder should
 * render. It contains no guest data, so a screenshot is safe to
 * share; the hotel only learns who the guest is after scanning
 * succeeds.
 */
public record VoucherResponse(
        Long voucherId,
        Long bookingId,
        String bookingReference,
        VoucherStatus status,
        String voucherCode,
        String guestName,
        LocalDate validFrom,
        LocalDate validUntil,
        LocalDateTime issuedAt,
        LocalDateTime consumedAt,
        LocalDateTime voidedAt,
        String voidReason,
        Integer verificationCount,

        /* The exact string to encode as a QR image. */
        String qrPayload,

        boolean usable,
        String unusableReason
) {
}