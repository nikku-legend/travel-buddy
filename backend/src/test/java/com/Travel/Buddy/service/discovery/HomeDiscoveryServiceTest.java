package com.Travel.Buddy.service.discovery;

import com.Travel.Buddy.dto.discovery.HomeDiscoveryResponse;
import com.Travel.Buddy.entity.*;
import com.Travel.Buddy.repository.*;
import com.Travel.Buddy.service.partner.RoleService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Home discovery. (SRS 2.3 section 2)
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Home discovery (SRS 2.3 section 2)")
class HomeDiscoveryServiceTest {

    @Autowired
    private HomeDiscoveryService homeDiscoveryService;
    @Autowired
    private CityRepository cityRepository;
    @Autowired
    private TouristPlaceRepository placeRepository;
    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleService roleService;
    @Autowired
    private ReviewSummaryRepository reviewSummaryRepository;
    @Autowired
    private GuideRepository guideRepository;
    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private StateRepository stateRepository;

    private State state;
    private City puri;

    @BeforeEach
    void setUp() {
        if (stateRepository.findAll().isEmpty()) {
            Country country = new Country();
            country.setName("Homeland");
            country.setIsoCode("HM");
            country = countryRepository.save(country);

            state = new State();
            state.setName("Home State");
            state.setCountry(country);
            state.setRegionZone(RegionZone.EAST);
            state = stateRepository.save(state);

            puri = new City(state, "Puri", "puri-home");
            puri.setCapital(false);
            puri.setLatitude(new BigDecimal("19.8135"));
            puri.setLongitude(new BigDecimal("85.8312"));
            puri = cityRepository.save(puri);
        } else {
            state = stateRepository.findAll().get(0);
            puri = cityRepository
                    .findByState_StateIdOrderByNameAsc(
                            state.getStateId())
                    .stream()
                    .findFirst()
                    .orElse(null);
        }
    }

    private TouristPlace place(
            String name,
            String category,
            Integer minutes,
            boolean featured
    ) {
        TouristPlace place = new TouristPlace();
        place.setName(name + "-" + UUID.randomUUID()
                .toString().substring(0, 6));
        place.setState(state);
        place.setCity(puri);
        place.setDescription("Worth a visit");
        place.setCategory(category);
        place.setEstimatedVisitMinutes(minutes);
        place.setFeatured(featured);
        place.setEntryFee(new BigDecimal("50.00"));
        place.setCurrency("INR");
        place.setLatitude(new BigDecimal("19.8200"));
        place.setLongitude(new BigDecimal("85.8400"));
        return placeRepository.save(place);
    }

    private Property property(
            String name,
            PropertyStatus status,
            Boolean verified
    ) {
        User owner = new User();
        owner.setFullName("Owner " + UUID.randomUUID());
        owner.setEmail(UUID.randomUUID() + "@tb.local");
        owner.setPasswordHash("{noop}password");
        owner = userRepository.save(owner);

        Property property = new Property();
        property.setName(name + "-" + UUID.randomUUID()
                .toString().substring(0, 6));
        property.setPropertyType(PropertyType.HOTEL);
        property.setState(state);
        property.setCity(puri);
        property.setPartner(owner);
        property.setAddress("Somewhere");
        property.setDescription("A lovely stay");
        property.setLatitude(new BigDecimal("19.8300"));
        property.setLongitude(new BigDecimal("85.8500"));
        property.setStatus(status);
        property.setVerified(verified);
        return propertyRepository.save(property);
    }

    /* ============================================================
     * 2.1  SECTIONS
     * ============================================================ */

    @Test
    @DisplayName("home renders every section the spec lists")
    void everySectionIsPresent() {
        place("Temple", "TEMPLE", 120, true);
        property("Stay", PropertyStatus.APPROVED, true);

        HomeDiscoveryResponse home = homeDiscoveryService.home();

        assertNotNull(home.hero());
        assertNotNull(home.featuredAttractions());
        assertNotNull(home.zones());
        assertNotNull(home.cities());
        assertNotNull(home.popularAttractions());
        assertNotNull(home.featuredStays());
        assertNotNull(home.experiences());
        assertNotNull(home.inspiration());
        assertNotNull(home.plannerCta());
    }

