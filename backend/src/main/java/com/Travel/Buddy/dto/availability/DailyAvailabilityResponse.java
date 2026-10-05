package com.Travel.Buddy.dto.availability;

import java.time.LocalDate;

public record DailyAvailabilityResponse(

        LocalDate inventoryDate,

        Integer totalInventory,

        Integer reservedRooms,

        Integer availableInventory

) {
}