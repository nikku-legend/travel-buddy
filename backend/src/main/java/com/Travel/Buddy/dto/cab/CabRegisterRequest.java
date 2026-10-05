package com.Travel.Buddy.dto.cab;

import com.Travel.Buddy.entity.VehicleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CabRegisterRequest(
        @NotNull(message = "State ID is required")
        Integer stateId,

        @NotBlank(message = "Vehicle name is required")
        String vehicleName,

        @NotNull(message = "Vehicle type is required")
        VehicleType vehicleType,

        @NotBlank(message = "Registration number is required")
        String registrationNumber,

        @NotNull(message = "Seating capacity is required")
        Integer seatingCapacity,

        @NotBlank(message = "Driver name is required")
        String driverName,

        @NotBlank(message = "Driver phone is required")
        String driverPhone,

        @NotNull(message = "Price per km is required")
        @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal pricePerKm,

        @NotNull(message = "Base fare is required")
        @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal baseFare
) {}
