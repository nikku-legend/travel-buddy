package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.notification.NotificationPageResponse;
import com.Travel.Buddy.dto.notification.NotificationResponse;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.notification.NotificationService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * In-app notification inbox. (FR-25)
 *
 * <p>Every method is scoped to the authenticated principal, so
 * there is no route through this controller to read or dismiss
 * another user's notifications.
 */
@RestController
@RequestMapping("/api/v1/notifications")

public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(
            NotificationService notificationService,
            UserRepository userRepository
    ) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<NotificationPageResponse> myNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly
    ) {
        return ResponseEntity.ok(
                notificationService.myNotifications(
                        currentUserId(authentication),
                        page,
                        size,
                        unreadOnly
                )
        );
    }

    /**
     * Separate lightweight endpoint so the navbar badge can poll
     * without downloading notification bodies.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                Map.of(
                        "unreadCount",
                        notificationService.unreadCount(
                                currentUserId(authentication)
                        )
                )
        );
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markRead(
            Authentication authentication,
            @PathVariable Long notificationId
    ) {
        return ResponseEntity.ok(
                notificationService.markRead(
                        currentUserId(authentication),
                        notificationId
                )
        );
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                Map.of(
                        "updated",
                        notificationService.markAllRead(
                                currentUserId(authentication)
                        )
                )
        );
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new AccessDeniedException(
                    "Authentication is required"
            );
        }
        return userRepository.findByEmail(
                        authentication.getName()
                )
                .map(User::getUserId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: "
                                + authentication.getName()
                ));
    }
}