package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.dto.trip.CitySuggestionResponse;
import com.Travel.Buddy.dto.trip.CreateTripRequest;
import com.Travel.Buddy.dto.trip.SetTripDatesRequest;
import com.Travel.Buddy.dto.trip.SetTripPreferencesRequest;
import com.Travel.Buddy.dto.trip.SetTripScopeRequest;
import com.Travel.Buddy.dto.trip.TripDetailResponse;
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
 * The SRS 2.3 Custom Trip Planner input flow. (TP-01 .. TP-06)
 *
 * <p>2.3 changed the order of the wizard: planning now starts
 * with who is travelling and roughly where, before dates. These
 * tests exist because that reorder is the substantive difference
 * from 2.2, and because each input feeds a different downstream
 * recommendation.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Custom Trip Planner inputs (SRS 2.3 TP-01..TP-06)")
class TripPlannerInputServiceTest {

    @Autowired
    private TripPlannerInputService inputService;
    @Autowired
    private TripService tripService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CityRepository cityRepository;
    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private StateRepository stateRepository;

    private User traveller;
    private User stranger;

    @BeforeEach
    void setUp() {
        traveller = user("Wizard Traveller");
        stranger = user("Wizard Stranger");

        if (stateRepository.findAll().isEmpty()) {
            Country country = new Country();
            country.setName("Testland");
            country.setIsoCode("WZ");
            country = countryRepository.save(country);

            State state = new State();
            state.setName("Wizard State");
            state.setCountry(country);
            state.setRegionZone(RegionZone.EAST);
            stateRepository.save(state);
        }
    }

