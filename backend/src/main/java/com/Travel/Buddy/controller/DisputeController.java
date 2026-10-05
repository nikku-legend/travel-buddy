package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.dispute.ChangeDisputeStatusRequest;
import com.Travel.Buddy.dto.dispute.DisputePageResponse;
import com.Travel.Buddy.dto.dispute.DisputeQueueResponse;
import com.Travel.Buddy.dto.dispute.DisputeResponse;
import com.Travel.Buddy.dto.dispute.RaiseDisputeRequest;
import com.Travel.Buddy.dto.dispute.ResolveDisputeRequest;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.dispute.DisputeService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Disputes. (FR-28)
 *
 * <p>Split into claimant routes and admin routes so the two
 * audiences never share a handler. The admin prefix is already
 * gated to ROLE_SUPER_ADMIN by SecurityConfig, which is what stops
 * a partner calling the ruling endpoints.
 */
@RestController
@RequestMapping("/api/v1")
public class DisputeController {

    private final DisputeService disputeService;
    private final UserRepository userRepository;

    public DisputeController(
            DisputeService disputeService,
            UserRepository userRepository
    ) {
        this.disputeService = disputeService;
        this.userRepository = userRepository;
    }

    /* ============================================================
     * CLAIMANT
     * ============================================================ */

    @PostMapping("/bookings/{bookingId}/dispute")
    public ResponseEntity<DisputeResponse> raise(
            Authentication authentication,
            @PathVariable Long bookingId,
            @Valid @RequestBody RaiseDisputeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                disputeService.raise(
                        currentUserId(authentication),
                        bookingId,
                        request
                )
        );
    }

    @GetMapping("/disputes/mine")
    public ResponseEntity<DisputePageResponse> myDisputes(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                disputeService.myDisputes(
                        currentUserId(authentication),
                        page,
                        size
                )
        );
    }

    @PostMapping("/disputes/{disputeId}/withdraw")
    public ResponseEntity<DisputeResponse> withdraw(
            Authentication authentication,
            @PathVariable Long disputeId
    ) {
        return ResponseEntity.ok(
                disputeService.withdraw(
                        currentUserId(authentication),
                        disputeId
                )
        );
    }

    /* ============================================================
     * SHARED READ
     *
     * Visible to the claimant, the partner complained about, and
     * admins. The service decides which of those the caller is.
     * ============================================================ */

    @GetMapping("/disputes/{disputeId}")
    public ResponseEntity<DisputeResponse> get(
            Authentication authentication,
            @PathVariable Long disputeId
    ) {
        return ResponseEntity.ok(
                disputeService.get(
                        currentUserId(authentication),
                        isAdmin(authentication),
                        disputeId
                )
        );
    }

    /* ============================================================
     * PARTNER
     * ============================================================ */

    @GetMapping("/partner/disputes")
    public ResponseEntity<DisputeQueueResponse> againstMe(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                disputeService.disputesAgainst(
                        currentUserId(authentication),
                        page,
                        size
                )
        );
    }

    /* ============================================================
     * ADMIN
     * ============================================================ */

    @GetMapping("/admin/disputes")
    public ResponseEntity<DisputeQueueResponse> queue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                disputeService.queue(page, size)
        );
    }

    @GetMapping("/admin/disputes/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(disputeService.stats());
    }

    @PostMapping("/admin/disputes/{disputeId}/claim")
    public ResponseEntity<DisputeResponse> claim(
            Authentication authentication,
            @PathVariable Long disputeId,
            @RequestParam(required = false) String note
    ) {
        return ResponseEntity.ok(
                disputeService.claim(
                        currentUserId(authentication),
                        disputeId,
                        note
                )
        );
    }

    @PostMapping("/admin/disputes/{disputeId}/status")
    public ResponseEntity<DisputeResponse> changeStatus(
            Authentication authentication,
            @PathVariable Long disputeId,
            @Valid @RequestBody ChangeDisputeStatusRequest request
    ) {
        return ResponseEntity.ok(
                disputeService.changeStatus(
                        currentUserId(authentication),
                        disputeId,
                        request
                )
        );
    }

    @PostMapping("/admin/disputes/{disputeId}/resolve")
    public ResponseEntity<DisputeResponse> resolve(
            Authentication authentication,
            @PathVariable Long disputeId,
            @Valid @RequestBody ResolveDisputeRequest request
    ) {
        return ResponseEntity.ok(
                disputeService.resolve(
                        currentUserId(authentication),
                        disputeId,
                        request
                )
        );
    }

    /* ============================================================
     * HELPERS
     * ============================================================ */

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "ROLE_SUPER_ADMIN".equals(
                                authority.getAuthority()
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