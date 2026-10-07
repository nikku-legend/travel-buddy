package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.trip.TripDayAllocationResponse;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.trip.TripDayAllocationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only trip day-allocation advice.
 */
@RestController
@RequestMapping("/api/v1/trip-planner/trips/{tripId}/day-allocation")
public class TripDayAllocationController {

    private final TripDayAllocationService allocationService;
    private final UserRepository userRepository;

    public TripDayAllocationController(
            TripDayAllocationService allocationService,
            UserRepository userRepository
    ) {
        this.allocationService = allocationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public TripDayAllocationResponse recommend(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        return allocationService.recommend(
                currentUserId(authentication), tripId
        );
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new AccessDeniedException(
                    "Authentication is required"
            );
        }
        return userRepository.findByEmail(authentication.getName())
                .map(User::getUserId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + authentication.getName()
                ));
    }
}
