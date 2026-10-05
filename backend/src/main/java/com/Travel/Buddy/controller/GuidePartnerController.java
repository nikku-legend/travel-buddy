package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.guide.GuideProfileRequest;
import com.Travel.Buddy.dto.guide.GuideReservationResponse;
import com.Travel.Buddy.dto.guide.GuideSummaryResponse;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.guide.GuideService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/partner/guide")
@PreAuthorize("hasRole('GUIDE_PARTNER') or hasRole('SUPER_ADMIN')")
public class GuidePartnerController {

    private final GuideService guideService;
    private final UserRepository userRepository;

    public GuidePartnerController(
            GuideService guideService,
            UserRepository userRepository
    ) {
        this.guideService = guideService;
        this.userRepository = userRepository;
    }

    @GetMapping("/profile")
    public ResponseEntity<GuideSummaryResponse> getMyProfile(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        return ResponseEntity.ok(guideService.getGuideProfileForUser(user));
    }

    /**
     * The tours this guide has actually been given. (FR-16, FR-23)
     *
     * <p>The partner portal previously had no such endpoint and
     * rendered invented figures instead, including a 5.0 rating on a
     * profile it simultaneously described as new.
     */
    @GetMapping("/reservations")
    public ResponseEntity<List<GuideReservationResponse>>
            myReservations(Authentication authentication) {

        User user = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow();

        return ResponseEntity.ok(
                guideService.myReservations(user)
        );
    }
    @PostMapping("/profile")
    public ResponseEntity<GuideSummaryResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody GuideProfileRequest request
    ) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        return ResponseEntity.ok(guideService.upsertGuideProfile(user, request));
    }

    @PostMapping("/availability")
    public ResponseEntity<Map<String, Object>> updateAvailability(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam boolean isAvailable
    ) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        guideService.setAvailability(user, date, isAvailable);
        return ResponseEntity.ok(Map.of(
                "date", date,
                "isAvailable", isAvailable,
                "message", "Availability updated successfully"
        ));
    }
}
