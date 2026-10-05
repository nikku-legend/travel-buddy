package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.guide.GuideSummaryResponse;
import com.Travel.Buddy.service.guide.GuideService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/guides")
public class GuideController {

    private final GuideService guideService;

    public GuideController(GuideService guideService) {
        this.guideService = guideService;
    }

    @GetMapping
    public ResponseEntity<List<GuideSummaryResponse>> getGuides(
            @RequestParam(required = false) Integer stateId
    ) {
        return ResponseEntity.ok(guideService.getGuides(stateId));
    }

    @GetMapping("/{guideId}")
    public ResponseEntity<GuideSummaryResponse> getGuideById(
            @PathVariable Long guideId
    ) {
        return ResponseEntity.ok(guideService.getGuideById(guideId));
    }

    @GetMapping("/{guideId}/availability")
    public ResponseEntity<Map<String, Object>> checkAvailability(
            @PathVariable Long guideId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        boolean available = guideService.checkAvailability(guideId, date);
        return ResponseEntity.ok(Map.of(
                "guideId", guideId,
                "date", date,
                "available", available
        ));
    }
}
