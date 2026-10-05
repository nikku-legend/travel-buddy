package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.cab.CabRegisterRequest;
import com.Travel.Buddy.dto.cab.CabResponse;
import com.Travel.Buddy.dto.cab.RideResponse;
import com.Travel.Buddy.entity.RideStatus;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.cab.CabService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/partner/cab")
@PreAuthorize("hasRole('CAB_PARTNER') or hasRole('SUPER_ADMIN')")
public class CabPartnerController {

    private final CabService cabService;
    private final UserRepository userRepository;

    public CabPartnerController(CabService cabService, UserRepository userRepository) {
        this.cabService = cabService;
        this.userRepository = userRepository;
    }

    @GetMapping("/vehicles")
    public ResponseEntity<List<CabResponse>> getMyVehicles(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        return ResponseEntity.ok(cabService.getPartnerCabs(user));
    }

    @PostMapping("/vehicles")
    public ResponseEntity<CabResponse> addVehicle(
            Authentication authentication,
            @Valid @RequestBody CabRegisterRequest request
    ) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        return ResponseEntity.ok(cabService.registerCab(user, request));
    }

    @GetMapping("/rides")
    public ResponseEntity<List<RideResponse>> getAssignedRides(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        return ResponseEntity.ok(cabService.getPartnerRides(user));
    }

    @PatchMapping("/rides/{rideId}/status")
    public ResponseEntity<RideResponse> updateRideStatus(
            Authentication authentication,
            @PathVariable Long rideId,
            @RequestParam RideStatus status
    ) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        return ResponseEntity.ok(cabService.updateRideStatus(user, rideId, status));
    }
}
