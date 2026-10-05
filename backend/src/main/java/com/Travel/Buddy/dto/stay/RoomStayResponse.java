package com.Travel.Buddy.dto.stay;

import com.Travel.Buddy.entity.RoomStayStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A guest's occupancy of one physical room. (FR-22, FR-23)
 */
public record RoomStayResponse(
        Long stayId,
        Long bookingId,
        String bookingReference,
        Long physicalRoomId,
        String roomNumber,
        String floorLabel,
        Long roomTypeId,
        String categoryName,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        RoomStayStatus status,
        String guestName,
        LocalDateTime checkedInAt,
        LocalDateTime checkedOutAt,
        String notes,
        boolean canCheckIn,
        boolean canCheckOut,
        String blockedReason
) {
}