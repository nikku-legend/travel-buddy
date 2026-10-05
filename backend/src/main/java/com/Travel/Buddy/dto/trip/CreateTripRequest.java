package com.Travel.Buddy.dto.trip;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateTripRequest(

        @Size(
                max = 150,
                message = "Title must be at most 150 characters"
        )
        String title,

        /*
         * Mandatory and validated as a pair: SRS 2.2 TP-01 makes
         * start and end dates the first required planner input,
         * and a trip with one date is not a trip.
         */
        @NotNull(
                message = "A start date is required"
        )
        LocalDate startDate,

        @NotNull(
                message = "An end date is required"
        )
        LocalDate endDate,

        @Min(
                value = 1,
                message = "A trip must visit at least one city"
        )
        @Max(
                value = 15,
                message = "At most 15 cities in one trip"
        )
        int plannedCityCount,

        BigDecimal budget,

        @Size(max = 3)
        String currency
) {
}