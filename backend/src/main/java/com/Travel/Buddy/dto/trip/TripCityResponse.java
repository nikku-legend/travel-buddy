package com.Travel.Buddy.dto.trip;

import java.time.LocalDate;

public record TripCityResponse(

        Long tripCityId,

        Long cityId,

        /*
         * The citys state, so a client can scope guide and cab
         * lookups without a second geo round trip. Both public
         * listings take a stateId, and the planner needs one per
         * stop to offer local services.
         */
        Long stateId,

        String cityName,

        String slug,

        int sequence,

        LocalDate arrivalDate,

        LocalDate departureDate,

        /*
         * Why the engine ordered it here, or null once the
         * traveller has reordered and the claim no longer holds.
         */
        String sequenceReason,

        java.math.BigDecimal latitude,

        java.math.BigDecimal longitude,

        int placeCount
) {
}