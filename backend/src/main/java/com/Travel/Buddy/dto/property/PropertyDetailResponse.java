package com.Travel.Buddy.dto.property;

import com.Travel.Buddy.entity.PolicyType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the hotel detail page needs. (SRS 2.3 section 6.2)
 *
 * <p>One request rather than six. The detail page shows
 * overview, images, location, amenities, room types, occupancy,
 * prices, policies, cancellation terms, ratings and
 * availability together, and fetching them separately would let a
 * traveller see a price from one call beside an availability
 * from another.
 */
public record PropertyDetailResponse(

        Long propertyId,

        /*
         * Retained from the response this supersedes, so the
         * existing Stay Details page keeps working unchanged.
         * Section 6.2 adds sections; it must not remove any.
         */
        Integer stateId,

        String name,

        String description,

        /* Location and map. */
        String address,

        String cityName,

        String stateName,

        com.Travel.Buddy.entity.PropertyType propertyType,

        BigDecimal latitude,

        BigDecimal longitude,

        /* Images, cover first. */
        List<Image> images,

        /* Grouped so the page can section them. */
        List<AmenityGroup> amenities,

        /* Room types with occupancy and pricing. */
        List<RoomOption> roomTypes,

        /* Trust and quality. */
        BigDecimal rating,

        int reviewCount,

        boolean verified,

        List<Policy> policies,

        List<CancellationTier> cancellationTerms,

        /*
         * Availability for the dates actually asked about, when
         * dates were supplied. Null otherwise, because "available"
         * without a date range would be a meaningless claim.
         */
        Availability availability,

        /*
         * Derived from the most generous tier. Null when the
         * property has published no terms, which the page renders
         * as non-refundable rather than as a silent blank.
         */
        Integer mostGenerousCancellationDays
) {
    public record Image(
            Long imageId,
            String imageUrl,
            String altText,
            boolean cover
    ) {
    }

    public record AmenityGroup(
            String category,
            List<Entry> amenities
    ) {
    }

    public record Entry(
            String code,
            String label,
            String iconName,
            boolean free,
            boolean requiresBooking,
            String note
    ) {
    }

    public record RoomOption(
            Long roomTypeId,
            String categoryName,
            int maxOccupancy,
            BigDecimal basePrice,
            String currency,
            int totalInventory,
            boolean active
    ) {
    }

    public record Policy(
            PolicyType policyType,
            String title,
            String description
    ) {
    }

    /**
     * {@code refundPercent} is what the traveller gets back.
     */
    public record CancellationTier(
            int daysBeforeCheckIn,
            BigDecimal refundPercent,
            BigDecimal penaltyPercent,
            int minNightsCharge,
            String description
    ) {
    }

    /**
     * Room counts for the requested range. Null when no dates
     * were given, which is deliberately different from zero.
     */
    public record Availability(
            LocalDate checkIn,
            LocalDate checkOut,
            int nights,
            boolean available,
            String reason
    ) {
    }
}