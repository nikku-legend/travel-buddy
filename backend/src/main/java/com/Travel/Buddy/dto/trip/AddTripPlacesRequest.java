package com.Travel.Buddy.dto.trip;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Selects several places at once, because TP-04 is explicitly a
 * multi-select step and the traveller should not make one
 * request per card they tap.
 */
public record AddTripPlacesRequest(

        @NotNull
        List<PlaceSelection> places
) {
    public record PlaceSelection(

            @NotNull
            Long tripCityId,

            @NotNull
            Long placeId,

            String note
    ) {
    }
}