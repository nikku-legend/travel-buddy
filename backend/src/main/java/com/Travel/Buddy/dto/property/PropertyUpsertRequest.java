package com.Travel.Buddy.dto.property;

import com.Travel.Buddy.entity.PropertyType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Property details submitted by a hotel partner.
 *
 * <p>Creating a property always yields a DRAFT. Nothing here can make a
 * property live; that requires an explicit submit plus admin approval.
 */
public record PropertyUpsertRequest(

        @NotBlank(message = "Property name is required")
        @Size(max = 200, message = "Property name must not exceed 200 characters")
        String name,

        @NotNull(message = "Property type is required")
        PropertyType propertyType,

        @NotNull(message = "State is required")
        Integer stateId,

        @NotBlank(message = "Address is required")
        @Size(max = 500, message = "Address must not exceed 500 characters")
        String address,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        BigDecimal longitude
) {
}