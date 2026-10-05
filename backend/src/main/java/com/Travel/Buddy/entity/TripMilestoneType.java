package com.Travel.Buddy.entity;

/**
 * A checkpoint on the journey map. (SRS 2.2 TP-11, section 15)
 *
 * <p>Section 15 is explicit that the map is driven by these rows
 * rather than hard-coded progress, so a checkpoint is only
 * completed when something real happened: a payment cleared, a
 * guest actually checked in.
 */
public enum TripMilestoneType {

    TRIP_STARTED,

    CITY_ARRIVED,

    CITY_DEPARTED,

    CHECKED_IN,

    CHECKED_OUT,

    TRIP_COMPLETED,

    REVIEW_OPENED
}