package com.Travel.Buddy.dto.notification;

import com.Travel.Buddy.entity.Notification;
import com.Travel.Buddy.entity.NotificationChannel;
import com.Travel.Buddy.entity.NotificationDeliveryStatus;
import com.Travel.Buddy.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(

        Long notificationId,

        NotificationType type,

        NotificationChannel channel,

        NotificationDeliveryStatus deliveryStatus,

        String title,

        String body,

        String relatedType,

        Long relatedId,

        String actionUrl,

        boolean read,

        LocalDateTime readAt,

        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getNotificationId(),
                n.getType(),
                n.getChannel(),
                n.getDeliveryStatus(),
                n.getTitle(),
                n.getBody(),
                n.getRelatedType(),
                n.getRelatedId(),
                n.getActionUrl(),
                n.isRead(),
                n.getReadAt(),
                n.getCreatedAt()
        );
    }
}