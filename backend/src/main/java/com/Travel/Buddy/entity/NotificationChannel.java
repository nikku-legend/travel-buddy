package com.Travel.Buddy.entity;

/**
 * Where a notification is delivered. (FR-25)
 *
 * <p>Delivery channel is separate from read state: an email can be
 * SENT while the in-app card is still unread, and a traveller who
 * reads the in-app copy has still read the notification.
 */
public enum NotificationChannel {

    IN_APP,

    EMAIL
}