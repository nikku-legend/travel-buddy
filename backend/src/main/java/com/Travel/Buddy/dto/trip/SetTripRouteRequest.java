package com.Travel.Buddy.dto.trip;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Replaces the whole route in one call. (TP-02, TP-03)
 *
 * <p>The traveller's chosen order is authoritative. The engine
 * proposes an order and the traveller may ignore it, so this
 * accepts an explicit list rather than a delta to apply: it makes
 * "the order I see is the order that gets saved" true by
 * construction.
 */
public record SetTripRouteRequest(

        @NotNull
        List<CityStopRequest> stops
) {
    public record CityStopRequest(

            @NotNull
            Long cityId,

            /*
             * Optional. The planner can propose dates, but the
             * traveller's own dates win, and leaving both null
             * means "not decided yet" rather than a zero-length
             * stay that would break hotel recommendations.
             */
            java.time.LocalDate arrivalDate,

            java.time.LocalDate departureDate
    ) {
    }
}