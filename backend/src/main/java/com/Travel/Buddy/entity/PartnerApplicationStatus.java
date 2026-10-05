package com.Travel.Buddy.entity;

/**
 * Lifecycle of a partner application.
 *
 * <pre>
 *   DRAFT
 *     | submit
 *     v
 *   PENDING_REVIEW ---- admin opens ----> UNDER_REVIEW
 *     |                                      |
 *     |<------------- close -----------------|
 *     |
 *     +---- admin rejects (with reason) ----> REJECTED
 *     |                                         | applicant corrects
 *     |                                         v
 *     |                                   PENDING_CORRECTION
 *     |                                         | resubmit
 *     |                                         +----> PENDING_REVIEW
 *     |
 *     +---- admin approves ----> APPROVED   (role granted in user_roles)
 *
 *   WITHDRAWN - applicant cancelled their own application.
 * </pre>
 *
 * <p>This status is intentionally independent from the user role, so a
 * suspended or rejected partner can never be identified by role alone.
 */
public enum PartnerApplicationStatus {

    DRAFT,

    PENDING_REVIEW,

    UNDER_REVIEW,

    APPROVED,

    REJECTED,

    PENDING_CORRECTION,

    WITHDRAWN;

    /**
     * A new application may still be edited by the applicant.
     */
    public boolean isEditableByApplicant() {
        return this == DRAFT
                || this == PENDING_CORRECTION
                || this == REJECTED;
    }

    /**
     * Only these states are waiting for an admin decision.
     */
    public boolean isAwaitingReview() {
        return this == PENDING_REVIEW
                || this == UNDER_REVIEW;
    }

    /**
     * Rejected applications become PENDING_CORRECTION once the
     * applicant acknowledges the reason and starts fixing it.
     */
    public static PartnerApplicationStatus afterCorrection(
            PartnerApplicationStatus current
    ) {

        return current == REJECTED
                ? PENDING_CORRECTION
                : current;
    }
}
