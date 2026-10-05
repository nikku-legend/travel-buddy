package com.Travel.Buddy.dto.property;

import com.Travel.Buddy.entity.PropertyType;

import java.math.BigDecimal;

public record PropertyResponse(

        Long propertyId,

        Integer stateId,

        String stateName,

        String name,

        PropertyType propertyType,

        String address,

        String description,

        BigDecimal latitude,

        BigDecimal longitude,

        Boolean verified

) {
}
