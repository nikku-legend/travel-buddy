package com.Travel.Buddy.dto.stay;

import com.Travel.Buddy.entity.PhysicalRoomStatus;

import java.time.LocalDate;

/**
 * A physical room in the partner's room list. (FR-22)
 *
 * <p>{@code occupied} is computed for the requested window so the
 * front desk sees which rooms are actually free tonight, not just
 * which ones exist.
 */
public record PhysicalRoomResponse(
        Long physicalRoomId,
        Long propertyId,
        Long roomTypeId,
        String categoryName,
        String roomNumber,
        String floorLabel,
        PhysicalRoomStatus status,
        String notes,
        boolean assignable,
        boolean occupiedInWindow
) {
}