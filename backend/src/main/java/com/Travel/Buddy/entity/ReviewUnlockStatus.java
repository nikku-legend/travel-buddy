package com.Travel.Buddy.entity;

/**
 * Post-trip review eligibility. (SRS 2.3 TP-12, section 11)
 *
 * <pre>
 *   LOCKED -> ELIGIBLE -> SUBMITTED -> PUBLISHED
 *                                        |
 *                                     FLAGGED
 * </pre>
 *
 * <p>The SRS states plainly that "eligibility is booking-based": a
 * service becomes reviewable only once its own booking reached
 * COMPLETED, never because the trip's end date passed.
 *
 * <p>FLAGGED is reachable from SUBMITTED rather than from ELIGIBLE,
 * because moderation can only ever apply to a review that exists.
 * A flagged review can still be rejected outright, in which case
 * ReviewService withdraws it and the traveller may review again.
 */
public enum ReviewUnlockStatus {

    /**
     * The trip is booked but this service has not completed. No card
     * is shown. This is the resting state for most of the trip.
     */
    LOCKED,

    /** The service completed and the traveller may review it now. */
    ELIGIBLE,

    /** Written but not yet moderated. */
    SUBMITTED,

    /** Moderated and visible on the public listing. */
    PUBLISHED,

    /** Moderated and pulled for attention. */
    FLAGGED;

    /** Whether the Review Center should offer a review form. */
    public boolean acceptsReview() {
        return this == ELIGIBLE || this == FLAGGED;
    }
}