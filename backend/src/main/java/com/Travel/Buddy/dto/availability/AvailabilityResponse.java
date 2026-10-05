package com.Travel.Buddy.dto.availability;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AvailabilityResponse(

        Long propertyId,

        Long roomTypeId,

        String categoryName,

        Integer maxOccupancy,

        BigDecimal pricePerNight,

        String currency,

        Integer roomsRequested,

        Integer availableRooms,

        Integer totalInventory,

        Integer numberOfNights,

        BigDecimal totalPrice,

        LocalDate checkIn,

        LocalDate checkOut,

        Integer guests,

        List<DailyAvailabilityResponse> dailyAvailability

) {
}
