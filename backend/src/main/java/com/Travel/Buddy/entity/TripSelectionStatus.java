package com.Travel.Buddy.entity;

/**
 * How far a selection has progressed. (SRS 2.2 section 11)
 *
 * <pre>
 *   SELECTED -> REVALIDATING -> READY_FOR_CHECKOUT
 *                                  |
 *                    +-------------+-------------+
 *                    |             |             |
 *                 BOOKED    UNAVAILABLE     REMOVED
 * </pre>
 *
 * <p>None of these is a reservation. Inventory is only taken in
 * TripCheckoutService, and only at the moment payment is created.
 */
public enum TripSelectionStatus {

    SELECTED,

    REVALIDATING,

    READY_FOR_CHECKOUT,

    BOOKED,

    /*
     * Checkout found it unsellable. Kept rather than deleted so
     * the traveller can see which line item failed and why,
     * which section 6 requires before asking them to re-confirm.
     */
    UNAVAILABLE,

    /* The traveller removed it themselves. */
    REMOVED;

    public boolean countsTowardBill() {
        return this == SELECTED
                || this == REVALIDATING
                || this == READY_FOR_CHECKOUT
                || this == BOOKED;
    }
}