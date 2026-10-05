package com.Travel.Buddy.entity;

/**
 * Centralized payment lifecycle. (SRS 2.2 section 6, section 11)
 *
 * <pre>
 *   CREATED -> REVALIDATING -> PAYMENT_PENDING -> PAID
 *                                                     |
 *                                          +----------+----------+
 *                                          |                     |
 *                                      CONFIRMED     RECOVERY_REQUIRED
 *                                                            |
 *                                                          FAILED
 * </pre>
 *
 * <p>RECOVERY_REQUIRED exists because payment success and
 * booking confirmation are not the same event. A Razorpay
 * callback can arrive after a timeout, or confirmation can fail
 * after the money has cleared. SRS 2.2 section 6 is explicit that
 * this state must be reconciled from persisted payment events
 * and never resolved by charging the traveller again.
 */
public enum TripCheckoutStatus {

    CREATED,

    REVALIDATING,

    PAYMENT_PENDING,

    PAID,

    CONFIRMED,

    /*
     * Money taken, bookings not all confirmed. Requires
     * reconciliation, not a retry of the charge.
     */
    RECOVERY_REQUIRED,

    FAILED;

    public boolean isOpen() {
        return this == CREATED
                || this == REVALIDATING
                || this == PAYMENT_PENDING
                || this == RECOVERY_REQUIRED;
    }

    public boolean isTerminal() {
        return this == CONFIRMED || this == FAILED;
    }
}