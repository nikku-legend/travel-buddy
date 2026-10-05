package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.cab.BookRideRequest;
import com.Travel.Buddy.dto.cab.CabResponse;
import com.Travel.Buddy.dto.cab.RideResponse;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.entity.VehicleType;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.cab.CabService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cabs")
public class CabController {

    private final CabService cabService;
    private final UserRepository userRepository;

    public CabController(CabService cabService, UserRepository userRepository) {
        this.cabService = cabService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<CabResponse>> searchCabs(
            @RequestParam(required = false) Integer stateId,
            @RequestParam(required = false) VehicleType vehicleType
    ) {
        return ResponseEntity.ok(cabService.searchCabs(stateId, vehicleType));
    }

    @GetMapping("/{cabId}")
    public ResponseEntity<CabResponse> getCabById(@PathVariable Long cabId) {
        return ResponseEntity.ok(cabService.getCabById(cabId));
    }

    @PostMapping("/book")
    public ResponseEntity<RideResponse> bookRide(
            Authentication authentication,
            @Valid @RequestBody BookRideRequest request
    ) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        return ResponseEntity.ok(cabService.bookRide(user, request));
    }

    @GetMapping("/my-rides")
    public ResponseEntity<List<RideResponse>> getMyRides(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        return ResponseEntity.ok(cabService.getUserRides(user));
    }
}
