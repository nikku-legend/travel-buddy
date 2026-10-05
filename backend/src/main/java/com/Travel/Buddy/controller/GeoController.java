package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.geo.CountryResponse;
import com.Travel.Buddy.dto.geo.StateResponse;
import com.Travel.Buddy.entity.RegionZone;
import com.Travel.Buddy.service.geo.GeoService;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/geo")
public class GeoController {

    private final GeoService geoService;

    public GeoController(
            GeoService geoService
    ) {
        this.geoService = geoService;
    }

    @GetMapping("/countries")
    public List<CountryResponse> getCountries() {

        return geoService.getCountries();
    }

    @GetMapping("/states")
    public List<StateResponse> getStates(

            @RequestParam Integer countryId,

            @RequestParam(required = false)
            RegionZone region
    ) {

        return geoService.getStates(
                countryId,
                region
        );
    }
}