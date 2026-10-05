package com.Travel.Buddy.dto.discovery;

import java.math.BigDecimal;
import java.util.List;

/**
 * The whole Home page in one response. (SRS 2.3 section 2)
 *
 * <p>Assembled server-side on purpose. Section 2.1 lists ten
 * sections; a frontend that fetched each one separately would
 * make ten round trips on first paint and could render a page
 * whose sections disagree with each other, because each would
 * have been read at a slightly different moment.
 */
public record HomeDiscoveryResponse(

        HeroSection hero,

        List<AttractionCard> featuredAttractions,

        List<ZoneCard> zones,

        List<CityCard> cities,

        List<AttractionCard> popularAttractions,

        List<StayCard> featuredStays,

        List<ExperienceCard> experiences,

        InspirationSection inspiration,

        /*
         * The persistent CTA from section 2.1, expressed as data
         * so the route and label live in one place rather than
         * being duplicated across every Home layout.
         */
        PlannerCta plannerCta
) {
    public record HeroSection(
            String headline,
            String subline,
            String searchPlaceholder,
            String plannerCtaLabel,
            String plannerCtaRoute
    ) {
    }

    /**
     * An attraction card built to the section 2.2 standard:
     * image, name, city context, category, description, visit
     * duration, map coordinates, and the actions a card offers.
     */
    public record AttractionCard(
            Long placeId,
            String name,
            String category,
            String description,
            String imageUrl,
            String cityName,
            String stateName,
            String visitDuration,
            BigDecimal entryFee,
            String currency,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean featured
    ) {
    }

    public record ZoneCard(
            String zone,
            String label,
            int cityCount,
            int attractionCount
    ) {
    }

    public record CityCard(
            Long cityId,
            String name,
            String slug,
            String stateName,
            String regionZone,
            boolean capital,
            int attractionCount,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }

    public record StayCard(
            Long propertyId,
            String name,
            String description,
            String imageUrl,
            String cityName,
            BigDecimal latitude,
            BigDecimal longitude,

            /*
             * Null when the property has no published reviews yet.
             * An unrated property is shown honestly rather than
             * given a flattering default.
             */
            BigDecimal rating,

            int reviewCount,
            boolean featured
    ) {
    }

    public record ExperienceCard(
            Long guideId,
            String name,
            String bio,
            String stateName,
            BigDecimal dailyRate,
            String currency,
            BigDecimal rating,
            Integer yearsOfExperience,
            boolean verified,

            /**
             * How many published reviews the rating rests on.
             *
             * <p>Shipped beside the rating because a 5.00 from one
             * review and a 5.00 from two hundred are not the same
             * claim, and a card that shows the first without the
             * second overstates its evidence.
             */
            int reviewCount
    ) {
    }

    public record InspirationSection(
            String title,
            List<InspirationCard> collections
    ) {
    }

    public record InspirationCard(
            String title,
            String subtitle,
            String cityName,
            int attractionCount,
            String imageUrl
    ) {
    }

    public record PlannerCta(
            String label,
            String route,
            String description
    ) {
    }
}