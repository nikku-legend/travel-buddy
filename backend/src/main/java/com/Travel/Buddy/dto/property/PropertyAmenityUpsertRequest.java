package com.Travel.Buddy.dto.property;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Attaches a catalogue amenity to a property. (SRS 2.3 section 6.2)
 *
 * <p>The code must already exist in the catalogue. A partner picks
 * from what the platform defines rather than typing their own, which
 * is what stops "free wifi" and "complimentary WiFi" from becoming
 * two amenities that no search can match.
 */
public record PropertyAmenityUpsertRequest(

        @NotBlank
        @Size(max = 40)
        String amenityCode,

        Boolean free,

        Boolean requiresBooking,

        @Size(max = 200)
        String note
) {
}