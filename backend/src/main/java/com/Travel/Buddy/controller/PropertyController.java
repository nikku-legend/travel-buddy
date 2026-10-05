package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.availability.AvailabilityResponse;
import com.Travel.Buddy.dto.property.PropertyDetailResponse;
import com.Travel.Buddy.dto.property.PropertyResponse;
import com.Travel.Buddy.entity.PropertyType;
import com.Travel.Buddy.service.property.AvailabilityService;
import com.Travel.Buddy.service.property.PropertyService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/stays")
public class PropertyController {

    private final PropertyService propertyService;
    private final AvailabilityService availabilityService;

    public PropertyController(
            PropertyService propertyService,
            AvailabilityService availabilityService
    ) {
        this.propertyService = propertyService;
        this.availabilityService = availabilityService;
    }

    /**
     * Get verified active properties.
     *
     * Examples:
     *
     * GET /api/v1/stays
     *
     * GET /api/v1/stays?stateId=1
     *
     * GET /api/v1/stays?stateId=1&propertyType=HOTEL
     */
    @GetMapping
    public ResponseEntity<List<PropertyResponse>> getProperties(
            @RequestParam(
                    name = "stateId",
                    required = false
            )
            Integer stateId,

            @RequestParam(
                    name = "propertyType",
                    required = false
            )
            PropertyType propertyType
    ) {

        return ResponseEntity.ok(
                propertyService.getProperties(
                        stateId,
                        propertyType
                )
        );
    }

    /**
     * Get property details and room types.
     *
     * Example:
     *
     * GET /api/v1/stays/1
     */
    @GetMapping("/{propertyId}")
    public ResponseEntity<PropertyDetailResponse> getPropertyById(
            @PathVariable Long propertyId
    ) {

        return ResponseEntity.ok(
                propertyService.getPropertyById(
                        propertyId
                )
        );
    }

    /**
     * Check real-time room availability for a property.
     *
     * Example:
     *
     * GET /api/v1/stays/1/availability?checkIn=2026-10-01&checkOut=2026-10-04&guests=2&rooms=1
     */
    @GetMapping("/{propertyId}/availability")
    public ResponseEntity<List<AvailabilityResponse>> checkAvailability(
            @PathVariable Long propertyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(defaultValue = "1") Integer guests,
            @RequestParam(defaultValue = "1") Integer rooms
    ) {

        return ResponseEntity.ok(
                availabilityService.checkAvailability(
                        propertyId,
                        checkIn,
                        checkOut,
                        guests,
                        rooms
                )
        );
    }
}
