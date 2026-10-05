package com.Travel.Buddy.dto.guide;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record GuideProfileRequest(
        @NotNull(message = "State ID is required")
        Integer stateId,

        @NotNull(message = "Daily rate is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Daily rate must be positive")
        BigDecimal dailyRate,

        String bio,

        Integer yearsOfExperience,

        List<String> languages
) {}
