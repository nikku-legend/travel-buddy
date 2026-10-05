package com.Travel.Buddy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * A single notification delivered to one user. (FR-25)
 *
 * <p>Everything hangs off {@code userId} because the only common
 * query is "my notifications, newest first" and "my unread count",
 * and both are driven by a single-column index.
 */
@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(
                        name = "idx_notifications_user_created",
                        columnList = "user_id, created_at"
                ),
                @Index(
                        name = "idx_notifications_unread",
                        columnList = "user_id, read_at"
                )
        }
)
public class Notification {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long notificationId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "type",
            nullable = false
    )
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "channel",
            nullable = false
    )
    private NotificationChannel channel = NotificationChannel.IN_APP;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "delivery_status",
            nullable = false
    )
    private NotificationDeliveryStatus deliveryStatus =
            NotificationDeliveryStatus.PENDING;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 1000)
    private String body;

    /**
     * Kind of thing this is about, for deep linking. Intentionally
     * a loose string rather than a foreign key: notifications
     * outlive the rows they point at, and a hard reference would
     * either block deletes or cascade and destroy the audit trail.
     */
    @Column(name = "related_type", length = 40)
    private String relatedType;

    @Column(name = "related_id")
    private Long relatedId;

    /**
     * In-app route to open when the card is clicked.
     */
    @Column(name = "action_url", length = 300)
    private String actionUrl;

    /**
     * Idempotency key. Null for genuinely repeatable events such
     * as a daily reminder.
     */
    @Column(name = "dedupe_key", length = 120, unique = true)
    private String dedupeKey;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Notification() {
    }

    public Notification(
            User user,
            NotificationType type,
            String title
    ) {
        this.user = user;
        this.type = type;
        this.title = title;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = now();
        }
    }

    /**
     * Column precision, not an arbitrary choice.
     *
     * <p>The database column keeps microseconds. The JVM clock
     * supplies nanoseconds, and the two do not agree about
     * rounding: truncating here and letting MySQL round would make
     * the same row report two different instants depending on
     * whether it had been reloaded yet. Storing the exact value
     * the column will hold keeps a response and a later re-read
     * identical, which also makes "newest first" stable.
     */
    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    public void markRead() {
        if (readAt == null) {
            readAt = now();
        }
    }

    public void markSent() {
        this.deliveryStatus = NotificationDeliveryStatus.SENT;
        this.sentAt = now();
    }

    public void markFailed(String reason) {
        this.deliveryStatus = NotificationDeliveryStatus.FAILED;
        this.failureReason = reason;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public void setChannel(NotificationChannel channel) {
        this.channel = channel;
    }

    public NotificationDeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(
            NotificationDeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getRelatedType() {
        return relatedType;
    }

    public void setRelatedType(String relatedType) {
        this.relatedType = relatedType;
    }

    public Long getRelatedId() {
        return relatedId;
    }

    public void setRelatedId(Long relatedId) {
        this.relatedId = relatedId;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}