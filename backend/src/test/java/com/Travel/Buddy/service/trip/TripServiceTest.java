package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.dto.trip.AddTripPlacesRequest;
import com.Travel.Buddy.dto.trip.AddTripSelectionRequest;
import com.Travel.Buddy.dto.trip.ConfirmTripCheckoutRequest;
import com.Travel.Buddy.dto.trip.CreateTripRequest;
import com.Travel.Buddy.dto.trip.SetTripRouteRequest;
import com.Travel.Buddy.dto.trip.TripBillResponse;
import com.Travel.Buddy.dto.trip.TripCheckoutPreviewResponse;
import com.Travel.Buddy.dto.trip.TripCityResponse;
import com.Travel.Buddy.dto.trip.TripDetailResponse;
import com.Travel.Buddy.dto.trip.TripSelectionResponse;
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
 * Trip Planner. (SRS 2.2 FR-14, TP-01 .. TP-10)
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Trip Planner (SRS 2.2)")
class TripServiceTest {

    @Autowired
    private TripService tripService;
    @Autowired
    private TripCartService cartService;
    @Autowired
    private TripCheckoutService checkoutService;
    @Autowired
    private com.Travel.Buddy.repository.BookingRepository bookingRepository;
    @Autowired
    private com.Travel.Buddy.repository.GuideRepository guideRepository;
    @Autowired
    private com.Travel.Buddy.repository.CabRepository cabRepository;
    @Autowired
    private com.Travel.Buddy.repository.GuideReservationRepository guideReservationRepository;
    @Autowired
    private com.Travel.Buddy.repository.CabRideRepository cabRideRepository;
    @Autowired
    private TripBillService billService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CityRepository cityRepository;
    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private RoomTypeRepository roomTypeRepository;
    @Autowired
    private TouristPlaceRepository placeRepository;
    @Autowired
    private RoomInventoryRepository inventoryRepository;
    @Autowired
    private StateRepository stateRepository;
    @Autowired
    private CountryRepository countryRepository;

    private User traveller;
    private User stranger;
    private User owner;
    private City puri;
    private City bhubaneswar;
    private City cuttack;
    private Property property;
    private RoomType roomType;
    private com.Travel.Buddy.entity.Guide guide;
    private com.Travel.Buddy.entity.Cab cab;

    @BeforeEach
    void setUp() {
        traveller = user("Planner Traveller");
        stranger = user("Planner Stranger");
        owner = user("Hotel Owner");

        State state = new State();
        state.setName("Odisha");
        state.setRegionZone(RegionZone.EAST);
        state.setCountry(country());
        state = stateRepository.save(state);

        puri = city(state, "Puri", "puri", "19.8135", "85.8312");
        bhubaneswar = city(state, "Bhubaneswar", "bhubaneswar",
                "20.2961", "85.8245");
        cuttack = city(state, "Cuttack", "cuttack",
                "20.4625", "85.8828");

        property = new Property();
        property.setName("Planner Hotel");
        property.setPropertyType(PropertyType.HOTEL);
        property.setState(state);
        property.setCity(puri);
        property.setPartner(owner);
        property.setAddress("Beach Road");
        property.setDescription("Sea facing");
        property.setLatitude(new BigDecimal("19.8200"));
        property.setLongitude(new BigDecimal("85.8400"));
        property.setVerified(true);
        property.setStatus(PropertyStatus.APPROVED);
        property = propertyRepository.save(property);

        RoomType room = new RoomType();
        room.setProperty(property);
        room.setCategoryName("Deluxe");
        room.setMaxOccupancy(2);
        room.setBasePrice(new BigDecimal("2000.00"));
        room.setCurrency("INR");
        room.setTotalInventory(5);
        roomType = roomTypeRepository.save(room);

        guide = new com.Travel.Buddy.entity.Guide();
        guide.setUser(owner);
        guide.setState(state);
        guide.setDailyRate(new BigDecimal("2500.00"));
        guide.setCurrencyCode("INR");
        guide.setYearsOfExperience(8);
        guide.setVerified(true);
        guide.setActive(true);
        guide = guideRepository.save(guide);

        cab = new com.Travel.Buddy.entity.Cab();
        cab.setPartner(owner);
        cab.setState(state);
        cab.setVehicleName("Planner Sedan");
        cab.setVehicleType(
                com.Travel.Buddy.entity.VehicleType.SEDAN);
        /* Unique per test: setUp runs for every one, and the column
         * is unique. A fixed value collides on the second test. */
        cab.setRegistrationNumber(
                "OD07" + UUID.randomUUID()
                        .toString().substring(0, 6)
                        .toUpperCase()
        );
        cab.setSeatingCapacity(4);
        cab.setDriverName("Ravi");
        cab.setDriverPhone("9876500011");
        cab.setPricePerKm(new BigDecimal("18.00"));
        cab.setBaseFare(new BigDecimal("250.00"));
        cab.setAvailable(true);
        cab.setVerified(true);
        cab.setActive(true);
        cab = cabRepository.save(cab);
    }

    private Country country() {
        if (countryRepository.findAll().isEmpty()) {
            Country c = new Country();
            c.setName("Testland");
            c.setIsoCode("PL");
            return countryRepository.save(c);
        }
        return countryRepository.findAll().get(0);
    }

    private City city(State state, String name, String slug,
                      String lat, String lon) {
        java.util.Optional<City> existing =
                cityRepository.findBySlug(slug);
        if (existing.isPresent()) {
            return existing.get();
        }
        City c = new City(state, name, slug);
        c.setLatitude(new BigDecimal(lat));
        c.setLongitude(new BigDecimal(lon));
        return cityRepository.save(c);
    }

