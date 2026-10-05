package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.destination.TouristPlaceResponse;
import com.Travel.Buddy.service.destination.DestinationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/v1/destinations")
public class DestinationController {

    private final DestinationService destinationService;

    public DestinationController(
            DestinationService destinationService
    ) {
        this.destinationService = destinationService;
    }

    /**
     * Get destinations by state IDs.
     *
     * Example:
     *
     * GET /api/v1/destinations?stateIds=1
     *
     * GET /api/v1/destinations?stateIds=1,2,3
     */
    @GetMapping
    public ResponseEntity<List<TouristPlaceResponse>> getDestinations(
            @RequestParam(
                    name = "stateIds",
                    required = false
            )
            String stateIds
    ) {

        if (stateIds == null || stateIds.isBlank()) {

            return ResponseEntity.ok(
                    Collections.emptyList()
            );
        }

        List<Long> ids;

        try {

            ids = Arrays.stream(stateIds.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(Long::valueOf)
                    .toList();

        } catch (NumberFormatException exception) {

            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(
                destinationService.getDestinations(ids)
        );
    }

    /**
     * Get one destination by ID.
     *
     * Example:
     *
     * GET /api/v1/destinations/1
     */
    @GetMapping("/{placeId}")
    public ResponseEntity<TouristPlaceResponse> getDestinationById(
            @PathVariable Long placeId
    ) {

        return ResponseEntity.ok(
                destinationService.getDestinationById(placeId)
        );
    }
}