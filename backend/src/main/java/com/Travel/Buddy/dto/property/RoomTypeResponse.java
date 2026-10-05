package com.Travel.Buddy.dto.property;

import java.math.BigDecimal;

public record RoomTypeResponse(

        Long roomTypeId,

        Long propertyId,

        String categoryName,

        Integer maxOccupancy,

        BigDecimal basePrice,

        String currency,

        Integer totalInventory,

        Boolean active

) {
}