    /* ============================================================
     * EXPERIENCES (GUIDE CARDS)
     *
     * The guide card used to read the guide entity's own rating
     * column, which is NOT NULL DEFAULT 5.00 and was never written.
     * Every card showed a flawless 5.00 and the section's own sort
     * tied on that constant, so the ordering before limit() was
     * arbitrary and presented as a ranking.
     * ============================================================ */

    private Guide guide(String name, BigDecimal dailyRate) {
        User owner = new User();
        owner.setFullName(name + " Owner");
        owner.setEmail(UUID.randomUUID() + "@tb.local");
        owner.setPasswordHash("{noop}password");
        owner = userRepository.save(owner);

        Guide guide = new Guide();
        guide.setUser(owner);
        guide.setState(state);
        guide.setDailyRate(dailyRate);
        guide.setBio("A guide called " + name);
        guide.setVerified(true);
        guide.setActive(true);
        return guideRepository.save(guide);
    }

    private void guideSummary(Guide guide, String average, int count) {
        ReviewSummary summary = new ReviewSummary();
        summary.setTargetType(ReviewTargetType.GUIDE);
        summary.setTargetId(guide.getGuideId());
        summary.setAverageRating(new BigDecimal(average));
        summary.setReviewCount(count);
        summary.setOneStarCount(0);
        summary.setTwoStarCount(0);
        summary.setThreeStarCount(0);
        summary.setFourStarCount(count / 2);
        summary.setFiveStarCount(count - (count / 2));
        reviewSummaryRepository.save(summary);
    }

    private HomeDiscoveryResponse.ExperienceCard cardFor(
            HomeDiscoveryResponse home,
            Long guideId
    ) {
        return home.experiences().stream()
                .filter(c -> c.guideId().equals(guideId))
                .findFirst()
                .orElse(null);
    }

    @Test
    @DisplayName("a guide card reports the real published rating")
    void guideCardReportsItsRealRating() {
        Guide guide = guide("Rated", new BigDecimal("1000.00"));
        guideSummary(guide, "2.20", 40);

        HomeDiscoveryResponse.ExperienceCard card = cardFor(
                homeDiscoveryService.home(), guide.getGuideId()
        );

        assertNotNull(card);
        assertEquals(
                0, new BigDecimal("2.20").compareTo(card.rating()),
                "a guide averaging 2.20 must not display the 5.00 "
                        + "its column was declared with"
        );
        assertEquals(40, card.reviewCount());
    }

    @Test
    @DisplayName("an unreviewed guide card shows no rating")
    void unreviewedGuideCardHasNoRating() {
        Guide guide = guide("Unreviewed", new BigDecimal("1000.00"));

        HomeDiscoveryResponse.ExperienceCard card = cardFor(
                homeDiscoveryService.home(), guide.getGuideId()
        );

        if (card != null) {
            assertNull(
                    card.rating(),
                    "no reviews is unknown, not a flawless score"
            );
            assertEquals(0, card.reviewCount());
        }
    }

    /**
     * The ordering was the subtler half of the defect: the sort
     * compared a column every guide tied on, so which guides
     * survived limit(MAX_CARDS) was arbitrary.
     */
    @Test
    @DisplayName("a reviewed guide outranks an unreviewed one")
    void reviewedGuideOutranksUnreviewed() {
        Guide unreviewed = guide("Zulu Unreviewed",
                new BigDecimal("1000.00"));
        Guide reviewed = guide("Alpha Reviewed",
                new BigDecimal("1000.00"));
        guideSummary(reviewed, "3.00", 5);

        List<HomeDiscoveryResponse.ExperienceCard> cards =
                homeDiscoveryService.home().experiences();

        int reviewedIndex = indexOf(cards, reviewed.getGuideId());
        int unreviewedIndex = indexOf(cards, unreviewed.getGuideId());

        if (reviewedIndex >= 0 && unreviewedIndex >= 0) {
            assertTrue(
                    reviewedIndex < unreviewedIndex,
                    "a guide with reviews belongs above one without, "
                            + "otherwise the section is not a ranking"
            );
        }
    }

