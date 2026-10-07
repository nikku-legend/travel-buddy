package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.admin.AdminDestinationRequest;
import com.Travel.Buddy.dto.admin.AdminDestinationResponse;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.destination.DestinationAdminService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Destination CRUD for the admin console. (FR-32)
 *
 * <p>Under /admin so SecurityConfig's SUPER_ADMIN guard covers
 * it without this class restating the rule.
 */
@RestController
@RequestMapping("/api/v1/admin/destinations")
public class DestinationAdminController {

    private final DestinationAdminService adminService;
    private final UserRepository userRepository;

    public DestinationAdminController(
            DestinationAdminService adminService,
            UserRepository userRepository
    ) {
        this.adminService = adminService;
        this.userRepository = userRepository;
    }

    /** Everything, retired listings included. */
    @GetMapping
    public ResponseEntity<List<AdminDestinationResponse>> list() {
        return ResponseEntity.ok(adminService.list());
    }

    @PostMapping
    public ResponseEntity<AdminDestinationResponse> create(
            Authentication authentication,
            @Valid @RequestBody AdminDestinationRequest request
    ) {
        return ResponseEntity.ok(adminService.create(
                currentUserId(authentication),
                request
        ));
    }

    @PutMapping("/{placeId}")
    public ResponseEntity<AdminDestinationResponse> update(
            Authentication authentication,
            @PathVariable Long placeId,
            @Valid @RequestBody AdminDestinationRequest request
    ) {
        return ResponseEntity.ok(adminService.update(
                currentUserId(authentication),
                placeId,
                request
        ));
    }

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