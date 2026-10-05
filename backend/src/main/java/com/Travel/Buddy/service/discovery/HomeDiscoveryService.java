package com.Travel.Buddy.service.discovery;

import com.Travel.Buddy.dto.discovery.HomeDiscoveryResponse;
import com.Travel.Buddy.dto.discovery.HomeDiscoveryResponse.AttractionCard;
import com.Travel.Buddy.dto.discovery.HomeDiscoveryResponse.CityCard;
import com.Travel.Buddy.dto.discovery.HomeDiscoveryResponse.ExperienceCard;
import com.Travel.Buddy.dto.discovery.HomeDiscoveryResponse.HeroSection;
import com.Travel.Buddy.dto.discovery.HomeDiscoveryResponse.InspirationCard;
import com.Travel.Buddy.dto.discovery.HomeDiscoveryResponse.InspirationSection;
import com.Travel.Buddy.dto.discovery.HomeDiscoveryResponse.PlannerCta;
import com.Travel.Buddy.dto.discovery.HomeDiscoveryResponse.StayCard;
import com.Travel.Buddy.dto.discovery.HomeDiscoveryResponse.ZoneCard;
import com.Travel.Buddy.entity.City;
import com.Travel.Buddy.entity.Guide;
import com.Travel.Buddy.entity.Property;
import com.Travel.Buddy.entity.PropertyStatus;
import com.Travel.Buddy.entity.RegionZone;
import com.Travel.Buddy.entity.ReviewSummary;
import com.Travel.Buddy.entity.ReviewTargetType;
import com.Travel.Buddy.entity.TouristPlace;
import com.Travel.Buddy.repository.CityRepository;
import com.Travel.Buddy.repository.GuideRepository;
import com.Travel.Buddy.repository.PropertyRepository;
import com.Travel.Buddy.repository.ReviewSummaryRepository;
import com.Travel.Buddy.repository.TouristPlaceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Home discovery. (SRS 2.3 section 2)
 *
 * <p>Read-only and public. Section 2 turns Home from a marketing
 * landing page into the surface that introduces destinations,
 * attractions, stays and experiences, and from which the Custom
 * Trip Planner is entered.
 *
 * <p>Two decisions worth stating:
 *
 * <ol>
 *   <li><strong>Only verified, active content is shown.</strong>
 *       A rejected or suspended listing appearing on the front
 *       page would be the worst possible advertisement for the
 *       moderation work already built.</li>
 *   <li><strong>Ratings are never invented.</strong> An unrated
 *       property or guide reports a null rating rather than a
 *       flattering default, because a number on a discovery card
 *       reads as evidence.</li>
 * </ol>
 */
@Service
public class HomeDiscoveryService {

    /**
     * Caps per section. Home is a discovery surface, not a
     * catalogue; a traveller choosing between eight options is
     * making a decision, a traveller scrolling forty is not.
     */
    private static final int MAX_CARDS = 8;

    private final CityRepository cityRepository;
    private final TouristPlaceRepository placeRepository;
    private final PropertyRepository propertyRepository;
    private final GuideRepository guideRepository;
    private final ReviewSummaryRepository reviewSummaryRepository;

    public HomeDiscoveryService(
            CityRepository cityRepository,
            TouristPlaceRepository placeRepository,
            PropertyRepository propertyRepository,
            GuideRepository guideRepository,
            ReviewSummaryRepository reviewSummaryRepository
    ) {
        this.cityRepository = cityRepository;
        this.placeRepository = placeRepository;
        this.propertyRepository = propertyRepository;
        this.guideRepository = guideRepository;
        this.reviewSummaryRepository = reviewSummaryRepository;
    }

    @Transactional(readOnly = true)
    public HomeDiscoveryResponse home() {
        List<City> cities = cityRepository.findAll();
        List<TouristPlace> places = liveAttractions();
        List<Property> stays = bookableStays();

        return new HomeDiscoveryResponse(
                hero(),
                attractionCards(featured(places)),
                zoneCards(cities, places),
                cityCards(cities, places),
                attractionCards(places),
                stayCards(stays),
                experienceCards(),
                inspiration(cities, places),
                new PlannerCta(
                        "Start Custom Trip",
                        "/trip-planner",
                        "Tell us who is travelling and where you want "
                                + "to go, and we will build a trip with you."
                )
        );
    }

    /* ============================================================
     * 2.1  HERO
     * ============================================================ */

    private HeroSection hero() {
        return new HeroSection(
                "Find your next journey",
                "Destinations, stays, guides and experiences across "
                        + "one place, planned around you.",
                "Search destinations, places or stays",
                "Start Custom Trip",
                "/trip-planner"
        );
    }

    /* ============================================================
     * 2.2  ATTRACTION CARDS
     * ============================================================ */

    private List<TouristPlace> liveAttractions() {
        return placeRepository.findAll().stream()
                .filter(place -> !Boolean.FALSE.equals(
                        place.getActive()))
                .toList();
    }

