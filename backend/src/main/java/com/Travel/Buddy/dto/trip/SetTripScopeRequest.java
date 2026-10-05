package com.Travel.Buddy.dto.trip;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Steps 2 and 3 of the SRS 2.3 wizard: zone then region.
 *
 * <p>Zone is the broad travel area and region narrows within it.
 * Both are free strings because the geography hierarchy has no
 * region table; what actually constrains recommendations is the
 * state that resolves from them.
 */
public record SetTripScopeRequest(

        @NotNull
        @Size(max = 30, message = "Zone name is too long")
        String zone,

        @NotNull
        @Size(max = 120, message = "Region name is too long")
        String regionName
) {
}