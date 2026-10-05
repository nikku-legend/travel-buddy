package com.Travel.Buddy.entity;

/**
 * Where a dispute is in its lifecycle. (FR-28)
 *
 * <pre>
 *   OPEN -> UNDER_REVIEW -> RESOLVED | REJECTED
 *     |          |
 *     |          +-> AWAITING_PARTNER_RESPONSE -> UNDER_REVIEW
 *     |
 *     +-> WITHDRAWN
 * </pre>
 *
 * <p>Three states are terminal. Nothing leaves them: a resolved
 * or rejected dispute is a record of a ruling, and reopening it
 * would silently rewrite the money that was paid out.
 */
public enum DisputeStatus {

    OPEN,

    UNDER_REVIEW,

    AWAITING_PARTNER_RESPONSE,

    RESOLVED,

    REJECTED,

    WITHDRAWN;

    public boolean isTerminal() {
        return this == RESOLVED
                || this == REJECTED
                || this == WITHDRAWN;
    }

    /**
     * A claimant can only walk away while nobody has invested
     * effort yet. Once a moderator has picked it up, withdrawing
     * would discard their work and the trail would imply the
     * complaint was answered.
     */
    public boolean isWithdrawableByClaimant() {
        return this == OPEN;
    }

    /**
     * True while the dispute is still capable of changing, which
     * is what gates evidence uploads and claimant comments.
     */
    public boolean isOpen() {
        return !isTerminal();
    }
}