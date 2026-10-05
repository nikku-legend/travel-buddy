package com.Travel.Buddy.dto.voucher;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Staff scanning or keying in a voucher at the front desk. (FR-24)
 *
 * <p>Accepts either the scanned QR payload or the short manual
 * code, because a phone camera will not always focus on a
 * damaged or laminated voucher.
 */
public record VoucherScanRequest(

        @NotBlank(message = "A voucher code or scanned QR payload is required")
        @Size(max = 512, message = "Voucher payload is too long")
        String code
) {
}