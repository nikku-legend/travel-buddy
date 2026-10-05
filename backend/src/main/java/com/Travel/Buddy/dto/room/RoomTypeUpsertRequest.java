package com.Travel.Buddy.dto.room;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Room type details submitted by a hotel partner. (FR-05)
 *
 * <p>{@code totalInventory} is the number of physical rooms of this type.
 * Daily availability rows are generated from it automatically; partners
 * never set per-day availability here.
 */
public record RoomTypeUpsertRequest(

        @NotBlank(message = "Category name is required")
        @Size(max = 150, message = "Category name must not exceed 150 characters")
        String categoryName,

        @NotNull(message = "Maximum occupancy is required")
        @Min(value = 1, message = "Maximum occupancy must be at least 1")
        @Max(value = 20, message = "Maximum occupancy must not exceed 20")
        Integer maxOccupancy,

        @NotNull(message = "Base price is required")
        @DecimalMin(value = "0.0", message = "Base price must not be negative")
        BigDecimal basePrice,

        @Size(min = 3, max = 3, message = "Currency must be a 3 letter code")
        String currency,

        @NotNull(message = "Total inventory is required")
        @Min(value = 1, message = "Total inventory must be at least 1")
        @Max(value = 500, message = "Total inventory must not exceed 500")
        Integer totalInventory
) {
}