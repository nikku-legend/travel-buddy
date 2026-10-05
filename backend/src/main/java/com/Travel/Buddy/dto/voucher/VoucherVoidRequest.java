package com.Travel.Buddy.dto.voucher;

import jakarta.validation.constraints.Size;

/**
 * Reason a voucher is being voided. (FR-24)
 */
public record VoucherVoidRequest(

        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason
) {
}