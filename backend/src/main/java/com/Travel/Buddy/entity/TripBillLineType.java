package com.Travel.Buddy.entity;

/**
 * A line on the itemized trip bill. (SRS 2.2 section 5.1)
 *
 * <p>TAX, FEE and DISCOUNT are line types rather than columns so
 * they appear in the itemized breakdown the traveller reads, and
 * so the arithmetic that produces the total is visible rather
 * than implied.
 */
public enum TripBillLineType {

    HOTEL,

    GUIDE,

    CAB,

    ACTIVITY,

    TAX,

    FEE,

    /* Stored as a negative amount so the total is a plain sum. */
    DISCOUNT
}