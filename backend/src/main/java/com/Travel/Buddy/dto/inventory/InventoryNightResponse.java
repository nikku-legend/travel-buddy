package com.Travel.Buddy.dto.inventory;

import com.Travel.Buddy.entity.RoomBlockReason;

/**
 * One night in the partner's inventory calendar. (FR-21)
 *
 * <p>Deliberately returns all three quantities so the UI can explain
 * the number instead of only colouring it red. A partner asking "why
 * is this night full?" needs to see booked, blocked and free.
 */
public record InventoryNightResponse(
        java.time.LocalDate date,
        int total,
        int reserved,
        int blocked,
        int available,
        RoomBlockReason blockReason,
        String blockNotes
) {

    public boolean isSellable() {
        return available > 0;
    }

    public boolean isFullyCommitted() {
        return available == 0 && (reserved > 0 || blocked > 0);
    }
}