package com.Travel.Buddy.dto.admin;

import java.math.BigDecimal;

/**
 * Create/update payload for the destination admin tools. (FR-32)
 *
 * <p>Null means "leave as is" on update, so a focused edit (say,
 * flipping {@code active}) cannot blank the description it did
 * not mention. On create the service requires stateId, name and
 * currency outright.
 */
public record AdminDestinationRequest(

        Long stateId,

        Long cityId,

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