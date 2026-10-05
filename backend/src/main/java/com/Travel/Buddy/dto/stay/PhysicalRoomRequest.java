package com.Travel.Buddy.dto.stay;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Adds or renames a physical room. (FR-22)
 */
public record PhysicalRoomRequest(

        @NotBlank(message = "Room number is required")
        @Size(max = 30, message = "Room number must not exceed 30 characters")
        String roomNumber,

        @Size(max = 30, message = "Floor label must not exceed 30 characters")
        String floorLabel,

        @Size(max = 500, message = "Notes must not exceed 500 characters")
        String notes
) {
}