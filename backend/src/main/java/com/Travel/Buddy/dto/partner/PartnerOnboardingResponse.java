package com.Travel.Buddy.dto.partner;

import com.Travel.Buddy.entity.PartnerType;

/**
 * Where a newly logged-in applicant should be sent, so a guest who
 * clicked "Become a Partner" continues the application after login
 * instead of being dropped on the homepage.
 */
public record PartnerOnboardingResponse(
        PartnerType partnerType,
        String destination,
        String message
) {
}
