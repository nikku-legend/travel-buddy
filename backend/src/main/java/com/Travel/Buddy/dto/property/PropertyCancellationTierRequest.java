package com.Travel.Buddy.dto.property;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One cancellation tier. (FR-11, SRS 2.3 section 6.2)
 *
 * <p>{@code refundPercent} is the traveller's refund, not the
 * penalty. Refusing to accept a value outside 0..100 is what stops
 * a property promising a 150% refund by entering the deduction it
 * meant to charge.
 */
public record PropertyCancellationTierRequest(

        @Min(0)
        @Max(365)
        Integer daysBeforeCheckIn,

        @NotNull
        @DecimalMin("0.00")
        @DecimalMax("100.00")
        BigDecimal refundPercent,

        @Min(0)
        Integer minNightsCharge,

        @Size(max = 200)
        String description
) {
}