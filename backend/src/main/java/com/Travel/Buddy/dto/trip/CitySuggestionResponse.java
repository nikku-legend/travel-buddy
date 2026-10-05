package com.Travel.Buddy.dto.trip;

import java.math.BigDecimal;

/**
 * A city the engine proposes for a trip. (SRS 2.3 TP-06)
 *
 * <p>Every suggestion carries the reason it was made, because
 * section 4.1 requires the engine to be explainable rather than a
 * black box.
 */
public record CitySuggestionResponse(

        Long cityId,

        String name,

        String stateName,

        BigDecimal latitude,

        BigDecimal longitude,

        int placesInScope,

        String reason
) {
}