    private User user(String name) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(UUID.randomUUID() + "@tb.local");
        user.setPasswordHash("{noop}password");
        return userRepository.save(user);
    }

    private Long trip(int nights) {
        LocalDate start = LocalDate.now().plusDays(40);
        return tripService.create(
                traveller.getUserId(),
                new CreateTripRequest(
                        "Wizard Trip", start, start.plusDays(nights),
                        2, null, "INR"
                )
        ).tripId();
    }

    private SetTripPreferencesRequest preferences(
            int travelers,
            Integer adults,
            Integer children,
            String style
    ) {
        return new SetTripPreferencesRequest(
                travelers, adults, children, style
        );
    }

    /* ============================================================
     * TP-02  TRAVELER COUNT
     * ============================================================ */

    @Test
    @DisplayName("traveller count is recorded and drives room count")
    void travelerCountDrivesRooms() {
        Long tripId = trip(5);

        TripDetailResponse trip = inputService.setPreferences(
                traveller.getUserId(),
                tripId,
                preferences(4, 2, 2, null)
        );

        assertEquals(4, trip.travelerCount());
        assertEquals(2, trip.adultCount());
        assertEquals(2, trip.childCount());
        assertEquals(2, trip.roomsRequired());
    }

    @Test
    @DisplayName("a party split that does not add up is refused")
    void inconsistentPartySplitRejected() {
        Long tripId = trip(5);

        assertThrows(
                IllegalArgumentException.class,
                () -> inputService.setPreferences(
                        traveller.getUserId(),
                        tripId,
                        preferences(4, 3, 2, null)
                )
        );
    }

    @Test
    @DisplayName("a single traveller still needs one room")
    void soloTravellerGetsOneRoom() {
        Long tripId = trip(3);

        TripDetailResponse trip = inputService.setPreferences(
                traveller.getUserId(),
                tripId,
                preferences(1, 1, 0, null)
        );

        assertEquals(1, trip.roomsRequired());
    }

    @Test
    @DisplayName("an odd party size rounds up")
    void oddPartyRoundsUp() {
        Long tripId = trip(3);

        TripDetailResponse trip = inputService.setPreferences(
                traveller.getUserId(),
                tripId,
                preferences(3, 2, 1, null)
        );

        assertEquals(2, trip.roomsRequired());
    }

    /* ============================================================
     * TP-05  TRAVEL STYLE
     * ============================================================ */

    @Test
    @DisplayName("travel style is recorded in either mode")
    void travelStyleRecorded() {
        Long tripId = trip(4);

        TripDetailResponse trip = inputService.setPreferences(
                traveller.getUserId(),
                tripId,
                preferences(2, 2, 0, "premium")
        );

        assertEquals(TravelStyle.PREMIUM, trip.travelStyle());
    }

    @Test
    @DisplayName("an unknown travel style is refused by name")
    void unknownStyleRejected() {
        Long tripId = trip(4);

        PartnerApplicationException error = assertThrows(
                PartnerApplicationException.class,
                () -> inputService.setPreferences(
                        traveller.getUserId(),
                        tripId,
                        preferences(2, 2, 0, "backpacker")
                )
        );

        assertTrue(error.getMessage()
                .contains("BUDGET"));
    }

    /**
     * The SRS is explicit that budget is not "cheapest wins" and
     * premium is not "budget ignored". Both weights must stay
     * non-zero or the modes would become a hard filter, which the
     * spec rules out.
     */
    @Test
    @DisplayName("both styles weigh price and quality, neither is absolute")
    void neitherStyleIsAnAbsoluteFilter() {
        assertTrue(TravelStyle.BUDGET.priceWeight() > 0);
        assertTrue(TravelStyle.BUDGET.qualityWeight() > 0);
        assertTrue(TravelStyle.PREMIUM.priceWeight() > 0);
        assertTrue(TravelStyle.PREMIUM.qualityWeight() > 0);

        assertTrue(
                TravelStyle.BUDGET.priceWeight()
                        > TravelStyle.PREMIUM.priceWeight(),
                "budget should lean harder on price than premium"
        );
        assertTrue(
                TravelStyle.PREMIUM.qualityWeight()
                        > TravelStyle.BUDGET.qualityWeight(),
                "premium should lean harder on quality than budget"
        );
    }

    /* ============================================================
     * TP-03  ZONE AND REGION
     * ============================================================ */

    @Test
    @DisplayName("zone and region are recorded")
    void scopeRecorded() {
        Long tripId = trip(5);

        TripDetailResponse trip = inputService.setScope(
                traveller.getUserId(),
                tripId,
                new SetTripScopeRequest("EAST", "Coastal Odisha")
        );

        assertEquals("EAST", trip.zone());
        assertEquals("Coastal Odisha", trip.regionName());
    }

    /* ============================================================
     * TP-04  DATES
     * ============================================================ */

    @Test
    @DisplayName("dates can be changed after the trip exists")
    void datesCanBeChanged() {
        Long tripId = trip(5);
        LocalDate newStart = LocalDate.now().plusDays(60);

        TripDetailResponse trip = inputService.setDates(
                traveller.getUserId(),
                tripId,
                newStart,
                newStart.plusDays(3)
        );

        assertEquals(3, trip.nights());
        assertEquals(newStart, trip.startDate());
    }

    @Test
    @DisplayName("an end date before the start is refused")
    void backwardsDatesRejected() {
        Long tripId = trip(5);
        LocalDate start = LocalDate.now().plusDays(60);

        assertThrows(
                PartnerApplicationException.class,
                () -> inputService.setDates(
                        traveller.getUserId(),
                        tripId, start, start.minusDays(2)
                )
        );
    }

    @Test
    @DisplayName("a same-day trip is refused as too short to stay")
    void sameDayTripRejected() {
        Long tripId = trip(5);
        LocalDate start = LocalDate.now().plusDays(60);

        PartnerApplicationException error = assertThrows(
                PartnerApplicationException.class,
                () -> inputService.setDates(
                        traveller.getUserId(),
                        tripId, start, start
                )
        );

        assertTrue(error.getMessage()
                .contains("one night"));
    }

    @Test
    @DisplayName("a past start date is refused")
    void pastDatesRejected() {
        Long tripId = trip(5);
        LocalDate past = LocalDate.now().minusDays(5);

        assertThrows(
                PartnerApplicationException.class,
                () -> inputService.setDates(
                        traveller.getUserId(),
                        tripId, past, past.plusDays(3)
                )
        );
    }

    /* ============================================================
     * TP-06  CITY SUGGESTIONS
     * ============================================================ */

    @Test
    @DisplayName("city suggestions need a zone first")
    void suggestionsRequireAZone() {
        Long tripId = trip(5);

        assertThrows(
                PartnerApplicationException.class,
                () -> inputService.suggestCities(
                        traveller.getUserId(), tripId
                )
        );
    }

    @Test
    @DisplayName("every suggested city explains why it was suggested")
    void suggestionsAreExplainable() {
        Long tripId = trip(5);
        inputService.setScope(
                traveller.getUserId(),
                tripId,
                new SetTripScopeRequest("EAST", "Testland")
        );

        List<CitySuggestionResponse> suggestions =
                inputService.suggestCities(
                        traveller.getUserId(), tripId
                );

        for (CitySuggestionResponse city : suggestions) {
            assertNotNull(city.reason(),
                    "FR-40 requires an explanation");
            assertFalse(city.reason().isBlank());
        }
    }

    @Test
    @DisplayName("suggesting cities selects nothing")
    void suggestionsDoNotSelect() {
        Long tripId = trip(5);
        inputService.setScope(
                traveller.getUserId(),
                tripId,
                new SetTripScopeRequest("EAST", "Testland")
        );

        inputService.suggestCities(
                traveller.getUserId(), tripId
        );

        assertTrue(
                tripService.get(traveller.getUserId(), tripId)
                        .cities()
                        .isEmpty(),
                "the engine proposes, the traveller decides"
        );
    }

    @Test
    @DisplayName("the suggestion list is capped so it stays a decision")
    void suggestionsAreCapped() {
        Long tripId = trip(5);
        inputService.setScope(
                traveller.getUserId(),
                tripId,
                new SetTripScopeRequest("EAST", "Testland")
        );

        assertTrue(
                inputService.suggestCities(
                                traveller.getUserId(), tripId
                        ).size() <= 8
        );
    }

    /* ============================================================
     * OWNERSHIP
     * ============================================================ */

    @Test
    @DisplayName("a stranger cannot drive someone else's wizard")
    void wizardIsScopedToOwner() {
        Long tripId = trip(5);

        assertThrows(
                PartnerApplicationException.class,
                () -> inputService.setPreferences(
                        stranger.getUserId(),
                        tripId,
                        preferences(2, 2, 0, "BUDGET")
                )
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> inputService.setScope(
                        stranger.getUserId(),
                        tripId,
                        new SetTripScopeRequest("EAST", "X")
                )
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> inputService.suggestCities(
                        stranger.getUserId(), tripId
                )
        );
    }
}