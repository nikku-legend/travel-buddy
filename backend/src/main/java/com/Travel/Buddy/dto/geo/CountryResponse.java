package com.Travel.Buddy.dto.geo;

public record CountryResponse(
        Integer countryId,
        String name,
        String isoCode
) {
}