    private List<TouristPlace> featured(
            List<TouristPlace> places
    ) {
        return places.stream()
                .filter(TouristPlace::isFeatured)
                .limit(MAX_CARDS)
                .toList();
    }

    /**
     * Builds a card to the section 2.2 standard. Every field the
     * standard names is either populated or deliberately null, so
     * a card can never present a placeholder as real information.
     */
    private List<AttractionCard> attractionCards(
            List<TouristPlace> places
    ) {
        return places.stream()
                .limit(MAX_CARDS)
                .map(place -> new AttractionCard(
                        place.getPlaceId(),
                        place.getName(),
                        place.getCategory(),
                        place.getDescription(),
                        place.getImageUrl(),
                        place.getCity() == null
                                ? null
                                : place.getCity().getName(),
                        place.getState() == null
                                ? null
                                : place.getState().getName(),
                        place.visitDurationLabel(),
                        place.getEntryFee(),
                        place.getCurrency(),
                        place.getLatitude(),
                        place.getLongitude(),
                        place.isFeatured()
                ))
                .toList();
    }

    /* ============================================================
     * 2.1  EXPLORE BY ZONE
     * ============================================================ */

    private List<ZoneCard> zoneCards(
            List<City> cities,
            List<TouristPlace> places
    ) {
        Map<RegionZone, int[]> counts = new LinkedHashMap<>();

        for (City city : cities) {
            if (city.getState() == null
                    || city.getState().getRegionZone() == null) {
                continue;
            }
            counts.computeIfAbsent(
                    city.getState().getRegionZone(),
                    zone -> new int[2]
            )[0]++;
        }

        for (TouristPlace place : places) {
            if (place.getState() == null
                    || place.getState().getRegionZone() == null) {
                continue;
            }
            int[] bucket = counts.computeIfAbsent(
                    place.getState().getRegionZone(),
                    zone -> new int[2]
            );
            bucket[1]++;
        }

        return counts.entrySet().stream()
                .map(entry -> new ZoneCard(
                        entry.getKey().name(),
                        labelFor(entry.getKey()),
                        entry.getValue()[0],
                        entry.getValue()[1]
                ))
                .sorted(
                        Comparator.comparingInt(
                                ZoneCard::cityCount
                        ).reversed()
                )
                .toList();
    }

    private String labelFor(RegionZone zone) {
        return switch (zone) {
            case NORTH -> "North India";
            case SOUTH -> "South India";
            case EAST -> "East India";
            case WEST -> "West India";
            case CENTRAL -> "Central India";
            default -> zone.name();
        };
    }

    /* ============================================================
     * 2.1  CITIES AND PLACES
     * ============================================================ */

    private List<CityCard> cityCards(
            List<City> cities,
            List<TouristPlace> places
    ) {
        return cities.stream()
                .filter(city -> city.getState() != null)
                .sorted(
                        Comparator.comparingInt(
                                (City city) -> countPlacesIn(
                                        places, city
                                )
                        ).reversed()
                                .thenComparing(City::getName)
                )
                .limit(MAX_CARDS)
                .map(city -> new CityCard(
                        city.getCityId(),
                        city.getName(),
                        city.getSlug(),
                        city.getState().getName(),
                        city.getState().getRegionZone() == null
                                ? null
                                : city.getState()
                                .getRegionZone()
                                .name(),
                        city.isCapital(),
                        countPlacesIn(places, city),
                        city.getLatitude(),
                        city.getLongitude()
                ))
                .toList();
    }

    private int countPlacesIn(
            List<TouristPlace> places,
            City city
    ) {
        return (int) places.stream()
                .filter(place -> place.getCity() != null
                        && place.getCity().getCityId()
                        .equals(city.getCityId()))
                .count();
    }

    /* ============================================================
     * 2.1  STAY DISCOVERY
     * ============================================================ */

    /**
     * Only live, verified properties. Showing a suspended or
     * not-yet-approved listing on the front page would undo the
     * work the approval pipeline exists to do.
     */
    private List<Property> bookableStays() {
        return propertyRepository.findAll().stream()
                .filter(property -> Boolean.TRUE.equals(
                        property.getVerified()))
                .filter(property -> !Boolean.FALSE.equals(
                        property.getActive()))
                .filter(property -> property.getStatus() != null
                        && property.getStatus().isLive())
                .toList();
    }

