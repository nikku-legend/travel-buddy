package com.Travel.Buddy.dto.property;

import com.Travel.Buddy.entity.PolicyType;

import java.math.BigDecimal;
import java.util.List;

/**
 * The editable view of a property's detail content. (FR-05)
 *
 * <p>Separate from {@link PropertyDetailResponse} because the two
 * answer different questions. The public payload is what a
 * traveller reads and deliberately hides row ids; this one is what
 * a partner's edit form binds to and therefore has to carry them.
 *
 * <p>Returning both views from a mutation is what makes it possible
 * to notice, at the moment of an edit, that the partner's idea of
 * the page and the traveller's view have come apart.
 */
public record PropertyContentResponse(
        Long propertyId,
        List<EditableAmenity> amenities,
        List<EditableImage> images,
        List<EditablePolicy> policies,
        List<EditableCancellationTerm> cancellationTerms
) {

    public record EditableAmenity(
            Integer amenityId,
            String code,
            String label,
            String category,
            String iconName,
            boolean free,
            boolean requiresBooking,
            String note
    ) {
    }

    public record EditableImage(
            Long imageId,
            String imageUrl,
            String altText,
            int sortOrder,
            boolean cover
    ) {
    }

    public record EditablePolicy(
            Long policyId,
            PolicyType policyType,
            String title,
            String description,
            int sortOrder
    ) {
    }

    public record EditableCancellationTerm(
            Long termId,
            int daysBeforeCheckIn,
            BigDecimal refundPercent,
            BigDecimal penaltyPercent,
            int minNightsCharge,
            String description
    ) {
    }
}