package com.Travel.Buddy.entity;

/**
 * Attempted delivery outcome. (FR-25)
 *
 * <p>Separate from {@code read_at} so a failed email can be retried
 * without appearing twice in the app.
 */
public enum NotificationDeliveryStatus {

    PENDING,

    SENT,

    FAILED
}