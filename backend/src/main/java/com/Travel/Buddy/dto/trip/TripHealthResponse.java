package com.Travel.Buddy.dto.trip;

import java.math.BigDecimal;
import java.util.List;

/**
 * Non-mutating itinerary checks for the traveller's current plan.
 */
public record TripHealthResponse(
        boolean advisoryOnly,
        String status,
        BigDecimal budget,
        String budgetCurrency,
        BigDecimal estimatedTotal,
        String currency,
        List<Issue> issues
) {
    public record Issue(
            String code,
            String severity,
            String message,
            Long tripCityId,
            Long selectionId
    ) {
    }
}