    private List<StayCard> stayCards(List<Property> stays) {
        List<Property> ordered = new ArrayList<>(stays);

        /*
         * Featured first, then rated, then rated highest. A
         * property with no reviews still appears, just below
         * everything that has earned its position.
         */
        ordered.sort(
                Comparator.comparing(
                                (Property p) -> !p.isFeatured())
                        .thenComparing(
                                p -> ratingOf(p) == null
                                        ? 1 : 0)
                        .thenComparing(
                                p -> ratingOf(p) == null
                                        ? java.math.BigDecimal.ZERO
                                        : ratingOf(p),
                                Comparator.reverseOrder())
        );

        return ordered.stream()
                .limit(MAX_CARDS)
                .map(property -> {
                    ReviewSummary summary = summaryOf(
                            ReviewTargetType.HOTEL,
                            property.getPropertyId()
                    );

                    return new StayCard(
                            property.getPropertyId(),
                            property.getName(),
                            property.getDescription(),
                            null,
                            property.getCity() == null
                                    ? null
                                    : property.getCity().getName(),
                            property.getLatitude(),
                            property.getLongitude(),
                            summary == null
                                    ? null
                                    : summary.getAverageRating(),
                            summary == null
                                    ? 0
                                    : summary.getReviewCount(),
                            property.isFeatured()
                    );
                })
                .toList();
    }

    private BigDecimal ratingOf(Property property) {
        ReviewSummary summary = summaryOf(
                ReviewTargetType.HOTEL,
                property.getPropertyId()
        );
        return summary == null
                ? null
                : summary.getAverageRating();
    }

    private ReviewSummary summaryOf(
            ReviewTargetType type,
            Long targetId
    ) {
        return reviewSummaryRepository
                .findByTargetTypeAndTargetId(type, targetId)
                .orElse(null);
    }

    /* ============================================================
     * 2.1  LOCAL EXPERIENCES
     * ============================================================ */

    private List<ExperienceCard> experienceCards() {
        /*
         * Rated first, then rated highest, exactly as stayCards
         * does.
         *
         * This used to sort on the guide's own rating column,
         * which nothing ever wrote and which was declared NOT NULL
         * DEFAULT 5.00. Every guide therefore tied at a perfect
         * 5.00, so the order below limit(MAX_CARDS) was whatever
         * the repository happened to return, and the "top guides"
         * section was an arbitrary selection presented as a
         * ranking.
         */
        List<Guide> ordered = new ArrayList<>(
                guideRepository.findAll()
        );

        ordered.removeIf(guide -> Boolean.FALSE.equals(
                guide.getActive()));
        ordered.removeIf(guide -> !Boolean.TRUE.equals(
                guide.getVerified()));

        ordered.sort(
                Comparator.comparing(
                                (Guide g) -> guideRating(g) == null
                                        ? 1 : 0)
                        .thenComparing(
                                g -> guideRating(g) == null
                                        ? java.math.BigDecimal.ZERO
                                        : guideRating(g),
                                Comparator.reverseOrder())
        );

        return ordered.stream()
                .limit(MAX_CARDS)
                .map(guide -> {
                    ReviewSummary summary = guideSummary(guide);

                    return new ExperienceCard(
                            guide.getGuideId(),
                            guide.getUser() == null
                                    ? null
                                    : guide.getUser().getFullName(),
                            guide.getBio(),
                            guide.getState() == null
                                    ? null
                                    : guide.getState().getName(),
                            guide.getDailyRate(),
                            guide.getCurrencyCode(),
                            guideRating(guide),
                            guide.getYearsOfExperience(),
                            Boolean.TRUE.equals(guide.getVerified()),
                            summary == null
                                    || summary.getReviewCount() == null
                                    ? 0
                                    : summary.getReviewCount()
                    );
                })
                .toList();
    }

    /**
     * The published guide rating, or null when there are none.
     */
    private BigDecimal guideRating(Guide guide) {
        ReviewSummary summary = guideSummary(guide);

        return summary == null || summary.getReviewCount() == 0
                ? null
                : summary.getAverageRating();
    }

    private ReviewSummary guideSummary(Guide guide) {
        return summaryOf(
                ReviewTargetType.GUIDE,
                guide.getGuideId()
        );
    }

    /* ============================================================
     * 2.1  TRAVEL INSPIRATION
     * ============================================================ */

    /**
     * Visual collections that lead into destination or planner
     * flows. Built from the cities that actually have content,
     * because an inspiration card pointing at an empty city is a
     * dead end.
     */
    private InspirationSection inspiration(
            List<City> cities,
            List<TouristPlace> places
    ) {
        List<InspirationCard> collections = new ArrayList<>();

        for (City city : cities) {
            int count = countPlacesIn(places, city);

            if (count == 0) {
                continue;
            }

            String primary = places.stream()
                    .filter(p -> p.getCity() != null
                            && p.getCity().getCityId()
                            .equals(city.getCityId()))
                    .findFirst()
                    .map(TouristPlace::getImageUrl)
                    .orElse(null);

            collections.add(
                    new InspirationCard(
                            city.getName(),
                            count + (count == 1
                                    ? " place to explore"
                                    : " places to explore"),
                            city.getName(),
                            count,
                            primary
                    )
            );
        }

        return new InspirationSection(
                "Travel inspiration",
                collections.stream()
                        .limit(MAX_CARDS)
                        .toList()
        );
    }
}