package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.room.PartnerRoomTypeResponse;
import com.Travel.Buddy.dto.room.RoomTypeUpsertRequest;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.property.RoomTypeService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Room type management for hotel partners. (FR-05)
 *
 * <p>Routes are nested under the property they belong to, which makes
 * the ownership scope explicit in the URL rather than something the
 * service has to guess.
 *
 * <p>Deactivation is exposed instead of deletion on purpose: deleting a
 * room type would cascade away daily inventory that existing bookings
 * reference, erasing history that must be preserved.
 */
@RestController
@RequestMapping("/api/v1/partner/hotel/properties")
public class RoomTypePartnerController {

    private final RoomTypeService roomTypeService;

    private final UserRepository userRepository;

    public RoomTypePartnerController(
            RoomTypeService roomTypeService,
            UserRepository userRepository
    ) {
        this.roomTypeService = roomTypeService;
        this.userRepository = userRepository;
    }

    @GetMapping("/{propertyId}/rooms")
    public ResponseEntity<List<PartnerRoomTypeResponse>> list(
            Authentication authentication,
            @PathVariable Long propertyId
    ) {
        return ResponseEntity.ok(
                roomTypeService.listForProperty(
                        currentUserId(authentication),
                        propertyId
                )
        );
    }

    @PostMapping("/{propertyId}/rooms")
    public ResponseEntity<PartnerRoomTypeResponse> create(
            Authentication authentication,
            @PathVariable Long propertyId,
            @Valid @RequestBody RoomTypeUpsertRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        roomTypeService.create(
                                currentUserId(authentication),
                                propertyId,
                                request
                        )
                );
    }

    @PutMapping("/{propertyId}/rooms/{roomTypeId}")
    public ResponseEntity<PartnerRoomTypeResponse> update(
            Authentication authentication,
            @PathVariable Long propertyId,
            @PathVariable Long roomTypeId,
            @Valid @RequestBody RoomTypeUpsertRequest request
    ) {
        return ResponseEntity.ok(
                roomTypeService.update(
                        currentUserId(authentication),
                        propertyId,
                        roomTypeId,
                        request
                )
        );
    }

    @DeleteMapping("/{propertyId}/rooms/{roomTypeId}")
    public ResponseEntity<PartnerRoomTypeResponse> deactivate(
            Authentication authentication,
            @PathVariable Long propertyId,
            @PathVariable Long roomTypeId
    ) {
        return ResponseEntity.ok(
                roomTypeService.deactivate(
                        currentUserId(authentication),
                        propertyId,
                        roomTypeId
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
