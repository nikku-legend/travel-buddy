package com.Travel.Buddy.entity;

/**
 * What a trip selection points at. (SRS 2.2 TP-05..TP-07)
 */
public enum TripSelectionType {

    HOTEL,

    GUIDE,

    CAB,

    /*
     * Paid attraction entry. A trip place with an entry fee is a
     * billable selection; a free one is not selected at all.
     */
    ACTIVITY
}