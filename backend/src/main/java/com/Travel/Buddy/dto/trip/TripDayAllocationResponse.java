package com.Travel.Buddy.dto.trip;

import java.time.LocalDate;
import java.util.List;

/**
 * Advisory distribution of trip nights across the traveller's city stops.
 */
public record TripDayAllocationResponse(
        int tripNights,
        int cityStopCount,
        boolean advisoryOnly,
        List<String> notes,
        List<CityAllocation> cityAllocations
) {
    public record CityAllocation(
            Long tripCityId,
            Long cityId,
            String cityName,
            int sequence,
            int selectedPlaceCount,
            int suggestedNights,
            LocalDate suggestedArrivalDate,
            LocalDate suggestedDepartureDate,
            LocalDate currentArrivalDate,
            LocalDate currentDepartureDate,
            String rationale
    ) {
    }
}
