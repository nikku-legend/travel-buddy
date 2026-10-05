package com.Travel.Buddy.dto.notification;

import java.util.List;

/**
 * Page of notifications plus the unread count in one round trip.
 *
 * <p>The badge and the list always render together, so shipping
 * the count inside the page response saves a request per
 * notification open.
 */
public record NotificationPageResponse(

        List<NotificationResponse> content,

        int page,

        int size,

        long totalElements,

        int totalPages,

        long unreadCount
) {
}