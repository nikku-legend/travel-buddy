package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.dto.trip.TripDetailResponse;
import com.Travel.Buddy.dto.trip.TripMilestoneResponse;
import com.Travel.Buddy.entity.*;
import com.Travel.Buddy.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The treasure map. (SRS 2.2 TP-11)
 *
 * <p>These exist because the map could not move. The entity had a
 * {@code completed} flag with no setter and no caller, the service
 * seeded only the first and last checkpoints, and the five
 * in-between ones were never created at all. A traveller paid for a
 * trip and watched a map with two grey pins on it.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Treasure map milestones (SRS 2.2 TP-11)")
class TripMilestoneServiceTest {

    @Autowired
    private TripService tripService;
    @Autowired
    private TripMilestoneService milestoneService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private StateRepository stateRepository;
    @Autowired
    private CityRepository cityRepository;

    private static State sharedState;

    private User traveller;
    private City first;
    private City second;

    @BeforeEach
    void setUp() {
        traveller = user("Map Traveller");

        if (sharedState == null) {
            Country country = countryRepository
                    .findByIsoCode("TBM")
                    .orElseGet(() -> {
                        Country created = new Country();
                        created.setName("Map Land");
                        created.setIsoCode("TBM");
                        return created;
                    });
            country = countryRepository.save(country);

            sharedState = new State();
            sharedState.setName("Map State");
            sharedState.setCountry(country);
            sharedState.setRegionZone(RegionZone.EAST);
            sharedState = stateRepository.save(sharedState);
        }

        first = city("Alpha");
        second = city("Beta");
    }

    private City city(String name) {
        City city = new City(
                sharedState,
                "TM-" + UUID.randomUUID()
                        .toString().substring(0, 6),
                "tm-" + UUID.randomUUID()
                        .toString().substring(0, 6)
        );
        return cityRepository.save(city);
    }

