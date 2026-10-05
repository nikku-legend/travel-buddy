package com.Travel.Buddy.entity;

/**
 * Why a dispute was raised. (FR-28)
 *
 * <p>Free text would be easier for a claimant but useless for
 * triage: an admin working a queue of forty claims needs to see
 * that one of them is a safety concern.
 */
public enum DisputeCategory {

    SERVICE_QUALITY,

    PROPERTY_NOT_AS_DESCRIBED,

    OVERCHARGING,

    /*
     * Separated from the rest deliberately. A safety concern is
     * the one category that may need action before any money is
     * discussed, so it is never merged into a generic quality
     * bucket where it would be lost.
     */
    SAFETY_CONCERN,

    CLEANLINESS,

    BOOKING_NOT_HONOURED,

    LATE_CHECK_IN,

    UNAUTHORISED_CANCELLATION,

    OTHER
}