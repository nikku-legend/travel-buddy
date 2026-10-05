package com.Travel.Buddy.dto.trip;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * TP-04 trip dates, set separately from the rest of the wizard.
 *
 * <p>Separate so a traveller can change dates without re-entering
 * party size or zone, and so the effect on existing selections can
 * be shown on the step where it actually happens.
 */
public record SetTripDatesRequest(

        @NotNull(
                message = "A start date is required"
        )
        LocalDate startDate,

        @NotNull(
                message = "An end date is required"
        )
        LocalDate endDate
) {
}