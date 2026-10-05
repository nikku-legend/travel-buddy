package com.Travel.Buddy.dto.cab;

import com.Travel.Buddy.entity.VehicleType;
import java.math.BigDecimal;

public record CabResponse(
        Long cabId,
        String vehicleName,
        VehicleType vehicleType,
        String registrationNumber,
        Integer seatingCapacity,
        String driverName,
        String driverPhone,
        Integer stateId,
        String stateName,
        BigDecimal pricePerKm,
        BigDecimal baseFare,
        BigDecimal rating,
        Boolean isAvailable,
        Boolean isVerified,

        /**
         * How many published reviews the rating rests on.
         *
         * <p>Shipped beside the rating rather than instead of it,
         * because a 5.00 from one review and a 5.00 from two
         * hundred are not the same claim, and a page that shows
         * the first without the second is overstating its
         * evidence.
         */
        Integer reviewCount
) {}
