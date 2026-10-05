package com.Travel.Buddy.dto.inventory;

import com.Travel.Buddy.entity.RoomBlockReason;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * A partner's request to take rooms out of service. (FR-21)
 *
 * <p>Blocking more rooms than are free on a date is rejected by the
 * service, so a partner can never block away inventory a guest has
 * already paid for.
 */
public record RoomBlockRequest(

        @NotNull(message = "A start date is required")
        LocalDate fromDate,

        @NotNull(message = "An end date is required")
        LocalDate endDate,

        @NotNull(message = "Number of rooms is required")
        @Min(value = 1, message = "At least one room must be blocked")
        @Max(value = 500, message = "Cannot block more than 500 rooms")
        Integer rooms,

        RoomBlockReason reason,

        @Size(max = 500, message = "Notes must not exceed 500 characters")
        String notes
) {
}