package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.dto.trip.AddTripSelectionRequest;
import com.Travel.Buddy.dto.trip.CreateTripRequest;
import com.Travel.Buddy.dto.trip.TripDetailResponse;
import com.Travel.Buddy.dto.trip.TripSelectionResponse;
import com.Travel.Buddy.dto.trip.AddTripPlacesRequest;
import com.Travel.Buddy.dto.trip.SetTripPreferencesRequest;
import com.Travel.Buddy.dto.trip.SetTripRouteRequest;
import com.Travel.Buddy.dto.trip.SetTripScopeRequest;
import com.Travel.Buddy.dto.trip.TripRecommendationResponse;
import com.Travel.Buddy.entity.*;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hotel recommendations. (SRS 2.3 TP-08, sections 4.1, 6.1, 12)
 *
 * <p>These tests exist for the two rules the SRS states as
 * absolutes: section 12 forbids recommending an unavailable
 * hotel, and section 4.1 requires every recommendation to be
 * explainable. Everything else here is scoring behaviour that
 * must not quietly regress.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Hotel recommendations (SRS 2.3 TP-08)")
class TripRecommendationServiceTest {

    @Autowired
    private TripRecommendationService recommendationService;
    @Autowired
    private TripService tripService;
    @Autowired
    private TripRecommendationRepository recommendationRepository;
    @Autowired
    private TripCartService tripCartService;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CityRepository cityRepository;
    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private RoomTypeRepository roomTypeRepository;
    @Autowired
    private RoomInventoryRepository inventoryRepository;
    @Autowired
    private TouristPlaceRepository placeRepository;
    @Autowired
    private ReviewSummaryRepository reviewSummaryRepository;
    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private StateRepository stateRepository;

    private User traveller;
    private User stranger;
    private User owner;
    private City puri;

    private static State sharedState;
    private static LocalDate checkIn;
    private static LocalDate checkOut;
    private static final int NIGHTS = 3;

    @BeforeEach
    void setUp() {
        traveller = user("Rec Traveller");
        stranger = user("Rec Stranger");
        owner = user("Rec Owner");

        checkIn = LocalDate.now().plusDays(60);
        checkOut = checkIn.plusDays(NIGHTS);

        if (sharedState == null) {
            Country country = new Country();
            country.setName("Rec Land");
            country.setIsoCode("RL");
            country = countryRepository.save(country);

            sharedState = new State();
            sharedState.setName("Rec State");
            sharedState.setCountry(country);
            sharedState.setRegionZone(RegionZone.EAST);
            sharedState = stateRepository.save(sharedState);
        }

        /*
         * A fresh city per test, deliberately.
         *
         * Candidates are city-scoped, and only the top five are
         * persisted. Sharing one city across the class would let
         * earlier tests push a later test's property outside the
         * top five, so "was this recommended" would depend on
         * execution order. Owning the city makes each test's
         * candidate set exactly its own.
         */
        puri = new City(
                sharedState,
                "Puri-" + UUID.randomUUID()
                        .toString().substring(0, 6),
                "puri-" + UUID.randomUUID()
                        .toString().substring(0, 6)
        );
        puri.setLatitude(new BigDecimal("19.8135"));
        puri.setLongitude(new BigDecimal("85.8312"));
        puri = cityRepository.save(puri);
    }

