package com.Travel.Buddy.dto.room;

import java.math.BigDecimal;

/**
 * Partner-facing room type view. (FR-05)
 *
 * <p>Includes {@code inventoryDays} so the partner portal can warn when
 * a room is listed but has no generated availability, which is the
 * difference between "bookable" and "invisible to search".
 */
public record PartnerRoomTypeResponse(
        Long roomTypeId,
        Long propertyId,
        String propertyName,
        String categoryName,
        Integer maxOccupancy,
        BigDecimal basePrice,
        String currency,
        Integer totalInventory,
        boolean active,
        long inventoryDays,
        boolean hasAvailability,
        boolean canEdit,
        String cannotEditReason
) {
}