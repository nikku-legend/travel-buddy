package com.Travel.Buddy.entity;

/**
 * How an admin settled a dispute. (FR-28)
 *
 * <p>Kept separate from {@link DisputeStatus} on purpose. A
 * dispute can be RESOLVED with no money moving, and that is a
 * genuine outcome: the complaint was fair, the partner apologised,
 * the trip was largely as sold. Collapsing that into
 * "REJECTED" would misreport the ruling to both sides.
 */
public enum DisputeResolution {

    FULL_REFUND,

    PARTIAL_REFUND,

    NO_REFUND,

    /*
     * Offered as a goodwill credit against a future booking rather
     * than cash back. Implemented as a ruling only; the ledger
     * mechanics land with the settlement work in FR-35.
     */
    CREDIT_NOTE;

    /**
     * Whether this ruling moves money out of the platform.
     */
    public boolean involvesRefund() {
        return this == FULL_REFUND
                || this == PARTIAL_REFUND;
    }

    /**
     * A refund must be justified in writing. Money leaving the
     * platform without a stated reason is the single easiest thing
     * for a partner to dispute later.
     */
    public boolean requiresAmount() {
        return involvesRefund();
    }
}