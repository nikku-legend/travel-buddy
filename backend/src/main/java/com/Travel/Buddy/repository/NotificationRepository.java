package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.Notification;
import com.Travel.Buddy.entity.NotificationDeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    /**
     * Newest first. Backs the notification list.
     *
     * <p>NotificationId is a tiebreak. Notifications created
     * within the same microsecond tie on the timestamp alone, and
     * with no tiebreaker "newest first" returns them in whatever
     * order the index produced, so the inbox reshuffles on
     * refresh. The id is monotonic, so it makes the order
     * deterministic.
     */
    Page<Notification> findByUser_UserIdOrderByCreatedAtDescNotificationIdDesc(
            Long userId,
            Pageable pageable
    );

    /**
     * Same list, narrowed to what the user has not seen. The
     * badge and the "Unread" tab share it.
     */
    @Query("""
            select n from Notification n
            where n.user.id = :userId and n.readAt is null
            order by n.createdAt desc, n.notificationId desc
            """)
    Page<Notification> findUnread(
            @Param("userId") Long userId,
            Pageable pageable
    );

    /**
     * Count only. Selecting the full rows to count them would
     * load every unread body just to discard it.
     */
    @Query("""
            select count(n) from Notification n
            where n.user.id = :userId and n.readAt is null
            """)
    long countUnread(@Param("userId") Long userId);

    /**
     * Scoped to the owner so a guessed id cannot let one user
     * read or dismiss another user's notifications.
     */
    Optional<Notification> findByNotificationIdAndUser_UserId(
            Long notificationId,
            Long userId
    );

    /**
     * Bulk read. One update rather than a select-modify-save per
     * row, which matters when someone taps "mark all read" on
     * hundreds of rows.
     */
    @Modifying(clearAutomatically = true,
            flushAutomatically = true)
    @Query("""
            update Notification n
               set n.readAt = current_timestamp
             where n.user.id = :userId and n.readAt is null
            """)
    int markAllRead(@Param("userId") Long userId);

    /**
     * Email outbox for the delivery worker.
     */
    List<Notification> findByChannelAndDeliveryStatusOrderByCreatedAtAsc(
            com.Travel.Buddy.entity.NotificationChannel channel,
            NotificationDeliveryStatus deliveryStatus
    );

    boolean existsByDedupeKey(String dedupeKey);
}