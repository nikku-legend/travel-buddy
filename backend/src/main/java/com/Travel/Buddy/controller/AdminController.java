package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.admin.AdminAuditLogResponse;
import com.Travel.Buddy.dto.admin.AdminLedgerResponse;
import com.Travel.Buddy.dto.admin.AdminRefundDecisionRequest;
import com.Travel.Buddy.dto.admin.AdminRefundResponse;
import com.Travel.Buddy.dto.admin.AdminStatsResponse;
import com.Travel.Buddy.dto.admin.AdminSuspendRequest;
import com.Travel.Buddy.dto.admin.AdminUserHistoryResponse;
import com.Travel.Buddy.dto.admin.AdminUserResponse;
import com.Travel.Buddy.dto.trip.TripCheckoutResponse;
import com.Travel.Buddy.entity.PaymentStatus;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.admin.AdminAuditService;
import com.Travel.Buddy.service.admin.AdminStatsService;
import com.Travel.Buddy.service.admin.AdminUserService;
import com.Travel.Buddy.service.admin.FinanceService;
import com.Travel.Buddy.service.trip.TripCheckoutService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * The Admin Command Center API. (FR-30, FR-31, FR-33)
 *
 * <p>Every path sits under {@code /api/v1/admin/**}, which
 * SecurityConfig already restricts to SUPER_ADMIN -- this
 * controller adds no bypass and assumes none.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminStatsService statsService;
    private final AdminUserService userService;
    private final AdminAuditService auditService;
    private final FinanceService financeService;
    private final UserRepository userRepository;
    private final TripCheckoutService checkoutService;

    public AdminController(
            AdminStatsService statsService,
            AdminUserService userService,
            AdminAuditService auditService,
            FinanceService financeService,
            UserRepository userRepository,
            TripCheckoutService checkoutService
    ) {
        this.statsService = statsService;
        this.userService = userService;
        this.auditService = auditService;
        this.financeService = financeService;
        this.userRepository = userRepository;
        this.checkoutService = checkoutService;
    }

    /* ============================================================
     * COMMAND CENTER  (FR-30)
     * ============================================================ */

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> stats() {
        return ResponseEntity.ok(statsService.overview());
    }

    @GetMapping("/audit-log")
    public ResponseEntity<List<AdminAuditLogResponse>> auditLog() {
        return ResponseEntity.ok(auditService.recent());
    }

    /* ============================================================
     * USER MANAGEMENT  (FR-31)
     * ============================================================ */

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserResponse>> users(
            @RequestParam(required = false) String query
    ) {
        return ResponseEntity.ok(userService.list(query));
    }

    @GetMapping("/users/{userId}/history")
    public ResponseEntity<AdminUserHistoryResponse> history(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(userService.history(userId));
    }

    @PostMapping("/users/{userId}/suspend")
    public ResponseEntity<AdminUserResponse> suspend(
            Authentication authentication,
            @PathVariable Long userId,
            @Valid @RequestBody AdminSuspendRequest request
    ) {
        return ResponseEntity.ok(userService.suspend(
                currentUserId(authentication),
                userId,
                request
        ));
    }

    @PostMapping("/users/{userId}/reactivate")
    public ResponseEntity<AdminUserResponse> reactivate(
            Authentication authentication,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(userService.reactivate(
                currentUserId(authentication),
                userId
        ));
    }

    /* ============================================================
     * FINANCIAL DESK  (FR-33)
     * ============================================================ */

    @GetMapping("/finance/ledger")
    public ResponseEntity<AdminLedgerResponse> ledger(
            @RequestParam(required = false)
            PaymentStatus paymentStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return ResponseEntity.ok(
                financeService.ledger(paymentStatus, page, size)
        );
    }

    @GetMapping("/finance/refunds")
    public ResponseEntity<List<AdminRefundResponse>> refunds() {
        return ResponseEntity.ok(financeService.pendingRefunds());
    }

    @PostMapping("/finance/refunds/{cancellationId}")
    public ResponseEntity<AdminRefundResponse> decideRefund(
            Authentication authentication,
            @PathVariable Long cancellationId,
            @Valid @RequestBody AdminRefundDecisionRequest request
    ) {
        return ResponseEntity.ok(financeService.decideRefund(
                currentUserId(authentication),
                cancellationId,
                request
        ));
    }

    @PostMapping("/trip-checkouts/{checkoutId}/reconcile")
    public ResponseEntity<TripCheckoutResponse> reconcileTripCheckout(
            Authentication authentication,
            @PathVariable Long checkoutId
    ) {
        return ResponseEntity.ok(
                TripCheckoutResponse.of(
                        checkoutService.reconcileRecovery(
                                currentUserId(authentication),
                                checkoutId
                        )
                )
        );
    }

    /* ============================================================
     * HELPER
     * ============================================================
     *
     * Same resolution the other admin controllers use: the
     * authenticated principal is an email, and the row is the
     * authority. An admin whose account vanished mid-session
     * must not act as an anonymous id.
     */

    private Long currentUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new AccessDeniedException(
                    "Authentication is required"
            );
        }

        return userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Authenticated user no longer exists"
                        )
                )
                .getUserId();
    }
}