    private User user(String name) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(UUID.randomUUID() + "@tb.local");
        user.setPasswordHash("{noop}password");
        return userRepository.save(user);
    }

    private TripDetailResponse trip() {
        LocalDate start = LocalDate.now().plusDays(120);
        LocalDate end = start.plusDays(6);

        TripDetailResponse created = tripService.create(
                traveller.getUserId(),
                new com.Travel.Buddy.dto.trip.CreateTripRequest(
                        "Map Trip", start, end, 2, null, "INR"
                )
        );

        return tripService.setRoute(
                traveller.getUserId(),
                created.tripId(),
                new com.Travel.Buddy.dto.trip.SetTripRouteRequest(
                        List.of(
                                stop(first, start, start.plusDays(3)),
                                stop(second,
                                        start.plusDays(3), end)
                        )
                )
        );
    }

    private com.Travel.Buddy.dto.trip.SetTripRouteRequest.CityStopRequest
            stop(City city, LocalDate from, LocalDate to) {
        return new com.Travel.Buddy.dto.trip.SetTripRouteRequest
                .CityStopRequest(
                city.getCityId(), from, to
        );
    }

    private List<TripMilestoneResponse> milestonesOf(
            TripDetailResponse trip
    ) {
        return trip.milestones();
    }

    private TripMilestoneResponse ofType(
            List<TripMilestoneResponse> milestones,
            TripMilestoneType type
    ) {
        return milestones.stream()
                .filter(m -> m.milestoneType() == type)
                .findFirst()
                .orElse(null);
    }

    /* ============================================================
     * THE MAP HAS CHECKPOINTS
     * ============================================================ */

    /**
     * Before this, a routed trip had exactly two pins: the start
     * and the finish. The five in-between checkpoint types were
     * never created for any trip in the system.
     */
    @Test
    @DisplayName("a routed trip has a checkpoint for every stop")
    void routeCreatesCheckpoints() {
        TripDetailResponse trip = trip();

        List<TripMilestoneResponse> milestones =
                milestonesOf(trip);

        assertEquals(
                2,
                milestones.stream()
                        .filter(m -> m.milestoneType()
                                == TripMilestoneType.CITY_ARRIVED)
                        .count(),
                "two stops means two arrival checkpoints"
        );

        assertEquals(
                2,
                milestones.stream()
                        .filter(m -> m.milestoneType()
                                == TripMilestoneType.CITY_DEPARTED)
                        .count()
        );

        assertNotNull(
                ofType(milestones, TripMilestoneType.TRIP_STARTED)
        );
        assertNotNull(
                ofType(milestones, TripMilestoneType.TRIP_COMPLETED)
        );
    }

    /**
     * A checkpoint that cannot be ordered against the others makes
     * the map ambiguous, so positions must be strictly increasing
     * along the journey.
     */
    @Test
    @DisplayName("checkpoints are ordered along the journey")
    void checkpointsAreInJourneyOrder() {
        List<TripMilestoneResponse> milestones =
                milestonesOf(trip());

        int previous = Integer.MIN_VALUE;

        for (TripMilestoneResponse milestone : milestones) {
            assertTrue(
                    milestone.progressPercent() > previous,
                    "positions must strictly increase, but "
                            + milestone.label() + " at "
                            + milestone.progressPercent()
                            + " did not follow " + previous
            );
            previous = milestone.progressPercent();
        }
    }

    @Test
    @DisplayName("a stop's checkpoints are named for that stop")
    void checkpointsAreNamed() {
        List<TripMilestoneResponse> milestones =
                milestonesOf(trip());

        assertTrue(
                milestones.stream().anyMatch(
                        m -> m.label().contains(first.getName())
                ),
                "a pin the traveller cannot place is decoration"
        );
    }

    /* ============================================================
     * NOTHING IS COMPLETED BY ITSELF
     * ============================================================ */

    /**
     * The core honesty rule. Planning a route is not travelling.
     */
    @Test
    @DisplayName("a freshly routed trip claims no progress")
    void freshTripClaimsNoProgress() {
        TripDetailResponse trip = trip();

        assertTrue(
                milestonesOf(trip).stream()
                        .noneMatch(TripMilestoneResponse::completed),
                "nothing has happened yet, so nothing may be ticked"
        );
    }

    /* ============================================================
     * THE MAP MOVES
     * ============================================================ */

    @Test
    @DisplayName("a real event completes its checkpoint")
    void realEventCompletesCheckpoint() {
        TripDetailResponse trip = trip();
        Trip entity = tripService.requireOwned(
                traveller.getUserId(), trip.tripId()
        );

        assertTrue(
                milestoneService.complete(
                        entity, TripMilestoneType.TRIP_STARTED
                ),
                "the start checkpoint exists and should complete"
        );

        TripDetailResponse after = tripService.get(
                traveller.getUserId(), trip.tripId()
        );

        assertTrue(
                ofType(milestonesOf(after),
                        TripMilestoneType.TRIP_STARTED)
                        .completed()
        );
    }

    /**
     * A payment webhook and a booking confirmation can both report
     * the same arrival. The second must not be a second event, and
     * must not move the recorded time.
     */
    @Test
    @DisplayName("completing twice is not a second completion")
    void completionIsIdempotent() {
        TripDetailResponse trip = trip();
        Trip entity = tripService.requireOwned(
                traveller.getUserId(), trip.tripId()
        );

        assertTrue(milestoneService.complete(
                entity, TripMilestoneType.TRIP_STARTED));

        java.time.LocalDateTime first = ofType(
                milestonesOf(tripService.get(
                        traveller.getUserId(), trip.tripId())),
                TripMilestoneType.TRIP_STARTED
        ).completedAt();

        assertFalse(
                milestoneService.complete(
                        entity, TripMilestoneType.TRIP_STARTED
                ),
                "a repeated event reports no change"
        );

        java.time.LocalDateTime second = ofType(
                milestonesOf(tripService.get(
                        traveller.getUserId(), trip.tripId())),
                TripMilestoneType.TRIP_STARTED
        ).completedAt();

        assertEquals(
                first, second,
                "the recorded time must not move on a repeat"
        );
    }

    /**
     * A webhook reporting an arrival for a route that was never
     * set must not fail the request; there is simply no pin to
     * tick.
     */
    @Test
    @DisplayName("an event for a checkpoint that does not exist is a no-op")
    void unknownCheckpointIsANoOp() {
        TripDetailResponse trip = trip();
        Trip entity = tripService.requireOwned(
                traveller.getUserId(), trip.tripId()
        );

        assertFalse(
                milestoneService.complete(
                        entity, TripMilestoneType.CHECKED_IN
                ),
                "no check-in checkpoint exists for a trip with no "
                        + "stay, so there is nothing to complete"
        );
    }

    /* ============================================================
     * REPLACING THE ROUTE
     * ============================================================ */

    /**
     * The route is replaced wholesale, so the old stops' pins must
     * go with them. A pin left pointing at a removed stop is a pin
     * that leads nowhere.
     */
    @Test
    @DisplayName("replacing the route does not leave orphaned pins")
    void replacingTheRouteRebuildsThePins() {
        TripDetailResponse trip = trip();

        LocalDate start = LocalDate.now().plusDays(120);
        LocalDate end = start.plusDays(6);

        TripDetailResponse replaced = tripService.setRoute(
                traveller.getUserId(),
                trip.tripId(),
                new com.Travel.Buddy.dto.trip.SetTripRouteRequest(
                        List.of(stop(first, start, end))
                )
        );

        List<TripMilestoneResponse> milestones =
                milestonesOf(replaced);

        assertEquals(
                1,
                milestones.stream()
                        .filter(m -> m.milestoneType()
                                == TripMilestoneType.CITY_ARRIVED)
                        .count(),
                "one stop means one arrival checkpoint, not two"
        );

        /*
         * Five: start, review and finish, plus arrival and
         * departure for the one stop. The review pin is not
         * route-scoped, so replacing the route must not remove it.
         */
        assertEquals(
                5,
                milestones.size(),
                "one stop adds two pins to the start, review and finish"
        );
    }
}