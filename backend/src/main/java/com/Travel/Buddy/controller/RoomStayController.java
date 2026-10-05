package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.stay.PartnerReservationResponse;
import com.Travel.Buddy.dto.stay.PhysicalRoomRequest;
import com.Travel.Buddy.dto.stay.PhysicalRoomResponse;
import com.Travel.Buddy.dto.stay.RoomAssignmentRequest;
import com.Travel.Buddy.dto.stay.RoomStayResponse;
import com.Travel.Buddy.entity.PhysicalRoomStatus;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.stay.PartnerReservationService;
import com.Travel.Buddy.service.stay.RoomStayService;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Front-desk API: physical rooms, assignment, check-in and check-out.
 * (FR-22, FR-23)
 */
@RestController
@RequestMapping("/api/v1/partner/hotel")
public class RoomStayController {

    private final RoomStayService roomStayService;

    private final PartnerReservationService partnerReservationService;

    private final UserRepository userRepository;

    public RoomStayController(
            RoomStayService roomStayService,
            PartnerReservationService partnerReservationService,
            UserRepository userRepository
    ) {
        this.roomStayService = roomStayService;
        this.partnerReservationService = partnerReservationService;
        this.userRepository = userRepository;
    }

    /* ============================================================
     * PHYSICAL ROOMS
     * ============================================================ */

    /**
     * The property's real rooms, flagging which are occupied in the
     * requested window so the front desk sees tonight rather than
     * just the master list.
     */
    @GetMapping("/properties/{propertyId}/physical-rooms")
    public ResponseEntity<List<PhysicalRoomResponse>> listRooms(
            Authentication authentication,
            @PathVariable Long propertyId,

            @RequestParam(required = false) Long roomTypeId,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    ) {
        return ResponseEntity.ok(
                roomStayService.listRooms(
                        currentUserId(authentication),
                        propertyId,
                        roomTypeId,
                        from,
                        to
                )
        );
    }

    @PostMapping(
            "/properties/{propertyId}/room-types/{roomTypeId}/physical-rooms"
    )
    public ResponseEntity<PhysicalRoomResponse> addRoom(
            Authentication authentication,
            @PathVariable Long propertyId,
            @PathVariable Long roomTypeId,
            @Valid @RequestBody PhysicalRoomRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        roomStayService.addRoom(
                                currentUserId(authentication),
                                propertyId,
                                roomTypeId,
                                request
                        )
                );
    }

    /**
     * Takes a room out of service, or returns it to service. Refused
     * while a live stay holds the room.
     */
    @PutMapping(
            "/properties/{propertyId}/physical-rooms/{physicalRoomId}/status"
    )
    public ResponseEntity<PhysicalRoomResponse> setStatus(
            Authentication authentication,
            @PathVariable Long propertyId,
            @PathVariable Long physicalRoomId,
            @RequestParam PhysicalRoomStatus status
    ) {
        return ResponseEntity.ok(
                roomStayService.setRoomStatus(
                        currentUserId(authentication),
                        propertyId,
                        physicalRoomId,
                        status
                )
        );
    }
    /* ============================================================
     * ASSIGNMENT (FR-22)
     * ============================================================ */

    @PostMapping("/bookings/{bookingId}/assignments")
    public ResponseEntity<RoomStayResponse> assign(
            Authentication authentication,
            @PathVariable Long bookingId,
            @Valid @RequestBody RoomAssignmentRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        roomStayService.assign(
                                currentUserId(authentication),
                                bookingId,
                                request.physicalRoomId()
                        )
                );
    }

    @GetMapping("/bookings/{bookingId}/assignments")
    public ResponseEntity<List<RoomStayResponse>> assignments(
            Authentication authentication,
            @PathVariable Long bookingId
    ) {
        return ResponseEntity.ok(
                roomStayService.staysForBooking(
                        currentUserId(authentication),
                        bookingId
                )
        );
    }

    @DeleteMapping("/assignments/{stayId}")
    public ResponseEntity<RoomStayResponse> unassign(
            Authentication authentication,
            @PathVariable Long stayId
    ) {
        return ResponseEntity.ok(
                roomStayService.unassign(
                        currentUserId(authentication),
                        stayId
                )
        );
    }

    /* ============================================================
     * CHECK-IN / CHECK-OUT (FR-23)
     * ============================================================ */

    @PostMapping("/assignments/{stayId}/check-in")
    public ResponseEntity<RoomStayResponse> checkIn(
            Authentication authentication,
            @PathVariable Long stayId
    ) {
        return ResponseEntity.ok(
                roomStayService.checkIn(
                        currentUserId(authentication),
                        stayId
                )
        );
    }

    @PostMapping("/assignments/{stayId}/check-out")
    public ResponseEntity<RoomStayResponse> checkOut(
            Authentication authentication,
            @PathVariable Long stayId
    ) {
        return ResponseEntity.ok(
                roomStayService.checkOut(
                        currentUserId(authentication),
                        stayId
                )
        );
    }

    @PostMapping("/assignments/{stayId}/no-show")
    public ResponseEntity<RoomStayResponse> noShow(
            Authentication authentication,
            @PathVariable Long stayId
    ) {
        return ResponseEntity.ok(
                roomStayService.markNoShow(
                        currentUserId(authentication),
                        stayId
                )
        );
    }

    /**
     * Arrivals and in-house guests the front desk must act on.
     */
    @GetMapping("/front-desk")
    public ResponseEntity<List<RoomStayResponse>> frontDesk(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                roomStayService.frontDeskQueue(
                        currentUserId(authentication)
                )
        );
    }

    /**
     * Every reservation the partner still has to act on, soonest first.
     *
     * <p>{@code /front-desk} only returns stays that already have a
     * physical room, so on its own it hides the booking that most needs
     * attention: a confirmed guest who has not been given a room yet.
     */
    @GetMapping("/reservations")
    public ResponseEntity<List<PartnerReservationResponse>>
    reservations(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                partnerReservationService.listOpenReservations(
                        currentUserId(authentication)
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