    private User user(String name) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(UUID.randomUUID() + "@tb.local");
        user.setPasswordHash("{noop}password");
        return userRepository.save(user);
    }

    /* ============================================================
     * FIXTURES
     * ============================================================ */

    private Property property(
            String name,
            String lat,
            String lon,
            PropertyStatus status,
            boolean verified
    ) {
        Property property = new Property();
        property.setName(name + "-" + UUID.randomUUID()
                .toString().substring(0, 6));
        property.setPropertyType(PropertyType.HOTEL);
        property.setState(puri.getState());
        property.setCity(puri);
        property.setPartner(owner);
        property.setAddress("Somewhere");
        property.setDescription("A stay");
        property.setLatitude(new BigDecimal(lat));
        property.setLongitude(new BigDecimal(lon));
        property.setStatus(status);
        property.setVerified(verified);
        /*
         * Both are filtered on by the candidate queries, and
         * both default to false on the entity. Omitting them here
         * would silently exclude every fixture property.
         */
        property.setActive(true);
        return propertyRepository.save(property);
    }

    private RoomType room(
            Property property,
            String category,
            String price,
            int maxOccupancy
    ) {
        RoomType room = new RoomType();
        room.setProperty(property);
        room.setCategoryName(category);
        room.setMaxOccupancy(maxOccupancy);
        room.setBasePrice(new BigDecimal(price));
        room.setCurrency("INR");
        room.setTotalInventory(5);
        room.setActive(true);
        return roomTypeRepository.save(room);
    }

    /**
     * Idempotent, so a test that seeds twice does not collide on
     * the unique (room_type, date) key.
     */
    private void stock(RoomType roomType, int nights, int available) {
        for (int i = 0; i < nights; i++) {
            final LocalDate night = checkIn.plusDays(i);

            RoomInventoryDaily day = inventoryRepository
                    .findByRoomType_RoomTypeIdAndInventoryDate(
                            roomType.getRoomTypeId(), night)
                    .orElseGet(RoomInventoryDaily::new);

            day.setRoomType(roomType);
            day.setInventoryDate(night);
            day.setTotalInventory(5);
            day.setReservedRooms(Math.max(5 - available, 0));
            day.setBlockedRooms(0);
            day.recalculateAvailableInventory();
            inventoryRepository.save(day);
        }
    }

    /**
     * Writes inventory for a partial range, deliberately leaving
     * nights missing. Used to prove that a gap is not "available".
     */
    private void stockPartial(
            RoomType roomType,
            int nights
    ) {
        for (int i = 0; i < nights; i++) {
            final LocalDate night = checkIn.plusDays(i);

            RoomInventoryDaily day = inventoryRepository
                    .findByRoomType_RoomTypeIdAndInventoryDate(
                            roomType.getRoomTypeId(), night)
                    .orElseGet(RoomInventoryDaily::new);

            day.setRoomType(roomType);
            day.setInventoryDate(night);
            day.setTotalInventory(5);
            day.setReservedRooms(0);
            day.setBlockedRooms(0);
            day.recalculateAvailableInventory();
            inventoryRepository.save(day);
        }
    }

    private void rate(
            Property property,
            String rating,
            int reviews
    ) {
        ReviewSummary summary = new ReviewSummary();
        summary.setTargetType(ReviewTargetType.HOTEL);
        summary.setTargetId(property.getPropertyId());
        summary.setAverageRating(new BigDecimal(rating));
        summary.setReviewCount(reviews);
        summary.setOneStarCount(0);
        summary.setTwoStarCount(0);
        summary.setThreeStarCount(0);
        summary.setFourStarCount(reviews / 2);
        summary.setFiveStarCount(reviews - (reviews / 2));
        reviewSummaryRepository.save(summary);
    }

    private TouristPlace place(
            String lat,
            String lon
    ) {
        TouristPlace place = new TouristPlace();
        place.setName("Spot-" + UUID.randomUUID()
                .toString().substring(0, 6));
        place.setState(puri.getState());
        place.setCity(puri);
        place.setDescription("A place");
        place.setCurrency("INR");
        place.setEntryFee(BigDecimal.ZERO);
        place.setLatitude(new BigDecimal(lat));
        place.setLongitude(new BigDecimal(lon));
        return placeRepository.save(place);
    }

    /* ============================================================
     * TRIP BUILDERS
     * ============================================================ */

    /**
     * A trip with one dated stop, which is the minimum the
     * recommender will act on.
     */
    private Long trip() {
        Long tripId = tripService.create(
                traveller.getUserId(),
                new CreateTripRequest(
                        "Rec Trip", checkIn, checkOut, 1,
                        null, "INR"
                )
        ).tripId();

        tripService.setRoute(
                traveller.getUserId(),
                tripId,
                new SetTripRouteRequest(List.of(
                        new SetTripRouteRequest.CityStopRequest(
                                puri.getCityId(), checkIn, checkOut
                        )
                ))
        );

        return tripId;
    }

    private List<TripRecommendationResponse> recommend(
            Long tripId
    ) {
        return recommendationService.recommendHotels(
                traveller.getUserId(), tripId
        );
    }

    private List<TripRecommendationResponse> liveOnly(
            Long tripId
    ) {
        return recommend(tripId).stream()
                .filter(TripRecommendationResponse::recommended)
                .toList();
    }

    /* ============================================================
     * SECTION 12  AVAILABILITY
     * ============================================================ */

    @Test
    @DisplayName("a hotel with no inventory is never recommended")
    void noInventoryMeansNoRecommendation() {
        Property property = property("NoStock", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType room = room(property, "Deluxe", "2000.00", 4);
        stock(room, NIGHTS, 0);

        Long tripId = trip();

        assertTrue(
                liveOnly(tripId).stream().noneMatch(
                        r -> r.targetId()
                                .equals(property.getPropertyId())),
                "section 12: never recommend as available when the "
                        + "dates are unavailable"
        );
    }

    /**
     * A missing inventory row is not an available one. A night
     * nobody has priced cannot be sold, and treating a gap as
     * availability is exactly how a traveller reaches checkout
     * and finds the booking gone.
     */
    @Test
    @DisplayName("a gap in inventory is not treated as available")
    void inventoryGapIsNotAvailable() {
        Property property = property("Gap", "19.8200", "85.8400",
                PropertyStatus.APPROVED, true);
        RoomType room = room(property, "Deluxe", "2000.00", 4);
        stockPartial(room, NIGHTS - 1);

        Long tripId = trip();

        assertTrue(
                liveOnly(tripId).stream().noneMatch(
                        r -> r.targetId()
                                .equals(property.getPropertyId())),
                "a night with no inventory row is not a night that "
                        + "can be sold"
        );
    }

    @Test
    @DisplayName("a hotel available for every night is recommended")
    void fullyAvailableIsRecommended() {
        Property property = property("Open", "19.8200", "85.8400",
                PropertyStatus.APPROVED, true);
        RoomType room = room(property, "Deluxe", "2000.00", 4);
        stock(room, NIGHTS, 4);

        Long tripId = trip();

        assertTrue(
                liveOnly(tripId).stream().anyMatch(
                        r -> r.targetId()
                                .equals(property.getPropertyId()))
        );
    }

    @Test
    @DisplayName("an unverified or unlisted property is never recommended")
    void onlyLiveVerifiedPropertiesAreConsidered() {
        Property rejected = property("Rejected", "19.8200",
                "85.8400", PropertyStatus.REJECTED, false);
        RoomType rejectedRoom = room(rejected, "Deluxe",
                "1000.00", 4);
        stock(rejectedRoom, NIGHTS, 4);

        Property suspended = property("Suspended", "19.8200",
                "85.8400", PropertyStatus.SUSPENDED, true);
        RoomType suspendedRoom = room(suspended, "Deluxe",
                "1000.00", 4);
        stock(suspendedRoom, NIGHTS, 4);

        Long tripId = trip();

        List<TripRecommendationResponse> live = liveOnly(tripId);

        assertTrue(live.stream().noneMatch(
                r -> r.targetId().equals(rejected.getPropertyId())
        ));
        assertTrue(live.stream().noneMatch(
                r -> r.targetId().equals(suspended.getPropertyId())
        ));
    }

    /* ============================================================
     * SECTION 6.1  OCCUPANCY
     * ============================================================ */

    @Test
    @DisplayName("a room too small for the party is skipped")
    void occupancyIsRespected() {
        Property property = property("Small", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType tiny = room(property, "Single", "1000.00", 1);
        stock(tiny, NIGHTS, 4);

        Long tripId = trip();

        inputServiceSetParty(tripId, 4);

        assertTrue(
                liveOnly(tripId).stream().noneMatch(
                        r -> r.targetId()
                                .equals(property.getPropertyId())),
                "a 1-person room must not be offered to 4 people"
        );
    }

    @Test
    @DisplayName("a room that fits the party is recommended")
    void largeEnoughRoomIsRecommended() {
        Property property = property("Large", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType family = room(property, "Family", "5000.00", 6);
        stock(family, NIGHTS, 4);

        Long tripId = trip();
        inputServiceSetParty(tripId, 4);

        assertTrue(
                liveOnly(tripId).stream().anyMatch(
                        r -> r.targetId()
                                .equals(property.getPropertyId()))
        );
    }

    @Autowired
    private TripPlannerInputService inputService;

    private void inputServiceSetParty(
            Long tripId,
            int travellers
    ) {
        inputService.setPreferences(
                traveller.getUserId(),
                tripId,
                new SetTripPreferencesRequest(
                        travellers, travellers, 0, null
                )
        );
    }

    /* ============================================================
     * 4.1  EXPLAINABILITY
     * ============================================================ */

    @Test
    @DisplayName("every live recommendation explains itself")
    void everyRecommendationExplainsItself() {
        Property property = property("Explained", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType room = room(property, "Deluxe", "2000.00", 4);
        stock(room, NIGHTS, 4);
        rate(property, "4.50", 20);

        Long tripId = trip();
        TouristPlace temple = place("19.8250", "85.8450");
        tripService.addPlaces(
                traveller.getUserId(),
                tripId,
                new AddTripPlacesRequest(List.of(
                        new AddTripPlacesRequest.PlaceSelection(
                                tripService.get(
                                                traveller.getUserId(),
                                                tripId
                                        ).cities()
                                        .get(0)
                                        .tripCityId(),
                                temple.getPlaceId(),
                                null
                        )
                ))
        );

        for (TripRecommendationResponse r : liveOnly(tripId)) {
            assertNotNull(r.reason(),
                    "FR-40 requires an explanation");
            assertFalse(r.reason().isBlank());
            assertTrue(
                    r.reason().contains("Available for all"),
                    "the availability claim must be stated, got: "
                            + r.reason()
            );
        }
    }

    /**
     * The defect the live run exposed: rejected candidates are
     * stored for transparency, and without an explicit flag a
     * client would render one as a real option with a blank
     * price.
     */
    @Test
    @DisplayName("rejected candidates are flagged, not inferred from nulls")
    void rejectionsAreFlaggedExplicitly() {
        Property unavailable = property("Full", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType full = room(unavailable, "Deluxe", "2000.00", 4);
        stock(full, NIGHTS, 0);

        Long tripId = trip();
        List<TripRecommendationResponse> all = recommend(tripId);

        List<TripRecommendationResponse> rejected = all.stream()
                .filter(r -> !r.recommended())
                .toList();

        assertFalse(rejected.isEmpty(),
                "the rejected candidate must be recorded");

        for (TripRecommendationResponse r : rejected) {
            assertNotNull(r.rejectionReason(),
                    "a rejection without a reason is not "
                            + "transparent");
            assertNull(r.rankPosition());
            assertFalse(r.shown());
        }
    }

    @Test
    @DisplayName("live and rejected rows never overlap")
    void liveAndRejectedAreDistinct() {
        Property good = property("Good", "19.8200", "85.8400",
                PropertyStatus.APPROVED, true);
        RoomType goodRoom = room(good, "Deluxe", "2000.00", 4);
        stock(goodRoom, NIGHTS, 4);

        Property bad = property("Bad", "19.8200", "85.8400",
                PropertyStatus.APPROVED, true);
        RoomType badRoom = room(bad, "Deluxe", "2000.00", 4);
        stock(badRoom, NIGHTS, 0);

        Long tripId = trip();

        for (TripRecommendationResponse r : recommend(tripId)) {
            if (r.recommended()) {
                assertNotNull(r.rankPosition());
                assertNotNull(r.reason());
            } else {
                assertNull(r.rankPosition());
                assertNotNull(r.rejectionReason());
            }
        }
    }

    /* ============================================================
     * SCORING
     * ============================================================ */

    @Test
    @DisplayName("budget travel ranks the cheaper stay first")
    void budgetRanksByPrice() {
        Property cheap = property("Cheap", "19.8200", "85.8400",
                PropertyStatus.APPROVED, true);
        RoomType cheapRoom = room(cheap, "Standard",
                "1000.00", 4);
        stock(cheapRoom, NIGHTS, 4);

        Property pricey = property("Pricey", "19.8200", "85.8400",
                PropertyStatus.APPROVED, true);
        RoomType priceyRoom = room(pricey, "Suite",
                "9000.00", 4);
        stock(priceyRoom, NIGHTS, 4);

        Long tripId = trip();
        inputServiceSetStyle(tripId, TravelStyle.BUDGET);

        List<TripRecommendationResponse> live = liveOnly(tripId);

        int cheapRank = rankOf(live, cheap.getPropertyId());
        int priceyRank = rankOf(live, pricey.getPropertyId());

        assertTrue(cheapRank > 0 && priceyRank > 0,
                "both should be offered");
        assertTrue(cheapRank < priceyRank,
                "budget travel should rank the cheaper stay first");
    }

    @Test
    @DisplayName("premium travel ranks the better-rated stay first")
    void premiumRanksByQuality() {
        Property cheapButLoved = property("Loved", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType lovedRoom = room(cheapButLoved, "Standard",
                "1000.00", 4);
        stock(lovedRoom, NIGHTS, 4);
        rate(cheapButLoved, "4.90", 80);

        Property priceyButPoor = property("Poor", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType poorRoom = room(priceyButPoor, "Suite",
                "9000.00", 4);
        stock(poorRoom, NIGHTS, 4);
        rate(priceyButPoor, "2.10", 60);

        Long tripId = trip();
        inputServiceSetStyle(tripId, TravelStyle.PREMIUM);

        List<TripRecommendationResponse> live = liveOnly(tripId);

        assertTrue(
                rankOf(live, cheapButLoved.getPropertyId())
                        < rankOf(live, priceyButPoor.getPropertyId()),
                "premium should rank the well-reviewed stay first "
                        + "despite the price"
        );
    }

    /* ============================================================
     * CONFIDENCE WEIGHTING
     *
     * A single perfect review is weaker evidence than a long
     * history of nearly perfect ones. These pin that, because
     * nothing else in the suite distinguishes the two.
     * ============================================================ */

    /**
     * The headline case. One five-star review must not outrank two
     * hundred 4.5s, however perfect the single one looks.
     */
    @Test
    @DisplayName("one perfect review loses to many nearly perfect ones")
    void singleReviewDoesNotOutrankEstablishedHistory() {
        Property onePerfect = property("One", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType oneRoom = room(onePerfect, "Standard",
                "2000.00", 4);
        stock(oneRoom, NIGHTS, 4);
        rate(onePerfect, "5.00", 1);

        Property established = property("Many", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType manyRoom = room(established, "Standard",
                "2000.00", 4);
        stock(manyRoom, NIGHTS, 4);
        rate(established, "4.50", 200);

        Long tripId = trip();
        inputServiceSetStyle(tripId, TravelStyle.PREMIUM);

        List<TripRecommendationResponse> live = liveOnly(tripId);

        assertTrue(
                rankOf(live, established.getPropertyId())
                        < rankOf(live, onePerfect.getPropertyId()),
                "a single 5.00 is less evidence than 200 reviews "
                        + "averaging 4.50, so it must not win on "
                        + "rating alone"
        );
    }

    /**
     * At an identical rating, volume is the only thing left to
     * separate them, so it has to decide.
     *
     * <p>Names are chosen so that alphabetical order, which is the
     * final tiebreak, points the wrong way. A test that passes on
     * a name sort while claiming to prove rating behaviour is
     * worse than no test, because it looks like coverage.
     */
    @Test
    @DisplayName("at equal ratings the longer history wins")
    void volumeBreaksAnEqualRatingTie() {
        Property thin = property("Alpha", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType thinRoom = room(thin, "Standard",
                "2000.00", 4);
        stock(thinRoom, NIGHTS, 4);
        rate(thin, "4.50", 1);

        Property thick = property("Zulu", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType thickRoom = room(thick, "Standard",
                "2000.00", 4);
        stock(thickRoom, NIGHTS, 4);
        rate(thick, "4.50", 200);

        Long tripId = trip();
        inputServiceSetStyle(tripId, TravelStyle.PREMIUM);

        List<TripRecommendationResponse> live = liveOnly(tripId);

        assertTrue(
                rankOf(live, thick.getPropertyId())
                        < rankOf(live, thin.getPropertyId()),
                "same average, so the better evidenced one leads"
        );
    }

    /**
     * The stronger claim: a well-evidenced good rating beats a
     * poorly evidenced perfect one even when the perfect one has
     * the higher number. Without this, the previous two could pass
     * on volume alone.
     */
    @Test
    @DisplayName("a well evidenced 4.9 beats a bare 5.0")
    void evidenceCanOutweighTheRawNumber() {
        Property perfect = property("Perfect", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType perfectRoom = room(perfect, "Standard",
                "2000.00", 4);
        stock(perfectRoom, NIGHTS, 4);
        rate(perfect, "5.00", 2);

        Property proven = property("Proven", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType provenRoom = room(proven, "Standard",
                "2000.00", 4);
        stock(provenRoom, NIGHTS, 4);
        rate(proven, "4.90", 200);

        Long tripId = trip();
        inputServiceSetStyle(tripId, TravelStyle.PREMIUM);

        List<TripRecommendationResponse> live = liveOnly(tripId);

        assertTrue(
                rankOf(live, proven.getPropertyId())
                        < rankOf(live, perfect.getPropertyId()),
                "two five-star reviews should not outweigh two "
                        + "hundred 4.9s"
        );
    }

    /**
     * The SRS is explicit that the two modes are preferences, not
     * filters, so the same pair must order differently under each.
     */
    @Test
    @DisplayName("the same candidates order differently under each style")
    void stylesChangeTheOrder() {
        Property cheap = property("C", "19.8200", "85.8400",
                PropertyStatus.APPROVED, true);
        RoomType cheapRoom = room(cheap, "Standard",
                "1000.00", 4);
        stock(cheapRoom, NIGHTS, 4);
        rate(cheap, "3.00", 40);

        Property premium = property("P", "19.8200", "85.8400",
                PropertyStatus.APPROVED, true);
        RoomType premiumRoom = room(premium, "Suite",
                "9000.00", 4);
        stock(premiumRoom, NIGHTS, 4);
        rate(premium, "4.90", 40);

        Long budgetTrip = trip();
        inputServiceSetStyle(budgetTrip, TravelStyle.BUDGET);
        List<TripRecommendationResponse> budget = liveOnly(
                budgetTrip);

        Long premiumTrip = trip();
        inputServiceSetStyle(premiumTrip, TravelStyle.PREMIUM);
        List<TripRecommendationResponse> rich = liveOnly(
                premiumTrip);

        assertTrue(
                rankOf(budget, cheap.getPropertyId())
                        < rankOf(budget, premium.getPropertyId()),
                "budget favours the cheaper stay"
        );
        assertTrue(
                rankOf(rich, premium.getPropertyId())
                        < rankOf(rich, cheap.getPropertyId()),
                "premium favours the better-rated stay"
        );
    }

    /**
     * Proximity only counts when the traveller anchored the trip
     * to places. With none selected there is no proximity claim.
     */
    @Test
    @DisplayName("a hotel near a selected place outranks a distant one")
    void proximityIsScored() {
        Property near = property("Near", "19.8200", "85.8400",
                PropertyStatus.APPROVED, true);
        RoomType nearRoom = room(near, "Deluxe", "2000.00", 4);
        stock(nearRoom, NIGHTS, 4);

        Property far = property("Far", "20.4625", "85.8828",
                PropertyStatus.APPROVED, true);
        RoomType farRoom = room(far, "Deluxe", "2000.00", 4);
        stock(farRoom, NIGHTS, 4);

        Long tripId = trip();
        TouristPlace temple = place("19.8205", "85.8405");

        tripService.addPlaces(
                traveller.getUserId(),
                tripId,
                new AddTripPlacesRequest(List.of(
                        new AddTripPlacesRequest.PlaceSelection(
                                tripService.get(
                                                traveller.getUserId(),
                                                tripId
                                        ).cities()
                                        .get(0)
                                        .tripCityId(),
                                temple.getPlaceId(),
                                null
                        )
                ))
        );

        List<TripRecommendationResponse> live = liveOnly(tripId);

        assertTrue(
                rankOf(live, near.getPropertyId())
                        < rankOf(live, far.getPropertyId()),
                "a hotel beside the selected place should win"
        );

        TripRecommendationResponse nearCard = live.stream()
                .filter(r -> r.targetId()
                        .equals(near.getPropertyId()))
                .findFirst()
                .orElseThrow();

        assertNotNull(nearCard.distanceKm());
        assertTrue(
                nearCard.reason().contains("from your selected place"),
                "FR-40 wants the proximity stated, got: "
                        + nearCard.reason()
        );
    }

    /**
     * A new listing must not be buried for lacking history.
     *
     * <p>Note what this does NOT assert. Under PREMIUM the rated
     * property is expected to win, because section 6.1 says
     * premium prioritises higher-rated properties. The claim
     * under test is only that the unrated one is still offered
     * and is not treated as the worst option on the board.
     */
    @Test
    @DisplayName("an unrated property is still offered, not discarded")
    void unratedIsNotTreatedAsBad() {
        Property rated = property("Rated", "19.8200", "85.8400",
                PropertyStatus.APPROVED, true);
        RoomType ratedRoom = room(rated, "Deluxe", "2000.00", 4);
        stock(ratedRoom, NIGHTS, 4);
        rate(rated, "4.00", 200);

        Property unrated = property("Unrated", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType unratedRoom = room(unrated, "Deluxe",
                "2000.00", 4);
        stock(unratedRoom, NIGHTS, 4);

        Long tripId = trip();
        inputServiceSetStyle(tripId, TravelStyle.PREMIUM);

        List<TripRecommendationResponse> live = liveOnly(tripId);

        assertTrue(
                live.stream().anyMatch(r -> r.targetId()
                        .equals(unrated.getPropertyId())),
                "a property with no reviews yet must still be "
                        + "offered; absence of evidence is not "
                        + "evidence of absence"
        );

        /*
         * Rank order between the two is deliberately not asserted
         * here. Premium favouring a well-reviewed property is
         * already covered by premiumRanksByQuality and
         * stylesChangeTheOrder; repeating it here would only
         * couple this test to the confidence-weighting detail
         * rather than to the claim under test.
         */
        assertEquals(
                2, live.size(),
                "both properties should be offered, not one "
                        + "silently dropped for lacking reviews"
        );
    }

    private int rankOf(
            List<TripRecommendationResponse> rows,
            Long targetId
    ) {
        return rows.stream()
                .filter(r -> r.targetId().equals(targetId))
                .mapToInt(r -> r.rankPosition() == null
                        ? Integer.MAX_VALUE
                        : r.rankPosition())
                .findFirst()
                .orElse(Integer.MAX_VALUE);
    }

    private void inputServiceSetStyle(
            Long tripId,
            TravelStyle style
    ) {
        inputService.setPreferences(
                traveller.getUserId(),
                tripId,
                new SetTripPreferencesRequest(
                        2, 2, 0, style.name()
                )
        );
    }

    /* ============================================================
     * RECOMPUTE
     * ============================================================ */

    /*
     * ============================================================
     * ROOM IDENTITY  (SRS 2.2 TP-08 into TP-05)
     * ============================================================
     *
     * The recommender picks one room, scores against it and names
     * it in its explanation. If the id does not travel with the
     * offer, a client can add the property but not the room, and
     * TripCartService prices a hotel from its room type -- so the
     * stay quotes zero and checkout then refuses the whole trip as
     * an empty cart.
     *
     * Every other test here passed while that was true, because
     * each one seeded a room type by hand instead of taking it from
     * a recommendation. This one goes the way the client does.
     */

    @Test
    @DisplayName("a live recommendation names the room it was priced on")
    void recommendationCarriesItsRoom() {
        Property property = property("Rooomed", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType cheap = room(property, "Standard", "1000.00", 2);
        RoomType dear = room(property, "Suite", "9000.00", 4);
        stock(cheap, NIGHTS, 4);
        stock(dear, NIGHTS, 4);

        Long tripId = trip();
        List<TripRecommendationResponse> recs = recommend(tripId);

        TripRecommendationResponse live = recs.stream()
                .filter(TripRecommendationResponse::recommended)
                .findFirst()
                .orElseThrow();

        assertNotNull(live.roomTypeId(),
                "a recommended hotel must say which room it means, "
                        + "or it cannot be added to a cart");
        assertEquals(cheap.getRoomTypeId(), live.roomTypeId(),
                "the room must be the one the engine priced on, "
                        + "not merely any room at the property");
    }

    @Test
    @DisplayName("the room a recommendation names can actually be booked")
    void theRecommendedRoomCanBeAddedToTheCart() {
        Property property = property("Bookable", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType cheap = room(property, "Standard", "1500.00", 2);
        stock(cheap, NIGHTS, 4);

        Long tripId = trip();
        TripRecommendationResponse live = recommend(tripId).stream()
                .filter(TripRecommendationResponse::recommended)
                .findFirst()
                .orElseThrow();

        TripDetailResponse afterCart = tripCartService.addSelection(
                traveller.getUserId(),
                tripId,
                new AddTripSelectionRequest(
                        TripSelectionType.HOTEL,
                        live.tripCityId(),
                        live.targetId(),
                        live.roomTypeId(),
                        checkIn, checkOut,
                        2, 1, null, "INR"
                )
        );

        TripSelectionResponse added = afterCart.selections()
                .stream()
                .filter(s -> s.targetId().equals(live.targetId()))
                .findFirst()
                .orElseThrow();

        assertTrue(
                added.quotedAmount() != null
                        && added.quotedAmount().signum() > 0,
                "a hotel added from a recommendation must actually "
                        + "be priced, or checkout calls the cart empty"
        );
    }

    @Test
    @DisplayName("a rejected candidate names no room")
    void rejectedCandidateCarriesNoRoom() {
        Property property = property("Full", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType sold = room(property, "Only", "2000.00", 2);
        stock(sold, NIGHTS, 0);

        Long tripId = trip();

        for (TripRecommendationResponse rec : recommend(tripId)) {
            assertFalse(rec.recommended(), "nothing is available, "
                    + "so nothing may be recommended");
            assertNull(rec.roomTypeId(),
                    "a candidate rejected for lack of a free room "
                            + "must not claim one");
        }
    }

    @Test
    @DisplayName("recomputing replaces rather than accumulates")
    void recomputeReplaces() {
        Property property = property("Stable", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType room = room(property, "Deluxe", "2000.00", 4);
        stock(room, NIGHTS, 4);

        Long tripId = trip();

        recommend(tripId);
        long afterFirst = recommendationRepository
                .findByTrip_TripIdOrderByRankPositionAsc(tripId)
                .size();

        recommend(tripId);
        long afterSecond = recommendationRepository
                .findByTrip_TripIdOrderByRankPositionAsc(tripId)
                .size();

        assertEquals(afterFirst, afterSecond,
                "a second run must replace the first, not double it");
    }

    @Test
    @DisplayName("a stop with no dates yields no recommendations")
    void undatedStopIsNotRecommended() {
        Property property = property("Undated", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType room = room(property, "Deluxe", "2000.00", 4);
        stock(room, NIGHTS, 4);

        Long tripId = tripService.create(
                traveller.getUserId(),
                new CreateTripRequest(
                        "Undated", checkIn, checkOut, 1,
                        null, "INR"
                )
        ).tripId();

        // Stop with no arrival or departure dates.
        tripService.setRoute(
                traveller.getUserId(),
                tripId,
                new SetTripRouteRequest(List.of(
                        new SetTripRouteRequest.CityStopRequest(
                                puri.getCityId(), null, null
                        )
                ))
        );

        assertTrue(
                liveOnly(tripId).isEmpty(),
                "section 12 forbids recommending on dates whose "
                        + "availability cannot be proven"
        );
    }

    @Test
    @DisplayName("a trip with no cities cannot be recommended for")
    void noCitiesRefuses() {
        Long tripId = tripService.create(
                traveller.getUserId(),
                new CreateTripRequest(
                        "Empty", checkIn, checkOut, 1,
                        null, "INR"
                )
        ).tripId();

        assertThrows(
                PartnerApplicationException.class,
                () -> recommend(tripId)
        );
    }

    @Test
    @DisplayName("a stranger cannot see another traveller's recommendations")
    void recommendationsAreScopedToOwner() {
        Property property = property("Private", "19.8200",
                "85.8400", PropertyStatus.APPROVED, true);
        RoomType room = room(property, "Deluxe", "2000.00", 4);
        stock(room, NIGHTS, 4);

        Long tripId = trip();

        assertThrows(
                PartnerApplicationException.class,
                () -> recommendationService.recommendHotels(
                        stranger.getUserId(), tripId
                )
        );
    }
}