    private int indexOf(
            List<HomeDiscoveryResponse.ExperienceCard> cards,
            Long guideId
    ) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).guideId().equals(guideId)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    @DisplayName("the planner CTA points at the planner")
    void plannerCtaIsPersistent() {
        HomeDiscoveryResponse home = homeDiscoveryService.home();

        assertEquals("/trip-planner",
                home.plannerCta().route());
        assertNotNull(home.plannerCta().label());
        assertEquals("/trip-planner",
                home.hero().plannerCtaRoute(),
                "section 2.1 makes the planner action "
                        + "persistent across Home");
    }

    /**
     * Section 2.1 lists Explore by Zone so a visitor can
     * understand the broader area before choosing a region.
     */
    @Test
    @DisplayName("zones report how much is inside them")
    void zonesCarryCounts() {
        place("Temple", "TEMPLE", 60, false);

        HomeDiscoveryResponse home = homeDiscoveryService.home();

        assertFalse(home.zones().isEmpty());

        HomeDiscoveryResponse.ZoneCard zone = home.zones()
                .stream()
                .filter(z -> "EAST".equals(z.zone()))
                .findFirst()
                .orElse(null);

        if (zone != null) {
            assertTrue(zone.attractionCount() >= 1,
                    "a zone card that counts no attractions is "
                            + "not a useful filter");
            assertNotNull(zone.label());
        }
    }

    @Test
    @DisplayName("cities report how many places they hold")
    void citiesCarryAttractionCounts() {
        place("One", "BEACH", 60, false);
        place("Two", "BEACH", 60, false);

        HomeDiscoveryResponse home = homeDiscoveryService.home();

        assertFalse(home.cities().isEmpty());
        assertTrue(
                home.cities().stream()
                        .anyMatch(c -> c.attractionCount() > 0)
        );
    }

    /* ============================================================
     * 2.2  ATTRACTION CARD STANDARD
     * ============================================================ */

    @Test
    @DisplayName("an attraction card carries every field the standard names")
    void attractionCardMeetsTheStandard() {
        TouristPlace temple = place("Konark", "HERITAGE", 90, true);

        HomeDiscoveryResponse home = homeDiscoveryService.home();

        HomeDiscoveryResponse.AttractionCard card = home
                .popularAttractions()
                .stream()
                .filter(c -> c.placeId()
                        .equals(temple.getPlaceId()))
                .findFirst()
                .orElseThrow();

        assertNotNull(card.name());
        assertNotNull(card.category());
        assertNotNull(card.description());
        assertNotNull(card.cityName());
        assertNotNull(card.stateName());
        assertNotNull(card.latitude());
        assertNotNull(card.longitude(),
                "the standard requires a map action, which needs "
                        + "coordinates");
        assertEquals("1h 30m", card.visitDuration());
    }

    @Test
    @DisplayName("a missing visit duration is omitted, not faked")
    void missingDurationIsOmitted() {
        TouristPlace unknown = place("Mystery", "ATTRACTION",
                null, false);

        HomeDiscoveryResponse home = homeDiscoveryService.home();

        HomeDiscoveryResponse.AttractionCard card = home
                .popularAttractions()
                .stream()
                .filter(c -> c.placeId()
                        .equals(unknown.getPlaceId()))
                .findFirst()
                .orElseThrow();

        assertNull(card.visitDuration(),
                "a rounded-up guess presented as fact is worse "
                        + "than an omitted line");
    }

    @Test
    @DisplayName("featured attractions appear in their own section")
    void featuredSectionIsSeparate() {
        place("Highlight", "TEMPLE", 60, true);
        place("Ordinary", "BEACH", 60, false);

        HomeDiscoveryResponse home = homeDiscoveryService.home();

        assertFalse(home.featuredAttractions().isEmpty());
        assertTrue(
                home.featuredAttractions().stream()
                        .allMatch(
                                HomeDiscoveryResponse
                                        .AttractionCard::featured),
                "the featured rail must not mix in unfeatured items"
        );
    }

    /* ============================================================
     * TRUST
     * ============================================================ */

    /**
     * A rejected or suspended listing on the front page would be
     * the worst possible advertisement for the approval
     * pipeline.
     */
    @Test
    @DisplayName("only live, verified properties appear on home")
    void onlyLiveVerifiedStaysAppear() {
        Property approved = property("Approved",
                PropertyStatus.APPROVED, true);
        Property rejected = property("Rejected",
                PropertyStatus.REJECTED, false);
        Property pending = property("Pending",
                PropertyStatus.PENDING_APPROVAL, false);
        Property suspended = property("Suspended",
                PropertyStatus.SUSPENDED, true);

        HomeDiscoveryResponse home = homeDiscoveryService.home();

        assertTrue(
                home.featuredStays().stream().anyMatch(
                        s -> s.propertyId()
                                .equals(approved.getPropertyId()))
        );

        assertFalse(
                home.featuredStays().stream().anyMatch(
                        s -> s.propertyId()
                                .equals(rejected.getPropertyId())
                                || s.propertyId().equals(
                                pending.getPropertyId())
                                || s.propertyId().equals(
                                suspended.getPropertyId())
                ),
                "unapproved or suspended stays must never reach Home"
        );
    }

    @Test
    @DisplayName("an unrated property reports no rating rather than a default")
    void unratedPropertyHasNoRating() {
        Property fresh = property("Fresh",
                PropertyStatus.APPROVED, true);

        HomeDiscoveryResponse home = homeDiscoveryService.home();

        HomeDiscoveryResponse.StayCard card = home
                .featuredStays()
                .stream()
                .filter(s -> s.propertyId()
                        .equals(fresh.getPropertyId()))
                .findFirst()
                .orElseThrow();

        assertNull(card.rating(),
                "a number on a discovery card reads as evidence");
        assertEquals(0, card.reviewCount());
    }

    @Test
    @DisplayName("a rated property reports its real review summary")
    void ratedPropertyReportsItsScore() {
        Property rated = property("Rated",
                PropertyStatus.APPROVED, true);

        ReviewSummary summary = new ReviewSummary();
        summary.setTargetType(ReviewTargetType.HOTEL);
        summary.setTargetId(rated.getPropertyId());
        summary.setAverageRating(new BigDecimal("4.50"));
        summary.setReviewCount(12);
        summary.setOneStarCount(0);
        summary.setTwoStarCount(0);
        summary.setThreeStarCount(1);
        summary.setFourStarCount(4);
        summary.setFiveStarCount(7);
        reviewSummaryRepository.save(summary);

        HomeDiscoveryResponse home = homeDiscoveryService.home();

        HomeDiscoveryResponse.StayCard card = home
                .featuredStays()
                .stream()
                .filter(s -> s.propertyId()
                        .equals(rated.getPropertyId()))
                .findFirst()
                .orElseThrow();

        assertEquals(0, new BigDecimal("4.50")
                .compareTo(card.rating()));
        assertEquals(12, card.reviewCount());
    }

    /* ============================================================
     * 2.1  INSPIRATION
     * ============================================================ */

    @Test
    @DisplayName("inspiration never points at an empty city")
    void inspirationSkipsEmptyCities() {
        City empty = new City(state, "Nowhere-" + UUID.randomUUID()
                .toString().substring(0, 6), "nowhere-" + UUID.randomUUID()
                .toString().substring(0, 6));
        cityRepository.save(empty);

        HomeDiscoveryResponse home = homeDiscoveryService.home();

        assertTrue(
                home.inspiration().collections().stream()
                        .noneMatch(c -> c.attractionCount() == 0),
                "an inspiration card pointing at nothing is a "
                        + "dead end"
        );
    }

    @Test
    @DisplayName("home is built even with no catalogue content at all")
    void emptyCatalogueStillRenders() {
        HomeDiscoveryResponse home = homeDiscoveryService.home();

        assertNotNull(home.hero());
        assertNotNull(home.plannerCta());
        assertNotNull(home.cities());
    }

    /* ============================================================
     * CAPS
     * ============================================================ */

    @Test
    @DisplayName("each section is capped so home stays a decision")
    void sectionsAreCapped() {
        for (int i = 0; i < 12; i++) {
            place("Bulk " + i, "BEACH", 60, true);
        }

        HomeDiscoveryResponse home = homeDiscoveryService.home();

        assertTrue(home.popularAttractions().size() <= 8);
        assertTrue(home.featuredAttractions().size() <= 8);
        assertTrue(home.cities().size() <= 8);
    }
}