package com.Travel.Buddy.dto.stay;

import jakarta.validation.constraints.NotNull;

/**
 * Assigns a specific physical room to a booking. (FR-22)
 */
public record RoomAssignmentRequest(

        @NotNull(message = "A physical room is required")
        Long physicalRoomId
) {
}