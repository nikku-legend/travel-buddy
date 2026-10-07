package com.Travel.Buddy.service.admin;

import com.Travel.Buddy.dto.admin.AdminSuspendRequest;
import com.Travel.Buddy.dto.admin.AdminUserHistoryResponse;
import com.Travel.Buddy.dto.admin.AdminUserResponse;
import com.Travel.Buddy.entity.Role;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.entity.UserStatus;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.BookingRepository;
import com.Travel.Buddy.repository.UserRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * User management operations. (FR-31)
 *
 * <p>Two rules keep the console from locking itself out of the
 * building: an admin cannot suspend their own account (the
 * action is one click from an unrecoverable mistake), and super
 * admin accounts cannot be suspended here at all (a compromised
 * reviewer could otherwise suspend every other reviewer first).
 */
@Service
public class AdminUserService {

    /** The console reads a page, not the user table. */
    private static final int PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final AdminAuditService auditService;

    public AdminUserService(
            UserRepository userRepository,
            BookingRepository bookingRepository,
            AdminAuditService auditService
    ) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.auditService = auditService;
    }

    /**
     * Search by name or email; blank query lists everyone (newest
     * first), capped at one console page.
     */
    @Transactional(readOnly = true)
    public List<AdminUserResponse> list(String query) {
        String normalized = query == null || query.isBlank()
                ? null
                : query.trim();

        Map<Long, Long> bookingCounts = bookingCounts();

        return userRepository
                .search(normalized, PageRequest.of(0, PAGE_SIZE))
                .stream()
                .map(user -> toResponse(user, bookingCounts))
                .toList();
    }

    /**
     * Profile plus every booking the account holds. (FR-31
     * "view user history")
     */
    @Transactional(readOnly = true)
    public AdminUserHistoryResponse history(Long userId) {
        User user = require(userId);

        List<AdminUserHistoryResponse.Row> bookings =
                bookingRepository
                        .findByUser_UserIdOrderByCreatedAtDesc(userId)
                        .stream()
                        .map(booking ->
                                new AdminUserHistoryResponse.Row(
                                        booking.getBookingId(),
                                        booking.getBookingReference(),
                                        booking.getBookingType(),
                                        booking.getTotalAmount(),
                                        booking.getCurrency(),
                                        booking.getBookingStatus(),
                                        booking.getPaymentStatus(),
                                        booking.getCreatedAt()
                                ))
                        .toList();

        return new AdminUserHistoryResponse(
                toResponse(user, bookingCounts()),
                bookings
        );
    }

    /**
     * Blocks login and token refresh until reactivated, and
     * records why. The role and every row the account owns are
     * untouched: suspension is a pause, not a deletion.
     */
    @Transactional
    public AdminUserResponse suspend(
            Long actorUserId,
            Long userId,
            AdminSuspendRequest request
    ) {
        User user = require(userId);

        if (user.getUserId().equals(actorUserId)) {
            throw PartnerApplicationException.badRequest(
                    "You cannot suspend your own account"
            );
        }

        if (user.getRole() == Role.ROLE_SUPER_ADMIN) {
            throw PartnerApplicationException.badRequest(
                    "Super admin accounts cannot be suspended "
                            + "from the console"
            );
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw PartnerApplicationException.conflict(
                    "This account is already suspended"
            );
        }

        user.setStatus(UserStatus.SUSPENDED);

        auditService.record(
                actorUserId,
                "USER_SUSPENDED",
                "User",
                userId,
                request.reason()
        );

        return toResponse(user, bookingCounts());
    }

    @Transactional
    public AdminUserResponse reactivate(
            Long actorUserId,
            Long userId
    ) {
        User user = require(userId);

        if (user.getStatus() != UserStatus.SUSPENDED) {
            throw PartnerApplicationException.conflict(
                    "This account is not suspended"
            );
        }

        user.setStatus(UserStatus.ACTIVE);

        auditService.record(
                actorUserId,
                "USER_REACTIVATED",
                "User",
                userId,
                "Account restored to ACTIVE"
        );

        return toResponse(user, bookingCounts());
    }

    private User require(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "User not found"
                        )
                );
    }

    private Map<Long, Long> bookingCounts() {
        return bookingRepository
                .countBookingsPerUser()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }

    private AdminUserResponse toResponse(
            User user,
            Map<Long, Long> bookingCounts
    ) {
        return new AdminUserResponse(
                user.getUserId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getStatus(),
                bookingCounts.getOrDefault(user.getUserId(), 0L),
                user.getCreatedAt()
        );
    }
}