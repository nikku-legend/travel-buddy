package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.inventory.InventoryNightResponse;
import com.Travel.Buddy.dto.inventory.RoomBlockRequest;
import com.Travel.Buddy.dto.inventory.RoomBlockResponse;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.property.InventoryOperationsService;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Partner inventory operations: calendar and blocking. (FR-21)
 *
 * <p>These routes sit under the same property-scoped path as rooms, so
 * the ownership boundary is visible in the URL rather than implied.
 */
@RestController
@RequestMapping("/api/v1/partner/hotel")
public class InventoryOperationsController {

    private final InventoryOperationsService inventoryService;

    private final UserRepository userRepository;

    public InventoryOperationsController(
            InventoryOperationsService inventoryService,
            UserRepository userRepository
    ) {
        this.inventoryService = inventoryService;
        this.userRepository = userRepository;
    }

    /**
     * Nightly availability, reservations and blocks for one room type.
     *
     * <p>Example:
     * {@code GET /api/v1/partner/hotel/properties/1/rooms/2/calendar
     * ?from=2026-11-01&to=2026-11-30}
     */
    @GetMapping("/properties/{propertyId}/rooms/{roomTypeId}/calendar")
    public ResponseEntity<List<InventoryNightResponse>> calendar(
            Authentication authentication,
            @PathVariable Long propertyId,
            @PathVariable Long roomTypeId,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    ) {
        return ResponseEntity.ok(
                inventoryService.calendar(
                        currentUserId(authentication),
                        propertyId,
                        roomTypeId,
                        from,
                        to
                )
        );
    }

    /**
     * Takes rooms out of service across a date range.
     */
    @PostMapping("/properties/{propertyId}/rooms/{roomTypeId}/blocks")
    public ResponseEntity<List<RoomBlockResponse>> block(
            Authentication authentication,
            @PathVariable Long propertyId,
            @PathVariable Long roomTypeId,
            @Valid @RequestBody RoomBlockRequest request
    ) {
        return ResponseEntity.ok(
                inventoryService.block(
                        currentUserId(authentication),
                        propertyId,
                        roomTypeId,
                        request
                )
        );
    }

    /**
     * Every block the partner owns, active and released.
     */
    @GetMapping("/blocks")
    public ResponseEntity<List<RoomBlockResponse>> listBlocks(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                inventoryService.listBlocks(
                        currentUserId(authentication)
                )
        );
    }

    /**
     * Puts a night back on sale. The block is released, not deleted,
     * so the closure history survives.
     */
    @DeleteMapping("/blocks/{blockId}")
    public ResponseEntity<RoomBlockResponse> release(
            Authentication authentication,
            @PathVariable Long blockId
    ) {
        return ResponseEntity.ok(
                inventoryService.release(
                        currentUserId(authentication),
                        blockId
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

        User user = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Authenticated user no longer exists"
                        )
                );

        return user.getUserId();
    }
}
