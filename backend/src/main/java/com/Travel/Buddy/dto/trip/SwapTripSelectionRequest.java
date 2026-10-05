package com.Travel.Buddy.dto.trip;

import com.Travel.Buddy.entity.TripSelectionType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Replaces a selected service with a different one. (SRS 2.3
 * section 6.2, "Replace recommended hotel")
 *
 * <p>Dates are optional and default to the selection being
 * replaced. Where the traveller does supply them they must be
 * used, because a swap that silently kept the old dates would
 * leave a stay on dates the traveller had already moved on from.
 */
public record SwapTripSelectionRequest(

        @NotNull
        TripSelectionType selectionType,

        @NotNull
        Long targetId,

        Long roomTypeId,

        LocalDate checkIn,

        LocalDate checkOut,

        Integer guests,

        Integer rooms,

        String currency
) {
}