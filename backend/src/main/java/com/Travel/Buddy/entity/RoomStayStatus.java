package com.Travel.Buddy.entity;

/**
 * Lifecycle of one guest in one physical room. (FR-22, FR-23)
 *
 * <pre>
 *   ASSIGNED    room allocated ahead of arrival
 *       |
 *       +---- guest never turns up ----> NO_SHOW
 *       |
 *       +---- guest arrives ----------> CHECKED_IN
 *       |
 *       +---- guest departs ---------> CHECKED_OUT
 *
 *   CANCELLED   assignment withdrawn before arrival
 * </pre>
 */
public enum RoomStayStatus {

    ASSIGNED,

    CHECKED_IN,

    CHECKED_OUT,

    CANCELLED,

    NO_SHOW;

    /**
     * Only these still occupy the room and therefore still block a
     * competing assignment for the same dates.
     */
    public boolean occupiesRoom() {
        return this == ASSIGNED
                || this == CHECKED_IN;
    }

    public boolean isOpen() {
        return this == ASSIGNED
                || this == CHECKED_IN;
    }
}