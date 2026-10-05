package com.Travel.Buddy.dto.inventory;

import com.Travel.Buddy.entity.RoomBlockReason;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A partner's view of one blocked night. (FR-21)
 */
public record RoomBlockResponse(
        Long blockId,
        Long propertyId,
        Long roomTypeId,
        String categoryName,
        LocalDate blockedDate,
        Integer roomsBlocked,
        RoomBlockReason reason,
        String notes,
        String createdByName,
        LocalDateTime createdAt,
        boolean active,
        LocalDateTime releasedAt
) {
}