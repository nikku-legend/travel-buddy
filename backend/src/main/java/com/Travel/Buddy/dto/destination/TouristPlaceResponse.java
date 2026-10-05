package com.Travel.Buddy.dto.destination;

import java.math.BigDecimal;

public record TouristPlaceResponse(

        Long placeId,

        Long stateId,

        String name,

        String description,

        BigDecimal entryFee,

        String currency,

        String imageUrl,

        BigDecimal latitude,

        BigDecimal longitude,

        Boolean active

) {
}
