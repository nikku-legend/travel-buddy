package com.Travel.Buddy.dto.cab;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookRideRequest(
        @NotNull(message = "Cab ID is required")
        Long cabId,

        @NotBlank(message = "Pickup location is required")
        String pickupLocation,

        @NotBlank(message = "Drop location is required")
        String dropLocation,

        @NotNull(message = "Pickup time is required")
        LocalDateTime pickupTime,

        @NotNull(message = "Distance in km is required")
        @DecimalMin(value = "0.1")
        BigDecimal distanceKm
) {}
