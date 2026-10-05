package com.Travel.Buddy.dto.property;

/**
 * A catalogue entry for the partner's amenity picker.
 *
 * <p>Read-only on purpose: the catalogue is platform reference
 * data, so a partner chooses a code here and never mints one.
 */
public record AmenitySummary(
        Integer amenityId,
        String code,
        String label,
        String category,
        String iconName
) {
}