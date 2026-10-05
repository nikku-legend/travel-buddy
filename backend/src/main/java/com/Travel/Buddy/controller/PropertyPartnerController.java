package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.property.PartnerPropertyResponse;
import com.Travel.Buddy.dto.property.PropertyDecisionRequest;
import com.Travel.Buddy.dto.property.PropertyUpsertRequest;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.property.PropertyApprovalService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Property management for hotel partners and the admin reviewer. (FR-20)
 *
 * <p>Split by audience:
 *
 * <ul>
 *   <li>{@code /api/v1/partner/hotel/properties} - the owner</li>
 *   <li>{@code /api/v1/admin/properties} - the reviewer</li>
 * </ul>
 *
 * <p>Note there is deliberately no partner endpoint that can set
 * {@code status = APPROVED}. A partner can create, edit and submit, and
 * nothing more. Going live is an admin-only action.
 */
@RestController
@RequestMapping("/api/v1")
public class PropertyPartnerController {

    private final PropertyApprovalService approvalService;

    private final UserRepository userRepository;

    public PropertyPartnerController(
            PropertyApprovalService approvalService,
            UserRepository userRepository
    ) {
        this.approvalService = approvalService;
        this.userRepository = userRepository;
    }

    /* ============================================================
     * PARTNER SIDE
     * ============================================================ */

    @GetMapping("/partner/hotel/properties")
    public ResponseEntity<List<PartnerPropertyResponse>>
    myProperties(Authentication authentication) {

        return ResponseEntity.ok(
                approvalService.myProperties(
                        currentUserId(authentication)
                )
        );
    }

    @PostMapping("/partner/hotel/properties")
    public ResponseEntity<PartnerPropertyResponse> create(
            Authentication authentication,
            @Valid @RequestBody PropertyUpsertRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        approvalService.create(
                                currentUser(authentication),
                                request
                        )
                );
    }

    @PutMapping("/partner/hotel/properties/{propertyId}")
    public ResponseEntity<PartnerPropertyResponse> update(
            Authentication authentication,
            @PathVariable Long propertyId,
            @Valid @RequestBody PropertyUpsertRequest request
    ) {

        return ResponseEntity.ok(
                approvalService.update(
                        currentUserId(authentication),
                        propertyId,
                        request
                )
        );
    }

    @PostMapping("/partner/hotel/properties/{propertyId}/submit")
    public ResponseEntity<PartnerPropertyResponse> submit(
            Authentication authentication,
            @PathVariable Long propertyId
    ) {

        return ResponseEntity.ok(
                approvalService.submit(
                        currentUserId(authentication),
                        propertyId
                )
        );
    }

    /* ============================================================
     * ADMIN SIDE
     * ============================================================ */

    @GetMapping("/admin/properties/pending")
    public ResponseEntity<List<PartnerPropertyResponse>> queue() {
        return ResponseEntity.ok(
                approvalService.approvalQueue()
        );
    }

    @GetMapping("/admin/properties/{propertyId}")
    public ResponseEntity<PartnerPropertyResponse> detail(
            @PathVariable Long propertyId
    ) {
        return ResponseEntity.ok(
                approvalService.getForReview(propertyId)
        );
    }

    @PostMapping("/admin/properties/{propertyId}/decision")
    public ResponseEntity<PartnerPropertyResponse> decide(
            Authentication authentication,
            @PathVariable Long propertyId,
            @Valid @RequestBody PropertyDecisionRequest request
    ) {

        return ResponseEntity.ok(
                approvalService.decide(
                        currentUserId(authentication),
                        propertyId,
                        request
                )
        );
    }

    @PostMapping("/admin/properties/{propertyId}/suspend")
    public ResponseEntity<PartnerPropertyResponse> suspend(
            Authentication authentication,
            @PathVariable Long propertyId,
            @RequestParam String reason
    ) {

        return ResponseEntity.ok(
                approvalService.suspend(
                        currentUserId(authentication),
                        propertyId,
                        reason
                )
        );
    }

    /* ============================================================
     * HELPER
     * ============================================================ */

    private User currentUser(Authentication authentication) {

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
                );
    }

    private Long currentUserId(Authentication authentication) {
        return currentUser(authentication).getUserId();
    }
}
