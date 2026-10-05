package com.Travel.Buddy.dto.trip;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Adds a service to the trip cart. (TP-05..TP-08)
 *
 * <p>This records intent and reserves nothing. The response says
 * so explicitly through {@code reserved}, which is false, so a
 * client cannot accidentally present a selection as a booking.
 */
public record AddTripSelectionRequest(

        @NotNull
        com.Travel.Buddy.entity.TripSelectionType selectionType,

        /*
         * Optional: a traveller may attach a guide to the whole
         * trip rather than to one stop.
         */
        Long tripCityId,

        @NotNull
        Long targetId,

        Long roomTypeId,

        LocalDate checkIn,

        LocalDate checkOut,

        @Min(value = 1, message = "At least one guest")
        Integer guests,

        /*
         * How many rooms this stay needs. (SRS 2.3 section 6.2)
         *
         * Optional: when omitted the cart derives the count from
         * the party size. Supplied when the traveller wants more
         * rooms than the headcount strictly needs.
         */
        @Min(value = 1, message = "At least one room")
        Integer rooms,

        BigDecimal quotedAmount,

        String currency
) {
}