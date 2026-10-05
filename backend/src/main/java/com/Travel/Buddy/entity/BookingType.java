package com.Travel.Buddy.entity;

/**
 * What a booking is for. (SRS 2.2 TP-10, FR-34)
 *
 * <p>Recorded on the booking rather than inferred from whichever
 * child table happens to reference it. Commission, settlement and
 * refunds all ask "of what" before they can answer anything, and
 * the rate differs per service, so the type cannot be a detail
 * worked out later.
 */
public enum BookingType {

    HOTEL,

    GUIDE,

    CAB
}