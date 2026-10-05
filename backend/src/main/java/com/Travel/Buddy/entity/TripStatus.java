package com.Travel.Buddy.entity;

/**
 * Trip lifecycle. (SRS 2.2 section 11)
 *
 * <pre>
 *   DRAFT -> PLANNING -> READY_FOR_CHECKOUT -> CONFIRMED
 *         -> IN_PROGRESS -> COMPLETED -> REVIEW_OPEN -> CLOSED
 * </pre>
 *
 * <p>Planning state and travel state are deliberately separate.
 * A traveller editing an itinerary months from departure is in
 * PLANNING; a confirmed trip that has not started is CONFIRMED.
 * Collapsing them would make "can I still edit this?" ambiguous
 * for every UI screen.
 *
 * <p>The final state before CLOSED is REVIEW_OPEN, not
 * COMPLETED: the post-trip review window (TP-12) outlives the
 * stay itself.
 */
public enum TripStatus {

    DRAFT,

    PLANNING,

    READY_FOR_CHECKOUT,

    CONFIRMED,

    IN_PROGRESS,

    COMPLETED,

    REVIEW_OPEN,

    CLOSED;

    /**
     * Whether itinerary changes are still permitted.
     *
     * <p>Closed once payment is pending. Editing a cart that is
     * mid-payment would change the bill after the traveller
     * authorised a figure.
     */
    public boolean isEditable() {
        return this == DRAFT
                || this == PLANNING
                || this == READY_FOR_CHECKOUT;
    }

    public boolean isFinal() {
        return this == CLOSED;
    }
}