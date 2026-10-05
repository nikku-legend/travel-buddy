package com.Travel.Buddy.dto.cab;

import com.Travel.Buddy.entity.RideStatus;
import com.Travel.Buddy.entity.VehicleType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RideResponse(
        Long rideId,
        Long cabId,
        String vehicleName,
        VehicleType vehicleType,
        String registrationNumber,
        String driverName,
        String driverPhone,
        String pickupLocation,
        String dropLocation,
        LocalDateTime pickupTime,
        BigDecimal distanceKm,
        BigDecimal fareAmount,
        RideStatus status,
        String otpCode,
        LocalDateTime createdAt
) {}
