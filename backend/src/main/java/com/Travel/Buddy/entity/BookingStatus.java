package com.Travel.Buddy.entity;

public enum BookingStatus {

    PENDING,

    CONFIRMED,

    /**
     * The guest has arrived and been given a physical room.
     * (FR-23)
     */
    CHECKED_IN,

    CANCELLED,

    COMPLETED,

    /**
     * The guest never arrived and did not cancel. (FR-23)
     */
    NO_SHOW
}