package com.Travel.Buddy.service.notification;

import com.Travel.Buddy.dto.notification.NotificationPageResponse;
import com.Travel.Buddy.dto.notification.NotificationResponse;
import com.Travel.Buddy.entity.Notification;
import com.Travel.Buddy.entity.NotificationChannel;
import com.Travel.Buddy.entity.NotificationDeliveryStatus;
import com.Travel.Buddy.entity.NotificationType;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.NotificationRepository;
import com.Travel.Buddy.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Notification delivery and inbox. (FR-25)
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(
            NotificationService.class
    );

    /**
     * Hard ceiling on page size. Without it a client asking for
     * size=100000 would page the entire table.
     */
    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailGateway emailGateway;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            EmailGateway emailGateway
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.emailGateway = emailGateway;
    }

    // ------------------------------------------------------------------
    // Sending
    // ------------------------------------------------------------------

    /**
     * Records an in-app notification.
     *
     * <p>Deliberately returns null instead of throwing when a
     * duplicate arrives. Events are retried in normal operation,
     * and a duplicate notification is never a reason to fail the
     * booking, payment or approval that triggered it.
     *
     * <p><strong>Joins the caller's transaction</strong> rather
     * than opening its own. That is a correctness requirement, not
     * a style choice:
     *
     * <ul>
     *   <li>With a separate transaction a notification would
     *       outlive a business rollback, so a user could be told
     *       their property was approved by a transaction that
     *       subsequently failed.</li>
     *   <li>It also could not see rows the business transaction
     *       has written but not yet committed, so notifying about
     *       a just-created booking would fail to find the
     *       traveller.</li>
     * </ul>
     *
     * @param dedupeKey idempotency key, or null to always insert
     */
    @Transactional
    public Notification notify(
            Long userId,
            NotificationType type,
            String title,
            String body,
            String relatedType,
            Long relatedId,
            String actionUrl,
            String dedupeKey
    ) {
        if (dedupeKey != null
                && notificationRepository.existsByDedupeKey(dedupeKey)) {
            log.debug(
                    "Suppressed duplicate notification for user {} key {}",
                    userId, dedupeKey
            );
            return null;
        }

        User user = userRepository.findById(userId)
                .orElse(null);

        if (user == null) {
            /*
             * Not an error worth failing the caller over. These
             * notifications are raised from inside approvals and
             * bookings, and a deleted or not-yet-visible recipient
             * must never undo the business change that triggered
             * the message.
             */
            log.warn(
                    "Skipped notification for unknown user {} "
                            + "(type {})",
                    userId, type
            );
            return null;
        }

        Notification n = new Notification(user, type, title);
        n.setBody(body);
        n.setRelatedType(relatedType);
        n.setRelatedId(relatedId);
        n.setActionUrl(actionUrl);
        n.setDedupeKey(dedupeKey);
        n.setChannel(NotificationChannel.IN_APP);
        n.setDeliveryStatus(NotificationDeliveryStatus.SENT);
        n.markSent();

        try {
            return notificationRepository.saveAndFlush(n);
        } catch (DataIntegrityViolationException e) {
            /*
             * The pre-check above already covers every ordinary
             * duplicate. Reaching here means two threads raced, and
             * the unique index did its job.
             *
             * The exception is rethrown rather than swallowed: the
             * notification now shares the caller's transaction, so
             * a constraint violation has already poisoned it and
             * the business change cannot commit either way.
             */
            log.warn(
                    "Concurrent duplicate notification for user {} "
                            + "key {}",
                    userId, dedupeKey
            );
            throw e;
        }
    }

    /**
     * Records the notification and also emails it.
     *
     * <p>Separate from {@link #notify} so transactional receipts
     * that should never be emailed do not have to remember to opt
     * out.
     */
    @Transactional
    public Notification notifyAndEmail(
            Long userId,
            NotificationType type,
            String title,
            String body,
            String relatedType,
            Long relatedId,
            String actionUrl,
            String dedupeKey
    ) {
        Notification inApp = notify(
                userId, type, title, body, relatedType, relatedId,
                actionUrl, dedupeKey
        );

        if (inApp == null || !type.isEmailWorthy()) {
            return inApp;
        }

        User user = inApp.getUser();
        try {
            boolean sent = emailGateway.send(user, inApp);
            if (sent) {
                inApp.markSent();
            } else {
                inApp.markFailed("Email gateway rejected the message");
            }
        } catch (RuntimeException e) {
            // The in-app copy is already saved, so a broken
            // mail server must not undo the notification.
            log.warn(
                    "Email delivery failed for notification {}: {}",
                    inApp.getNotificationId(), e.getMessage()
            );
            inApp.markFailed(e.getMessage());
        }
        return inApp;
    }

    // ------------------------------------------------------------------
    // Inbox
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public NotificationPageResponse myNotifications(
            Long userId,
            int page,
            int size,
            boolean unreadOnly
    ) {
        int safeSize = Math.min(
                Math.max(size, 1), MAX_PAGE_SIZE
        );
        int safePage = Math.max(page, 0);

        Page<Notification> result = unreadOnly
                ? notificationRepository.findUnread(
                        userId,
                        PageRequest.of(safePage, safeSize)
                )
                : notificationRepository
                        .findByUser_UserIdOrderByCreatedAtDescNotificationIdDesc(
                                userId,
                                PageRequest.of(safePage, safeSize)
                        );

        List<NotificationResponse> content = result.getContent()
                .stream()
                .map(NotificationResponse::from)
                .toList();

        return new NotificationPageResponse(
                content,
                safePage,
                safeSize,
                result.getTotalElements(),
                result.getTotalPages(),
                notificationRepository.countUnread(userId)
        );
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countUnread(userId);
    }

    /**
     * @return how many were actually marked, so the client can
     * avoid re-rendering unchanged cards
     */
    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId);
    }

    @Transactional
    public NotificationResponse markRead(
            Long userId, Long notificationId
    ) {
        Notification n = notificationRepository
                .findByNotificationIdAndUser_UserId(
                        notificationId, userId
                )
                .orElseThrow(() -> PartnerApplicationException.notFound(
                        "Notification not found: " + notificationId
                ));

        n.markRead();
        return NotificationResponse.from(
                notificationRepository.save(n)
        );
    }
}