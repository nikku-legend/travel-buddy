package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.discovery.HomeDiscoveryResponse;
import com.Travel.Buddy.service.discovery.HomeDiscoveryService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Home discovery. (SRS 2.3 section 2)
 *
 * <p>Public and unauthenticated. Section 2.1 makes Home the
 * discovery surface, so gating it behind a login would hide the
 * platform from exactly the visitors it is meant to attract.
 */
@RestController
@RequestMapping("/api/v1/home")
public class HomeDiscoveryController {

    private final HomeDiscoveryService homeDiscoveryService;

    public HomeDiscoveryController(
            HomeDiscoveryService homeDiscoveryService
    ) {
        this.homeDiscoveryService = homeDiscoveryService;
    }

    /**
     * Every Home section in one response.
     *
     * <p>One request rather than ten. Fetching each section
     * separately would mean ten round trips on first paint, and
     * the sections could disagree with each other because each
     * would have been read at a slightly different moment.
     */
    @GetMapping
    public ResponseEntity<HomeDiscoveryResponse> home() {
        return ResponseEntity.ok(homeDiscoveryService.home());
    }
}