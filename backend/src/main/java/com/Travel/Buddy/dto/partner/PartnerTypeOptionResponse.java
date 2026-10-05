package com.Travel.Buddy.dto.partner;

import com.Travel.Buddy.entity.PartnerType;

import java.util.List;

/**
 * Everything the "Become a Partner" type-selection screen needs, so the
 * frontend never hard-codes partner metadata.
 */
public record PartnerTypeOptionResponse(
        PartnerType type,
        String label,
        String description,
        String icon,
        List<PartnerTypeRequirement> requirements,
        List<KycDocumentTypeResponse> requiredDocuments
) {

    public record PartnerTypeRequirement(
            String field,
            String label,
            boolean required
    ) {
    }
}