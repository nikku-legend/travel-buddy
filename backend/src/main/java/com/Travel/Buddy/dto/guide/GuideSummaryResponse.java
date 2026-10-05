package com.Travel.Buddy.dto.guide;

import java.math.BigDecimal;
import java.util.List;

public record GuideSummaryResponse(
        Long guideId,
        String fullName,
        String email,
        String phoneNumber,
        Integer stateId,
        String stateName,
        BigDecimal dailyRate,
        String currencyCode,
        String bio,
        Integer yearsOfExperience,
        BigDecimal rating,
        Boolean isVerified,
        List<String> languages,

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
