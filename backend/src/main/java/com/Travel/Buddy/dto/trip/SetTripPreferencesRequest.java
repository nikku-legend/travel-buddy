package com.Travel.Buddy.dto.trip;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Steps 1 and 5 of the SRS 2.3 wizard: who is travelling, and in
 * what style.
 *
 * <p>Grouped into one call because persons and travel style are
 * both cheap, non-geographic preferences. Zone, region and dates
 * are separate because each narrows what can be suggested and
 * therefore earns its own screen.
 */
public record SetTripPreferencesRequest(

        /*
         * TP-02. Total travellers, which drives room occupancy,
         * cab capacity and whether a guide suits the group.
         */
        @NotNull
        @Min(value = 1, message = "At least one traveller")
        @Max(value = 30, message = "At most 30 travellers")
        Integer travelerCount,

        /*
         * The split is optional. The SRS says "if the UI collects
         * it", and some interfaces only ask for a headcount.
         */
        @Min(value = 0, message = "Cannot be negative")
        Integer adultCount,

        @Min(value = 0, message = "Cannot be negative")
        Integer childCount,

        /* TP-05. */
        @Size(max = 20)
        String travelStyle
) {
}