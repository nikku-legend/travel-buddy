package com.Travel.Buddy.entity;

/**
 * Where a booked tour has got to. (SRS 2.2 FR-16, FR-23)
 *
 * <p>Deliberately the same shape as {@link RideStatus}: a tour and a
 * ride are both "a partner performs a service on a date and reports
 * on it", and having the guide column diverge from the cab one would
 * mean two transition rules to reason about for no gain.
 *
 * <p>Unlike {@code RideStatus} there is no DRIVER_ASSIGNED
 * equivalent, because a guide is bound to the booking when it is
 * made and there is no separate dispatch step to record.
 */
public enum GuideReservationStatus {
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
