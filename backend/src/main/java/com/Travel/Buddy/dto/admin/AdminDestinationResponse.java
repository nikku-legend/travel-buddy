package com.Travel.Buddy.dto.admin;

import java.math.BigDecimal;

/**
 * A destination as the admin tools show it, including inactive
 * listings the public endpoints deliberately hide. (FR-32)
 */
public record AdminDestinationResponse(

        Long placeId,

        Long stateId,

        String stateName,

        Long cityId,

        String cityName,

        String name,

        String category,

        String description,

        BigDecimal entryFee,

        String currency,

        String imageUrl,

        BigDecimal latitude,

        BigDecimal longitude,

        Boolean featured,

        Boolean active
) {
}