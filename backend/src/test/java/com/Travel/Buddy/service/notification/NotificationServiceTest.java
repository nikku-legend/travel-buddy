package com.Travel.Buddy.service.notification;

import com.Travel.Buddy.dto.notification.NotificationPageResponse;
import com.Travel.Buddy.dto.notification.NotificationResponse;
import com.Travel.Buddy.entity.Notification;
import com.Travel.Buddy.entity.NotificationDeliveryStatus;
import com.Travel.Buddy.entity.NotificationType;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.NotificationRepository;
import com.Travel.Buddy.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-app notification inbox. (FR-25)
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Notifications (FR-25)")
class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    private Long travellerId;

    private Long otherTravellerId;

    @BeforeEach
    void setUp() {
        travellerId = createUser().getUserId();
        otherTravellerId = createUser().getUserId();
    }

    private User createUser() {
        User user = new User();
        user.setFullName("Notification Tester");
        user.setEmail(UUID.randomUUID() + "@tb.local");
        user.setPasswordHash("{noop}password");
        return userRepository.save(user);
    }

    private Notification send(
            Long userId,
            NotificationType type,
            String title,
            String dedupeKey
    ) {
        return notificationService.notify(
                userId, type, title, "body",
                "BOOKING", 1L, "/bookings/1", dedupeKey
        );
    }

    /* ============================================================
     * DELIVERY
     * ============================================================ */

    @Test
    @DisplayName("a notification is stored unread and delivered")
    void storesNotification() {
        Notification n = send(
                travellerId,
                NotificationType.BOOKING_CONFIRMED,
                "Your booking is confirmed",
                "test:store"
        );

        assertNotNull(n);
        assertEquals(
                NotificationDeliveryStatus.SENT,
                n.getDeliveryStatus()
        );
        assertNull(n.getReadAt());
        assertEquals(
                1L, notificationService.unreadCount(travellerId)
        );
    }

    /**
     * The reason dedupe_key exists. A retried payment webhook or a
     * double-clicked approval must not produce a second message.
     */
    @Test
    @DisplayName("the same event twice notifies once")
    void duplicateEventsAreSuppressed() {
        String key = "test:dupe:" + UUID.randomUUID();

        Notification first = send(
                travellerId,
                NotificationType.BOOKING_CONFIRMED,
                "Confirmed", key
        );
        Notification second = send(
                travellerId,
                NotificationType.BOOKING_CONFIRMED,
                "Confirmed", key
        );

        assertNotNull(first);
        assertNull(second, "duplicate must be suppressed");
        assertEquals(
                1L, notificationService.unreadCount(travellerId)
        );
    }

    @Test
    @DisplayName("a null dedupe key always notifies")
    void repeatableEventsAlwaysNotify() {
        send(travellerId, NotificationType.CHECK_IN_REMINDER,
                "Check-in tomorrow", null);
        send(travellerId, NotificationType.CHECK_IN_REMINDER,
                "Check-in tomorrow", null);

        assertEquals(
                2L, notificationService.unreadCount(travellerId)
        );
    }

    /**
     * A notification is a side effect of someone else's business
     * decision. It must never be the reason that decision fails.
     */
    @Test
    @DisplayName("an unknown recipient is skipped, not thrown")
    void unknownRecipientIsSkipped() {
        Notification n = send(
                999_999_999L,
                NotificationType.PROPERTY_APPROVED,
                "Approved", "test:missing"
        );

        assertNull(n);
    }

    /* ============================================================
     * INBOX
     * ============================================================ */

    @Test
    @DisplayName("a traveller only sees their own notifications")
    void inboxIsScopedToTheOwner() {
        send(travellerId, NotificationType.BOOKING_CONFIRMED,
                "Mine", "test:scope:a");
        send(otherTravellerId, NotificationType.BOOKING_CONFIRMED,
                "Theirs", "test:scope:b");

        NotificationPageResponse page =
                notificationService.myNotifications(
                        travellerId, 0, 20, false
                );

        assertEquals(1, page.content().size());
        assertEquals(
                "Mine", page.content().get(0).title()
        );
    }

    @Test
    @DisplayName("the page reports the unread count with the list")
    void pageCarriesUnreadCount() {
        send(travellerId, NotificationType.BOOKING_CONFIRMED,
                "A", "test:page:a");
        send(travellerId, NotificationType.PAYMENT_FAILED,
                "B", "test:page:b");

        NotificationPageResponse page =
                notificationService.myNotifications(
                        travellerId, 0, 20, false
                );

        assertEquals(2, page.totalElements());
        assertEquals(2L, page.unreadCount());
    }

    @Test
    @DisplayName("marking one read leaves the rest unread")
    void markingOneRead() {
        Notification first = send(
                travellerId, NotificationType.BOOKING_CONFIRMED,
                "A", "test:one:a"
        );
        send(travellerId, NotificationType.PAYMENT_FAILED,
                "B", "test:one:b");

        NotificationResponse updated = notificationService
                .markRead(travellerId, first.getNotificationId());

        assertTrue(updated.read());
        assertNotNull(updated.readAt());
        assertEquals(
                1L, notificationService.unreadCount(travellerId)
        );
    }

    @Test
    @DisplayName("reading the same notification twice is harmless")
    void markingTwiceKeepsTheOriginalTimestamp() {
        Notification n = send(
                travellerId, NotificationType.BOOKING_CONFIRMED,
                "A", "test:twice"
        );

        NotificationResponse first =
                notificationService.markRead(
                        travellerId, n.getNotificationId()
                );
        NotificationResponse second =
                notificationService.markRead(
                        travellerId, n.getNotificationId()
                );

        /*
         * Compared directly, with no tolerance. The entity stores
         * exactly the microsecond value the column holds, so an
         * in-memory response and a reloaded row must agree
         * precisely. A tolerance here would hide a real precision
         * bug.
         */
        assertEquals(first.readAt(), second.readAt());
    }

    @Test
    @DisplayName("mark all read clears the badge")
    void markingAllRead() {
        send(travellerId, NotificationType.BOOKING_CONFIRMED,
                "A", "test:all:a");
        send(travellerId, NotificationType.PAYMENT_FAILED,
                "B", "test:all:b");

        assertEquals(
                2, notificationService.markAllRead(travellerId)
        );
        assertEquals(
                0L, notificationService.unreadCount(travellerId)
        );
        assertEquals(
                0, notificationService.markAllRead(travellerId),
                "a second sweep has nothing left to update"
        );
    }

    @Test
    @DisplayName("mark all read does not touch another user's inbox")
    void markingAllReadIsScoped() {
        send(travellerId, NotificationType.BOOKING_CONFIRMED,
                "Mine", "test:allscoped:a");
        send(otherTravellerId, NotificationType.BOOKING_CONFIRMED,
                "Theirs", "test:allscoped:b");

        notificationService.markAllRead(travellerId);

        assertEquals(
                1L, notificationService.unreadCount(otherTravellerId)
        );
    }

    @Test
    @DisplayName("the unread filter hides read notifications")
    void unreadFilter() {
        Notification n = send(
                travellerId, NotificationType.BOOKING_CONFIRMED,
                "A", "test:filter:a"
        );
        send(travellerId, NotificationType.PAYMENT_FAILED,
                "B", "test:filter:b");

        notificationService.markRead(
                travellerId, n.getNotificationId()
        );

        NotificationPageResponse page =
                notificationService.myNotifications(
                        travellerId, 0, 20, true
                );

        assertEquals(1, page.content().size());
        assertEquals("B", page.content().get(0).title());
    }

    /* ============================================================
     * OWNERSHIP
     * ============================================================ */

    /**
     * A guessed notification id must not let one user read or
     * dismiss another user's message.
     */
    @Test
    @DisplayName("a traveller cannot read someone else's notification")
    void cannotReadAnotherUsersNotification() {
        Notification n = send(
                otherTravellerId, NotificationType.BOOKING_CONFIRMED,
                "Theirs", "test:steal"
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> notificationService.markRead(
                        travellerId, n.getNotificationId()
                )
        );
        assertEquals(
                1L, notificationService.unreadCount(otherTravellerId),
                "the owner's unread state must be untouched"
        );
    }

    @Test
    @DisplayName("an oversized page request is capped")
    void pageSizeIsCapped() {
        NotificationPageResponse page =
                notificationService.myNotifications(
                        travellerId, 0, 100_000, false
                );

        assertTrue(page.size() <= 50);
    }

    @Test
    @DisplayName("a negative page is treated as the first page")
    void negativePageIsClamped() {
        NotificationPageResponse page =
                notificationService.myNotifications(
                        travellerId, -5, 20, false
                );

        assertEquals(0, page.page());
    }

    /* ============================================================
     * EMAIL
     * ============================================================ */

    @Test
    @DisplayName("only email-worthy events are emailed")
    void onlyImportantEventsAreEmailed() {
        notificationService.notifyAndEmail(
                travellerId, NotificationType.PROPERTY_APPROVED,
                "Live", "body", "PROPERTY", 1L, "/p/1",
                "test:email:approved"
        );
        notificationService.notifyAndEmail(
                travellerId, NotificationType.BOOKING_CONFIRMED,
                "Confirmed", "body", "BOOKING", 1L, "/b/1",
                "test:email:confirmed"
        );

        assertTrue(NotificationType.PROPERTY_APPROVED.isEmailWorthy());
        assertFalse(
                NotificationType.BOOKING_CONFIRMED.isEmailWorthy(),
                "a receipt the booking page already shows should not "
                        + "be emailed"
        );
    }

    @Test
    @DisplayName("a broken email gateway does not lose the in-app copy")
    void emailFailureDoesNotLoseTheNotification() {
        NotificationService service = new NotificationService(
                notificationRepository,
                userRepository,
                (to, notification) -> {
                    throw new IllegalStateException(
                            "SMTP is down"
                    );
                }
        );

        Notification n = service.notifyAndEmail(
                travellerId, NotificationType.PROPERTY_APPROVED,
                "Live", "body", "PROPERTY", 1L, "/p/1",
                "test:email:down"
        );

        assertNotNull(n);
        assertEquals(
                NotificationDeliveryStatus.FAILED,
                n.getDeliveryStatus()
        );
        assertNull(n.getReadAt(),
                "the traveller must still see it in the app");
    }

    @Test
    @DisplayName("the list is newest first")
    void newestFirst() {
        send(travellerId, NotificationType.BOOKING_CONFIRMED,
                "Oldest", "test:order:a");
        Notification second = send(
                travellerId, NotificationType.PAYMENT_FAILED,
                "Newest", "test:order:b"
        );

        List<NotificationResponse> content =
                notificationService.myNotifications(
                                travellerId, 0, 20, false
                        )
                        .content();

        assertEquals(
                second.getNotificationId(),
                content.get(0).notificationId()
        );
    }
}