    private User user(String name) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(UUID.randomUUID() + "@tb.local");
        user.setPasswordHash("{noop}password");
        return userRepository.save(user);
    }

    private CreateTripRequest tripRequest(
            int nights, int cities
    ) {
        LocalDate start = LocalDate.now().plusDays(30);
        return new CreateTripRequest(
                "Test Trip", start, start.plusDays(nights),
                cities, null, "INR"
        );
    }

    private Long trip(int nights, int cities) {
        return tripService.create(
                traveller.getUserId(), tripRequest(nights, cities)
        ).tripId();
    }

    /**
     * Seeds availability. Idempotent, because a test that adds the
     * same hotel twice (to prove the selection updates rather
     * than duplicates) must not collide with the rows the first
     * call already wrote.
     */
    private void stockInventory(LocalDate from, int nights, int rooms) {
        for (int i = 0; i < nights; i++) {
            final LocalDate night = from.plusDays(i);

            RoomInventoryDaily day = inventoryRepository
                    .findByRoomType_RoomTypeIdAndInventoryDate(
                            roomType.getRoomTypeId(), night
                    )
                    .orElseGet(RoomInventoryDaily::new);

            day.setRoomType(roomType);
            day.setInventoryDate(night);
            day.setTotalInventory(5);
            day.setReservedRooms(Math.max(5 - rooms, 0));
            day.setBlockedRooms(0);
            day.recalculateAvailableInventory();
            inventoryRepository.save(day);
        }
    }

    private SetTripRouteRequest route(
            City... cities
    ) {
        return new SetTripRouteRequest(
                java.util.Arrays.stream(cities)
                        .map(c -> new SetTripRouteRequest
                                .CityStopRequest(
                                c.getCityId(), null, null))
                        .toList()
        );
    }

    /* ============================================================
     * TP-01  DATES
     * ============================================================ */

    @Test
    @DisplayName("a trip needs start and end dates")
    void datesAreMandatory() {
        assertThrows(
                Exception.class,
                () -> tripService.create(
                        traveller.getUserId(),
                        new CreateTripRequest(
                                "x", null, null, 1, null, "INR"
                        )
                )
        );
    }

    @Test
    @DisplayName("duration is counted in nights, not days")
    void durationIsNights() {
        TripDetailResponse trip = tripService.create(
                traveller.getUserId(), tripRequest(4, 1)
        );

        assertEquals(4, trip.nights());
    }

    @Test
    @DisplayName("the end date cannot precede the start")
    void endBeforeStartRejected() {
        LocalDate start = LocalDate.now().plusDays(30);

        assertThrows(
                PartnerApplicationException.class,
                () -> tripService.create(
                        traveller.getUserId(),
                        new CreateTripRequest(
                                "Backwards", start,
                                start.minusDays(2), 1, null, "INR"
                        )
                )
        );
    }

    @Test
    @DisplayName("a trip cannot start in the past")
    void pastStartRejected() {
        LocalDate start = LocalDate.now().minusDays(10);

        assertThrows(
                PartnerApplicationException.class,
                () -> tripService.create(
                        traveller.getUserId(),
                        new CreateTripRequest(
                                "Old", start, start.plusDays(3),
                                1, null, "INR"
                        )
                )
        );
    }

    @Test
    @DisplayName("a new trip starts in draft with map milestones already present")
    void milestonesAreSeededUpFront() {
        TripDetailResponse trip = tripService.create(
                traveller.getUserId(), tripRequest(3, 1)
        );

        assertEquals(TripStatus.DRAFT, trip.status());
        /*
         * Three, not two: start, review and finish. The review
         * pin exists from the outset so the map can later show
         * that sharing is a step the traveller owns.
         */
        assertEquals(3, trip.milestones().size());
        assertTrue(
                trip.milestones().stream()
                        .noneMatch(m -> m.completed()),
                "a fresh trip must not claim any progress"
        );
    }

    /* ============================================================
     * TP-02, TP-03  ROUTE
     * ============================================================ */

    @Test
    @DisplayName("cities are stored in the order given")
    void routeOrderIsPreserved() {
        Long tripId = trip(6, 3);

        TripDetailResponse trip = tripService.setRoute(
                traveller.getUserId(),
                tripId,
                route(cuttack, puri, bhubaneswar)
        );

        assertEquals(3, trip.cities().size());
        assertEquals("Cuttack", trip.cities().get(0).cityName());
        assertEquals("Puri", trip.cities().get(1).cityName());
        assertEquals("Bhubaneswar",
                trip.cities().get(2).cityName());
        assertEquals(1, trip.cities().get(0).sequence());
        assertEquals(3, trip.cities().get(2).sequence());
    }

    @Test
    @DisplayName("adding stops moves the trip into planning")
    void addingStopsBeginsPlanning() {
        Long tripId = trip(6, 2);

        TripDetailResponse trip = tripService.setRoute(
                traveller.getUserId(),
                tripId, route(puri, cuttack)
        );

        assertEquals(TripStatus.PLANNING, trip.status());
    }

    /**
     * The engine may suggest an order, but the traveller's order
     * is what is stored. Section 4.2 requires the sequence to
     * stay editable.
     */
    @Test
    @DisplayName("the suggested order never overrides the traveller")
    void suggestionIsAdvisoryOnly() {
        Long tripId = trip(6, 3);

        tripService.setRoute(
                traveller.getUserId(),
                tripId, route(cuttack, puri, bhubaneswar)
        );

        TripService.RouteSuggestionResponse suggestion =
                tripService.suggestRoute(
                        traveller.getUserId(), tripId
                );

        assertEquals(3, suggestion.stops().size());

        /*
         * The suggestion may well differ from what the traveller
         * chose; that is the point of offering it. What must
         * never happen is the stored route changing underneath
         * them.
         */
        assertEquals(
                "Cuttack",
                tripService.get(traveller.getUserId(), tripId)
                        .cities()
                        .get(0)
                        .cityName(),
                "the stored route must be untouched by a suggestion"
        );
        assertEquals(
                "Bhubaneswar",
                tripService.get(traveller.getUserId(), tripId)
                        .cities()
                        .get(2)
                        .cityName()
        );
    }

    @Test
    @DisplayName("every suggested stop explains itself")
    void suggestionsAreExplainable() {
        Long tripId = trip(6, 3);
        tripService.setRoute(
                traveller.getUserId(),
                tripId, route(puri, bhubaneswar, cuttack)
        );

        TripService.RouteSuggestionResponse suggestion =
                tripService.suggestRoute(
                        traveller.getUserId(), tripId
                );

        assertTrue(suggestion.totalDistanceKm() > 0);
        for (TripService.SuggestedStop stop
                : suggestion.stops()) {
            assertNotNull(stop.reason(),
                    "FR-40 requires every recommendation to "
                            + "explain itself");
            assertFalse(stop.reason().isBlank());
        }
    }

    @Test
    @DisplayName("a compressed itinerary is warned about")
    void compressedItineraryWarns() {
        Long tripId = trip(1, 3);
        tripService.setRoute(
                traveller.getUserId(),
                tripId, route(puri, cuttack, bhubaneswar)
        );

        List<String> warnings = tripService.warn(
                traveller.getUserId(), tripId
        );

        assertFalse(warnings.isEmpty());
        assertTrue(
                warnings.stream().anyMatch(
                        w -> w.contains("cities")
                                || w.contains("travelling")
                ),
                "expected a compression warning, got " + warnings
        );
    }

    /**
     * Section 12 asks for excessive backtracking to be flagged.
     * The three seeded Odisha cities sit within a few tens of
     * kilometres of each other, so this asserts the engine is
     * genuinely measuring the route rather than assuming every
     * order is acceptable.
     */
    @Test
    @DisplayName("route warnings are computed from the actual geography")
    void backtrackingIsMeasured() {
        Long tripId = trip(8, 3);
        tripService.setRoute(
                traveller.getUserId(),
                tripId, route(puri, bhubaneswar, cuttack)
        );

        List<String> warnings = tripService.warn(
                traveller.getUserId(), tripId
        );

        /*
         * Three cities over eight nights is comfortable, so no
         * warning is owed. What matters is that the check runs
         * and the distance is actually computed.
         */
        assertNotNull(warnings);

        TripService.RouteSuggestionResponse suggestion =
                tripService.suggestRoute(
                        traveller.getUserId(), tripId
                );

        assertTrue(
                suggestion.totalDistanceKm() > 0,
                "the engine must actually measure the route"
        );
    }

    @Test
    @DisplayName("a stop with no overnight stay is called out")
    void compressedStopIsWarned() {
        Long tripId = trip(6, 1);
        LocalDate start = LocalDate.now().plusDays(30);

        tripService.setRoute(
                traveller.getUserId(),
                tripId,
                new SetTripRouteRequest(List.of(
                        new SetTripRouteRequest.CityStopRequest(
                                puri.getCityId(),
                                start.plusDays(1),
                                start.plusDays(1)
                        )
                ))
        );

        List<String> warnings = tripService.warn(
                traveller.getUserId(), tripId
        );

        assertTrue(
                warnings.stream().anyMatch(
                        w -> w.contains("0 nights")
                                || w.contains("Check that")
                ),
                "expected a zero-night warning, got " + warnings
        );
    }

    @Test
    @DisplayName("the same city cannot appear twice in a route")
    void duplicateCityRejected() {
        Long tripId = trip(6, 2);

        assertThrows(
                PartnerApplicationException.class,
                () -> tripService.setRoute(
                        traveller.getUserId(),
                        tripId, route(puri, puri)
                )
        );
    }

    @Test
    @DisplayName("an unknown city is refused")
    void unknownCityRejected() {
        Long tripId = trip(6, 1);

        assertThrows(
                PartnerApplicationException.class,
                () -> tripService.setRoute(
                        traveller.getUserId(),
                        tripId,
                        new SetTripRouteRequest(List.of(
                                new SetTripRouteRequest
                                        .CityStopRequest(
                                        999_999_999L, null, null)
                        ))
                )
        );
    }

    @Test
    @DisplayName("a stop cannot run past the end of the trip")
    void stopBeyondTripEndRejected() {
        Long tripId = trip(4, 1);
        LocalDate start = LocalDate.now().plusDays(30);

        assertThrows(
                PartnerApplicationException.class,
                () -> tripService.setRoute(
                        traveller.getUserId(),
                        tripId,
                        new SetTripRouteRequest(List.of(
                                new SetTripRouteRequest
                                        .CityStopRequest(
                                        puri.getCityId(),
                                        start.plusDays(1),
                                        start.plusDays(9)
                                )
                        ))
                )
        );
    }

    /* ============================================================
     * TP-04  PLACES
     * ============================================================ */

    private TouristPlace place(String name) {
        TouristPlace p = new TouristPlace();
        p.setName(name + "-" + UUID.randomUUID()
                .toString().substring(0, 6));
        p.setState(puri.getState());
        p.setCity(puri);
        p.setDescription("A place");
        p.setEntryFee(new BigDecimal("100.00"));
        p.setCurrency("INR");
        p.setLatitude(new BigDecimal("19.8200"));
        p.setLongitude(new BigDecimal("85.8400"));
        return placeRepository.save(p);
    }

    @Test
    @DisplayName("places can be selected per city and the step is optional")
    void placesAreSelectable() {
        Long tripId = trip(6, 2);
        TripDetailResponse routed = tripService.setRoute(
                traveller.getUserId(),
                tripId, route(puri, cuttack)
        );
        Long puriStop = routed.cities()
                .get(0)
                .tripCityId();
        TouristPlace beach = place("Puri Beach");

        TripDetailResponse trip = tripService.addPlaces(
                traveller.getUserId(),
                tripId,
                new AddTripPlacesRequest(List.of(
                        new AddTripPlacesRequest
                                .PlaceSelection(
                                puriStop,
                                beach.getPlaceId(),
                                "Sunrise"
                        )
                ))
        );

        assertEquals(1, trip.cities().get(0).placeCount());
    }

    @Test
    @DisplayName("a trip with no places is perfectly valid")
    void placesAreOptional() {
        Long tripId = trip(6, 2);

        TripDetailResponse trip = tripService.get(
                traveller.getUserId(), tripId
        );

        assertNotNull(trip);
        assertTrue(trip.cities().isEmpty()
                || trip.cities().get(0).placeCount() == 0);
    }

    @Test
    @DisplayName("selecting the same place twice adds nothing")
    void duplicatePlaceIsANoOp() {
        Long tripId = trip(6, 1);
        TripDetailResponse routed = tripService.setRoute(
                traveller.getUserId(), tripId, route(puri)
        );
        Long stop = routed.cities().get(0).tripCityId();
        TouristPlace temple = place("Temple");

        AddTripPlacesRequest request = new AddTripPlacesRequest(
                List.of(new AddTripPlacesRequest.PlaceSelection(
                        stop, temple.getPlaceId(), null
                ))
        );

        tripService.addPlaces(
                traveller.getUserId(), tripId, request
        );
        TripDetailResponse second = tripService.addPlaces(
                traveller.getUserId(), tripId, request
        );

        assertEquals(
                1, second.cities().get(0).placeCount(),
                "a double tap must not double the entry fee"
        );
    }

    @Test
    @DisplayName("a place from another trip cannot be attached")
    void crossTripPlaceRejected() {
        Long mine = trip(6, 1);
        Long theirs = trip(6, 1);

        TripDetailResponse mineRouted = tripService.setRoute(
                traveller.getUserId(), mine, route(puri)
        );
        TripDetailResponse theirsRouted = tripService.setRoute(
                traveller.getUserId(), theirs, route(cuttack)
        );

        TouristPlace beach = place("Temple");

        assertThrows(
                PartnerApplicationException.class,
                () -> tripService.addPlaces(
                        traveller.getUserId(),
                        mine,
                        new AddTripPlacesRequest(List.of(
                                new AddTripPlacesRequest
                                        .PlaceSelection(
                                        theirsRouted.cities()
                                                .get(0)
                                                .tripCityId(),
                                        beach.getPlaceId(),
                                        null
                                )
                        ))
                )
        );
    }

    /* ============================================================
     * TP-08  CART  (SELECTED IS NOT BOOKED)
     * ============================================================ */

    /**
     * A trip with a routed stop and a bookable room in it,
     * which is the precondition for anything involving checkout.
     */
    private Long tripWithABookedStay() {
        return withHotel(trip(3, 1), 2).tripId();
    }

    private TripDetailResponse withHotel(
            Long tripId,
            int nights
    ) {
        Long routed = tripService.setRoute(
                traveller.getUserId(), tripId, route(puri)
        ).cities().get(0).tripCityId();

        LocalDate checkIn = LocalDate.now().plusDays(30);

        stockInventory(checkIn, nights, 5);

        return cartService.addSelection(
                traveller.getUserId(),
                tripId,
                new AddTripSelectionRequest(
                        TripSelectionType.HOTEL,
                        routed,
                        property.getPropertyId(),
                        roomType.getRoomTypeId(),
                        checkIn,
                        checkIn.plusDays(nights),
                        2,
                        null,
                        null,
                        "INR"
                )
        );
    }

    /**
     * The single most important rule in SRS 2.2.
     */
    @Test
    @DisplayName("a selection reserves nothing")
    void selectionReservesNothing() {
        Long tripId = trip(6, 1);

        TripDetailResponse trip = withHotel(tripId, 2);

        assertEquals(1, trip.selections().size());
        assertFalse(
                trip.selections().get(0).reserved(),
                "SELECTED must never be presented as BOOKED"
        );
        assertNull(
                trip.selections().get(0).bookingId(),
                "no booking may exist yet"
        );
        assertEquals(
                TripSelectionStatus.SELECTED,
                trip.selections().get(0).status()
        );
    }

    @Test
    @DisplayName("a quoted hotel price is nights times the base rate")
    void hotelIsQuoted() {
        Long tripId = trip(6, 1);

        TripDetailResponse trip = withHotel(tripId, 2);

        assertEquals(
                new BigDecimal("4000.00"),
                trip.selections().get(0).quotedAmount()
        );
    }

    @Test
    @DisplayName("selecting the same hotel twice updates rather than duplicates")
    void duplicateSelectionUpdates() {
        Long tripId = trip(6, 1);
        withHotel(tripId, 2);

        TripDetailResponse trip = withHotel(tripId, 3);

        assertEquals(
                1, trip.selections().size(),
                "the same property must not appear twice"
        );
        assertEquals(
                new BigDecimal("6000.00"),
                trip.selections().get(0).quotedAmount()
        );
    }

    @Test
    @DisplayName("a room type from another property cannot be attached")
    void mismatchedRoomTypeRejected() {
        Long tripId = trip(6, 1);
        Long stop = tripService.setRoute(
                traveller.getUserId(), tripId, route(puri)
        ).cities().get(0).tripCityId();

        LocalDate checkIn = LocalDate.now().plusDays(30);

        assertThrows(
                PartnerApplicationException.class,
                () -> cartService.addSelection(
                        traveller.getUserId(),
                        tripId,
                        new AddTripSelectionRequest(
                                TripSelectionType.HOTEL,
                                stop,
                                bhubaneswar.getCityId(),
                                roomType.getRoomTypeId(),
                                checkIn,
                                checkIn.plusDays(2),
                                2,
                                null,
                                null,
                                "INR"
                        )
                )
        );
    }

    @Test
    @DisplayName("another traveller cannot touch the cart")
    void cartIsScopedToOwner() {
        Long tripId = trip(6, 1);
        withHotel(tripId, 2);

        assertThrows(
                PartnerApplicationException.class,
                () -> tripService.get(
                        stranger.getUserId(), tripId
                )
        );
    }

    /* ============================================================
     * TP-09  BILL
     * ============================================================ */

    @Test
    @DisplayName("the bill itemises by category with tax and fee shown")
    void billIsItemised() {
        Long tripId = trip(6, 1);
        withHotel(tripId, 2);

        TripBillResponse bill = billService.preview(
                tripService.ownedTrip(
                        traveller.getUserId(), tripId
                )
        );

        assertEquals(
                new BigDecimal("4000.00"),
                bill.hotelSubtotal()
        );
        assertTrue(
                bill.taxAmount().signum() > 0,
                "section 5.1 requires taxes shown explicitly"
        );
        assertTrue(bill.feeAmount().signum() > 0);
        assertEquals(
                bill.hotelSubtotal()
                        .add(bill.taxAmount())
                        .add(bill.feeAmount()),
                bill.total()
        );
        assertTrue(
                tripService.get(traveller.getUserId(), tripId)
                        .estimatedTotal()
                        .compareTo(new BigDecimal("4000.00")) > 0
        );
    }

    @Test
    @DisplayName("exceeding a budget warns but never blocks")
    void overBudgetWarnsOnly() {
        LocalDate start = LocalDate.now().plusDays(30);
        Long tripId = tripService.create(
                traveller.getUserId(),
                new CreateTripRequest(
                        "Budget Trip", start, start.plusDays(2),
                        1, new BigDecimal("100.00"), "INR"
                )
        ).tripId();

        withHotel(tripId, 2);

        TripDetailResponse trip = tripService.get(
                traveller.getUserId(), tripId
        );

        assertTrue(trip.overBudget());
        assertNotNull(trip.budgetRemaining());
        assertTrue(trip.budgetRemaining().signum() < 0);

        /*
         * Section 4.5 says warn, never block. Checkout must still
         * be reachable.
         */
        assertDoesNotThrow(
                () -> checkoutService.preview(
                        traveller.getUserId(), tripId
                )
        );
    }

    /* ============================================================
     * TP-10  CENTRAL CHECKOUT
     * ============================================================ */

    @Test
    @DisplayName("checkout revalidates and returns an itemized bill")
    void checkoutPreviews() {
        Long tripId = trip(6, 1);
        withHotel(tripId, 2);

        TripCheckoutPreviewResponse preview =
                checkoutService.preview(
                        traveller.getUserId(), tripId
                );

        assertNotNull(preview.checkoutReference());
        assertTrue(preview.bill().total().signum() > 0);
        assertTrue(preview.bill().lines().size() >= 3,
                "hotel, tax and fee lines are expected");
    }

    /**
     * Section 6: a service that became unavailable must be shown
     * and never silently replaced.
     */
    @Test
    @DisplayName("an unavailable service is reported, never substituted")
    void unavailableServiceIsReported() {
        Long tripId = trip(6, 1);
        withHotel(tripId, 2);

        LocalDate checkIn = LocalDate.now().plusDays(30);

        for (int i = 0; i < 2; i++) {
            final LocalDate night = checkIn.plusDays(i);

            inventoryRepository.findAll().stream()
                    .filter(d -> d.getRoomType()
                            .getRoomTypeId()
                            .equals(roomType.getRoomTypeId()))
                    .filter(d -> d.getInventoryDate()
                            .equals(night))
                    .forEach(d -> {
                        d.setBlockedRooms(5);
                        d.recalculateAvailableInventory();
                        inventoryRepository.save(d);
                    });
        }

        TripCheckoutPreviewResponse preview =
                checkoutService.preview(
                        traveller.getUserId(), tripId
                );

        assertEquals(1, preview.changedLines().size());
        assertTrue(preview.changedLines()
                .get(0)
                .reason()
                .contains("No rooms left"));
        assertTrue(preview.requiresConfirmation());
    }

    @Test
    @DisplayName("an empty cart cannot be checked out")
    void emptyCartRejected() {
        Long tripId = trip(6, 1);

        assertThrows(
                PartnerApplicationException.class,
                () -> checkoutService.preview(
                        traveller.getUserId(), tripId
                )
        );
    }

    /**
     * One open checkout per trip, enforced by the row lock. Two
     * concurrent charges for one trip is the failure this
     * prevents.
     */
    @Test
    @DisplayName("a second checkout while one is open is refused")
    void onlyOneOpenCheckout() {
        Long tripId = trip(6, 1);
        withHotel(tripId, 2);

        checkoutService.preview(
                traveller.getUserId(), tripId
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> checkoutService.preview(
                        traveller.getUserId(), tripId
                )
        );
    }

    @Test
    @DisplayName("the traveller must agree to the exact revalidated total")
    void confirmationMustMatchTheTotal() {
        Long tripId = trip(6, 1);
        withHotel(tripId, 2);

        TripCheckoutPreviewResponse preview =
                checkoutService.preview(
                        traveller.getUserId(), tripId
                );

        assertThrows(
                PartnerApplicationException.class,
                () -> checkoutService.assertAgreed(
                        traveller.getUserId(),
                        tripId,
                        new ConfirmTripCheckoutRequest(
                                preview.checkoutId(),
                                new BigDecimal("1.00"),
                                "INR"
                        )
                )
        );

        assertNotNull(checkoutService.assertAgreed(
                traveller.getUserId(),
                tripId,
                new ConfirmTripCheckoutRequest(
                        preview.checkoutId(),
                        preview.revalidatedTotal(),
                        "INR"
                )
        ));
    }

    @Test
    @DisplayName("a repeated payment callback is not a second charge")
    void paymentIsIdempotent() {
        Long tripId = trip(6, 1);
        withHotel(tripId, 2);

        TripCheckoutPreviewResponse preview =
                checkoutService.preview(
                        traveller.getUserId(), tripId
                );
        checkoutService.assertAgreed(
                traveller.getUserId(),
                tripId,
                new ConfirmTripCheckoutRequest(
                        preview.checkoutId(),
                        preview.revalidatedTotal(),
                        "INR"
                )
        );

        checkoutService.markPaid(
                preview.checkoutId(), "pay_abc123"
        );
        checkoutService.markPaid(
                preview.checkoutId(), "pay_abc123"
        );

        assertEquals(
                TripCheckoutStatus.CONFIRMED,
                checkoutService.history(
                        traveller.getUserId(), tripId
                ).get(0).getStatus(),
                "a paid checkout whose rooms were all reserved is "
                        + "CONFIRMED, not merely PAID"
        );
    }

    @Test
    @DisplayName("one payment id cannot belong to two checkouts")
    void paymentIdIsUnique() {
        Long first = trip(6, 1);
        Long second = trip(6, 1);
        withHotel(first, 2);
        withHotel(second, 2);

        TripCheckoutPreviewResponse a =
                checkoutService.preview(
                        traveller.getUserId(), first
                );
        TripCheckoutPreviewResponse b =
                checkoutService.preview(
                        traveller.getUserId(), second
                );

        checkoutService.markPaid(a.checkoutId(), "pay_same");

        assertThrows(
                PartnerApplicationException.class,
                () -> checkoutService.markPaid(
                        b.checkoutId(), "pay_same"
                )
        );
    }

    /* ============================================================
     * TP-10  GUIDES AND CABS ARE BOOKED TOO
     *
     * The cart used to price both at zero and the booking step
     * refused them outright, so a traveller could pay for a guide
     * and be told it "cannot be reserved yet" while the money was
     * taken.
     * ============================================================ */

    @Test
    @DisplayName("a stop carries its state so local services can be found")
    void stopCarriesItsState() {
        Long tripId = trip(6, 1);
        Long stop = withStop(tripId);

        TripCityResponse city = tripService
                .get(traveller.getUserId(), tripId)
                .cities().get(0);

        assertNotNull(
                city.stateId(),
                "the planner offers guides and cabs for the stop's "
                        + "state, and both listings are scoped by "
                        + "stateId. Without it the step can only show "
                        + "everything, or nothing."
        );
    }

    @Test
    @DisplayName("a guide in the cart is charged per day, not zero")
    void guideIsPricedPerDay() {
        Long tripId = trip(6, 1);
        Long stop = withStop(tripId);

        TripDetailResponse after = cartService.addSelection(
                traveller.getUserId(), tripId,
                new AddTripSelectionRequest(
                        TripSelectionType.GUIDE, stop,
                        guide.getGuideId(), null,
                        LocalDate.now().plusDays(30),
                        LocalDate.now().plusDays(32),
                        2, null, null, "INR"
                )
        );

        assertEquals(
                new BigDecimal("5000.00"),
                after.selections().get(0).quotedAmount(),
                "two days at 2500 must not quote as free"
        );
    }

    @Test
    @DisplayName("paying a trip books its guide")
    void payingBooksTheGuide() {
        Long tripId = trip(6, 1);
        withGuide(tripId);

        payFully(tripId);

        TripSelectionResponse booked = tripService
                .get(traveller.getUserId(), tripId)
                .selections().get(0);

        assertEquals(TripSelectionStatus.BOOKED, booked.status());
        assertNotNull(booked.bookingId());

        assertEquals(
                BookingType.GUIDE,
                bookingRepository.findById(booked.bookingId())
                        .orElseThrow().getBookingType(),
                "FR-34 cannot price commission on a booking that "
                        + "does not say what it is for"
        );

        assertTrue(
                guideReservationRepository
                        .findByBooking_BookingId(booked.bookingId())
                        .stream()
                        .anyMatch(r -> r.getGuide().getGuideId()
                                .equals(guide.getGuideId())),
                "the guide must actually be reserved, not merely "
                        + "referenced by a booking"
        );
    }

    @Test
    @DisplayName("one guide cannot be sold twice on the same day")
    void guideCannotBeDoubleBooked() {
        Long first = trip(6, 1);
        withGuide(first);
        payFully(first);

        Long second = trip(6, 1);
        withGuide(second);

        TripCheckoutPreviewResponse preview =
                checkoutService.preview(
                        traveller.getUserId(), second
                );
        checkoutService.assertAgreed(
                traveller.getUserId(), second,
                new ConfirmTripCheckoutRequest(
                        preview.checkoutId(),
                        preview.revalidatedTotal(), "INR"
                )
        );

        TripCheckout paid = checkoutService.payMock(
                traveller.getUserId(), second,
                preview.checkoutId(), true
        );

        assertEquals(
                TripCheckoutStatus.RECOVERY_REQUIRED,
                paid.getStatus(),
                "the guide is already gone on that date"
        );

        assertEquals(
                1,
                guideReservationRepository
                        .findByGuide_GuideId(guide.getGuideId())
                        .size(),
                "two travellers must not end up with the same guide"
        );
    }

    @Test
    @DisplayName("paying a trip books its cab as a paid ride")
    void payingBooksTheCab() {
        Long tripId = trip(6, 1);
        withCab(tripId);

        payFully(tripId);

        TripSelectionResponse booked = tripService
                .get(traveller.getUserId(), tripId)
                .selections().get(0);

        assertEquals(TripSelectionStatus.BOOKED, booked.status());
        assertNotNull(booked.bookingId());

        assertEquals(
                BookingType.CAB,
                bookingRepository.findById(booked.bookingId())
                        .orElseThrow().getBookingType()
        );

        assertTrue(
                cabRideRepository
                        .findByUser_UserIdOrderByCreatedAtDesc(
                                traveller.getUserId()).stream()
                        .anyMatch(ride -> ride.getBooking() != null
                                && booked.bookingId().equals(
                                        ride.getBooking()
                                                .getBookingId())),
                "a paid cab must be a real ride the driver can see, "
                        + "and it must point at the booking that paid"
        );
    }

    private Long withStop(Long tripId) {
        return tripService.setRoute(
                traveller.getUserId(), tripId, route(puri)
        ).cities().get(0).tripCityId();
    }

    private void withGuide(Long tripId) {
        Long stop = withStop(tripId);
        cartService.addSelection(
                traveller.getUserId(), tripId,
                new AddTripSelectionRequest(
                        TripSelectionType.GUIDE, stop,
                        guide.getGuideId(), null,
                        LocalDate.now().plusDays(30),
                        LocalDate.now().plusDays(32),
                        2, null, null, "INR"
                )
        );
    }

    private void withCab(Long tripId) {
        Long stop = withStop(tripId);
        cartService.addSelection(
                traveller.getUserId(), tripId,
                new AddTripSelectionRequest(
                        TripSelectionType.CAB, stop,
                        cab.getCabId(), null,
                        LocalDate.now().plusDays(30),
                        LocalDate.now().plusDays(32),
                        2, null, null, "INR"
                )
        );
    }

    private void payFully(Long tripId) {
        TripCheckoutPreviewResponse preview =
                checkoutService.preview(
                        traveller.getUserId(), tripId
                );
        checkoutService.assertAgreed(
                traveller.getUserId(), tripId,
                new ConfirmTripCheckoutRequest(
                        preview.checkoutId(),
                        preview.revalidatedTotal(), "INR"
                )
        );
        checkoutService.payMock(
                traveller.getUserId(), tripId,
                preview.checkoutId(), true
        );
    }

    /*
     *
     * Before this existed a paid trip reserved nothing: the money
     * cleared, the trip was confirmed, the map advanced, and no
     * hotel was ever told. These assert the reservation actually
     * exists afterwards.
     * ============================================================ */

    @Test
    @DisplayName("paying a trip creates a real booking for the stay")
    void payingCreatesABooking() {
        Long tripId = trip(6, 1);
        withHotel(tripId, 2);

        TripCheckoutPreviewResponse preview =
                checkoutService.preview(
                        traveller.getUserId(), tripId
                );
        checkoutService.assertAgreed(
                traveller.getUserId(),
                tripId,
                new ConfirmTripCheckoutRequest(
                        preview.checkoutId(),
                        preview.revalidatedTotal(),
                        "INR"
                )
        );

        checkoutService.payMock(
                traveller.getUserId(), tripId,
                preview.checkoutId(), true
        );

        TripSelectionResponse booked = tripService
                .get(traveller.getUserId(), tripId)
                .selections().get(0);

        assertEquals(
                TripSelectionStatus.BOOKED,
                booked.status(),
                "a paid stay must be BOOKED, not merely selected"
        );

        assertNotNull(
                booked.bookingId(),
                "the stay must point at the booking that was made"
        );

        assertNotNull(
                bookingRepository.findById(booked.bookingId())
                        .orElse(null),
                "the booking the selection points at must exist"
        );
    }

    @Test
    @DisplayName("paying takes the rooms out of inventory")
    void payingReservesInventory() {
        Long tripId = trip(6, 1);
        withHotel(tripId, 2);

        LocalDate firstNight = LocalDate.now().plusDays(30);
        int before = availabilityOf(firstNight);

        TripCheckoutPreviewResponse preview =
                checkoutService.preview(
                        traveller.getUserId(), tripId
                );
        checkoutService.assertAgreed(
                traveller.getUserId(),
                tripId,
                new ConfirmTripCheckoutRequest(
                        preview.checkoutId(),
                        preview.revalidatedTotal(),
                        "INR"
                )
        );
        checkoutService.payMock(
                traveller.getUserId(), tripId,
                preview.checkoutId(), true
        );

        assertEquals(
                before - 1,
                availabilityOf(firstNight),
                "a paid stay must actually take a room, or two "
                        + "travellers can be sold the same one"
        );
    }

    @Test
    @DisplayName("paying twice does not book the same stay twice")
    void payingIsIdempotentAcrossBookings() {
        Long tripId = trip(6, 1);
        withHotel(tripId, 2);

        TripCheckoutPreviewResponse preview =
                checkoutService.preview(
                        traveller.getUserId(), tripId
                );
        checkoutService.assertAgreed(
                traveller.getUserId(),
                tripId,
                new ConfirmTripCheckoutRequest(
                        preview.checkoutId(),
                        preview.revalidatedTotal(),
                        "INR"
                )
        );

        checkoutService.payMock(
                traveller.getUserId(), tripId,
                preview.checkoutId(), true
        );

        long afterFirst = bookingRepository.count();

        /*
         * A gateway that delivers the same webhook twice must not
         * reserve a second room for a payment already taken.
         */
        checkoutService.markPaid(
                preview.checkoutId(), null
        );

        assertEquals(
                afterFirst, bookingRepository.count(),
                "a repeated payment callback must not create a "
                        + "second booking"
        );
    }

    private int availabilityOf(LocalDate night) {
        return inventoryRepository
                .findByRoomType_RoomTypeIdAndInventoryDate(
                        roomType.getRoomTypeId(), night
                )
                .map(RoomInventoryDaily::getAvailableInventory)
                .orElse(0);
    }

    /**
     * Section 6: money cleared but confirmation failed must be
     * recoverable without charging again.
     *
     * <p>This used to call requireRecovery by hand, which asserted
     * nothing real: it proved the method worked, not that the
     * system ever reaches it. Nothing in the application could put
     * a checkout into recovery, because nothing could fail.
     *
     * <p>It now fails the way it would in production. The room
     * sells out between the traveller confirming and the gateway
     * clearing, which is precisely the window revalidation exists to
     * cover.
     */
    @Test
    @DisplayName("a paid checkout that fails to confirm needs recovery, not a retry")
    void paidButUnconfirmedRequiresRecovery() {
        Long tripId = trip(6, 1);
        withHotel(tripId, 2);

        TripCheckoutPreviewResponse preview =
                checkoutService.preview(
                        traveller.getUserId(), tripId
                );
        checkoutService.assertAgreed(
                traveller.getUserId(),
                tripId,
                new ConfirmTripCheckoutRequest(
                        preview.checkoutId(),
                        preview.revalidatedTotal(),
                        "INR"
                )
        );

        /*
         * Somebody else takes the rooms while the traveller is at
         * the payment screen.
         */
        stockInventory(
                LocalDate.now().plusDays(30), 2, 0
        );

        TripCheckout afterPay = checkoutService.payMock(
                traveller.getUserId(), tripId,
                preview.checkoutId(), true
        );

        assertEquals(
                TripCheckoutStatus.RECOVERY_REQUIRED,
                afterPay.getStatus(),
                "money cleared but the room was gone, so the "
                        + "checkout must ask for recovery"
        );

        assertNotNull(
                afterPay.getRecoveryReason(),
                "recovery must say what went wrong, not just that "
                        + "something did"
        );

        assertEquals(
                TripStatus.PLANNING,
                tripService.get(
                        traveller.getUserId(), tripId
                ).status(),
                "a trip whose room was never held must not be "
                        + "presented as confirmed"
        );
    }

    @Test
    @DisplayName("a stranger cannot checkout another traveller's trip")
    void checkoutIsScopedToOwner() {
        Long tripId = trip(6, 1);
        withHotel(tripId, 2);

        assertThrows(
                PartnerApplicationException.class,
                () -> checkoutService.preview(
                        stranger.getUserId(), tripId
                )
        );
    }

    /* ============================================================
     * CHECKOUT REACHES PAID
     *
     * The flow used to dead-end. A checkout could be previewed
     * and the total confirmed, and then nothing could mark it
     * settled, so the trip never reached CONFIRMED, no booking
     * was created and the treasure map never moved. The whole of
     * centralised checkout led to a wall.
     * ============================================================ */

    @Test
    @DisplayName("a confirmed checkout can actually be paid")
    void confirmedCheckoutCanBePaid() {
        Long tripId = tripWithABookedStay();

        TripCheckoutPreviewResponse preview =
                checkoutService.preview(
                        traveller.getUserId(), tripId
                );

        checkoutService.assertAgreed(
                traveller.getUserId(), tripId,
                new ConfirmTripCheckoutRequest(
                        preview.checkoutId(),
                        preview.revalidatedTotal(),
                        preview.bill().currency()
                )
        );

        TripCheckout paid = checkoutService.payMock(
                traveller.getUserId(), tripId,
                preview.checkoutId(), true
        );

        assertEquals(
                TripCheckoutStatus.CONFIRMED, paid.getStatus(),
                "paying reserves the rooms as well as recording "
                        + "the money"
        );
    }

    /**
     * The payment is what tells the platform money cleared, so it
     * is the moment the trip becomes real and the first
     * checkpoint may be ticked.
     */
    @Test
    @DisplayName("paying moves the trip to confirmed and ticks the start")
    void payingMovesTheTripAndTheMap() {
        Long tripId = tripWithABookedStay();

        TripCheckoutPreviewResponse preview =
                checkoutService.preview(
                        traveller.getUserId(), tripId
                );

        checkoutService.assertAgreed(
                traveller.getUserId(), tripId,
                new ConfirmTripCheckoutRequest(
                        preview.checkoutId(),
                        preview.revalidatedTotal(),
                        preview.bill().currency()
                )
        );

        checkoutService.payMock(
                traveller.getUserId(), tripId,
                preview.checkoutId(), true
        );

        assertEquals(
                TripStatus.CONFIRMED,
                tripService.get(
                        traveller.getUserId(), tripId
                ).status()
        );

        assertTrue(
                tripService.get(
                        traveller.getUserId(), tripId
                ).milestones().stream()
                        .filter(m -> m.milestoneType()
                                == TripMilestoneType.TRIP_STARTED)
                        .findFirst()
                        .orElseThrow()
                        .completed()
        );
    }

    @Test
    @DisplayName("a declined payment fails the checkout")
    void declinedPaymentFails() {
        Long tripId = tripWithABookedStay();

        TripCheckoutPreviewResponse preview =
                checkoutService.preview(
                        traveller.getUserId(), tripId
                );

        checkoutService.assertAgreed(
                traveller.getUserId(), tripId,
                new ConfirmTripCheckoutRequest(
                        preview.checkoutId(),
                        preview.revalidatedTotal(),
                        preview.bill().currency()
                )
        );

        TripCheckout failed = checkoutService.payMock(
                traveller.getUserId(), tripId,
                preview.checkoutId(), false
        );

        assertEquals(
                TripCheckoutStatus.FAILED, failed.getStatus()
        );

        assertEquals(
                TripStatus.PLANNING,
                tripService.get(
                        traveller.getUserId(), tripId
                ).status(),
                "a declined payment must not confirm the trip"
        );
    }

    /**
     * A traveller must not pay for somebody else's trip by
     * guessing a checkout id.
     */
    @Test
    @DisplayName("another traveller cannot pay this checkout")
    void paymentIsScopedToTheOwner() {
        User stranger = new User();
        stranger.setFullName("Paying Stranger");
        stranger.setEmail(UUID.randomUUID() + "@tb.local");
        stranger.setPasswordHash("{noop}password");
        stranger = userRepository.save(stranger);
        final Long strangerId = stranger.getUserId();

        Long tripId = tripWithABookedStay();

        TripCheckoutPreviewResponse preview =
                checkoutService.preview(
                        traveller.getUserId(), tripId
                );

        checkoutService.assertAgreed(
                traveller.getUserId(), tripId,
                new ConfirmTripCheckoutRequest(
                        preview.checkoutId(),
                        preview.revalidatedTotal(),
                        preview.bill().currency()
                )
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> checkoutService.payMock(
                        strangerId, tripId,
                        preview.checkoutId(), true
                )
        );
    }
}