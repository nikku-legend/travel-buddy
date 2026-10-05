package com.Travel.Buddy.entity;

/**
 * Every event that can raise a notification. (FR-25)
 *
 * <p>Mirrors the event list in the SRS: registration, booking,
 * payment, cancellation, refund, guide and transport, check-in
 * reminder, review reminder, partner approval and KYC result.
 *
 * <p>The enum is stored as a string rather than a code so the
 * database stays readable when tracing why a user was told
 * something.
 */
public enum NotificationType {

    WELCOME,

    PARTNER_APPLICATION_SUBMITTED,

    PARTNER_APPLICATION_APPROVED,

    PARTNER_APPLICATION_REJECTED,

    KYC_DOCUMENT_REJECTED,

    PROPERTY_SUBMITTED,

    PROPERTY_APPROVED,

    PROPERTY_REJECTED,

    PROPERTY_SUSPENDED,

    BOOKING_CONFIRMED,

    PAYMENT_SUCCESS,

    PAYMENT_FAILED,

    BOOKING_CANCELLED,

    REFUND_PROCESSED,

    CHECK_IN_REMINDER,

    REVIEW_SUBMITTED,

    REVIEW_PUBLISHED,

    REVIEW_REJECTED,

    GUIDE_BOOKING,

    TRANSPORT_ASSIGNED,

    DISPUTE_RAISED,

    DISPUTE_RESOLVED;

    /**
     * Partner and traveller news is worth an email. Transactional
     * receipts are not: the booking page already shows them, and
     * emailing them trains people to ignore the inbox.
     */
    public boolean isEmailWorthy() {
        return this == PARTNER_APPLICATION_APPROVED
                || this == PARTNER_APPLICATION_REJECTED
                || this == KYC_DOCUMENT_REJECTED
                || this == PROPERTY_APPROVED
                || this == PROPERTY_REJECTED
                || this == PROPERTY_SUSPENDED
                || this == PAYMENT_FAILED
                || this == REFUND_PROCESSED
                || this == DISPUTE_RESOLVED;
    }

    /**
     * Moderation and submission events that only admins need to
     * see queue work rather than chasing a bell.
     */
    public boolean isActionableByUser() {
        return this != PARTNER_APPLICATION_SUBMITTED
                && this != PROPERTY_SUBMITTED
                && this != REVIEW_SUBMITTED;
    }
}