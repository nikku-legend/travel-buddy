package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.dto.trip.CitySuggestionResponse;
import com.Travel.Buddy.dto.trip.SetTripPreferencesRequest;
import com.Travel.Buddy.dto.trip.SetTripScopeRequest;
import com.Travel.Buddy.dto.trip.TripDetailResponse;
import com.Travel.Buddy.entity.City;
import com.Travel.Buddy.entity.RegionZone;
import com.Travel.Buddy.entity.TouristPlace;
import com.Travel.Buddy.entity.TravelStyle;
import com.Travel.Buddy.entity.Trip;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.CityRepository;
import com.Travel.Buddy.repository.TouristPlaceRepository;
import com.Travel.Buddy.repository.TripRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The SRS 2.3 Custom Trip Planner input flow. (TP-01 .. TP-06)
 *
 * <pre>
 *   PERSONS -> ZONE -> REGION -> DATES -> BUDGET/PREMIUM
 *            -> CITY SUGGESTIONS -> ...
 * </pre>
 *
 * <p>This is a genuine change from 2.2, where planning started
 * with dates. In 2.3 the traveller states who is going and roughly
 * where first, because zone and party size are what constrain
 * every downstream recommendation: occupancy, cab capacity and
 * even which cities are worth proposing.
 *
 * <p>The steps are separately addressable on purpose. A wizard
 * that only submits at the end gives no feedback until the last
 * screen, and the traveller cannot go back and change step 2
 * without losing steps 3 to 5.
 */
@Service
public class TripPlannerInputService {

    /**
     * How many cities to propose. Capped so the city-selection
     * screen stays a decision rather than a list to scroll.
     */
    private static final int MAX_SUGGESTIONS = 8;

    /**
     * A trip is not really a day trip, and the SRS asks for a
     * duration to be calculated and validated. One night is the
     * floor.
     */
    private static final int MIN_NIGHTS = 1;

    private final TripRepository tripRepository;
    private final CityRepository cityRepository;
    private final TouristPlaceRepository placeRepository;
    private final TripService tripService;

    public TripPlannerInputService(
            TripRepository tripRepository,
            CityRepository cityRepository,
            TouristPlaceRepository placeRepository,
            TripService tripService
    ) {
        this.tripRepository = tripRepository;
        this.cityRepository = cityRepository;
        this.placeRepository = placeRepository;
        this.tripService = tripService;
    }

    /* ============================================================
     * TP-02, TP-05  PERSONS AND STYLE
     * ============================================================ */

    @Transactional
    public TripDetailResponse setPreferences(
            Long userId,
            Long tripId,
            SetTripPreferencesRequest request
    ) {
        Trip trip = tripService.requireOwned(userId, tripId);
        trip.requireEditable();

        trip.setParty(
                request.travelerCount(),
                request.adultCount(),
                request.childCount()
        );

        if (request.travelStyle() != null
                && !request.travelStyle().isBlank()) {
            try {
                trip.setTravelStyle(
                        TravelStyle.valueOf(
                                request.travelStyle()
                                        .toUpperCase(Locale.ROOT)
                        )
                );
            } catch (IllegalArgumentException e) {
                throw PartnerApplicationException.badRequest(
                        "Travel style must be BUDGET or PREMIUM"
                );
            }
        }

        return tripService.detailFor(
                tripRepository.save(trip)
        );
    }

    /* ============================================================
     * TP-03  ZONE AND REGION
     * ============================================================ */

    @Transactional
    public TripDetailResponse setScope(
            Long userId,
            Long tripId,
            SetTripScopeRequest request
    ) {
        Trip trip = tripService.requireOwned(userId, tripId);
        trip.requireEditable();

        trip.setScope(
                request.zone().trim().toUpperCase(Locale.ROOT),
                request.regionName().trim()
        );

        return tripService.detailFor(
                tripRepository.save(trip)
        );
    }

    /* ============================================================
     * TP-04  DATES
     * ============================================================ */

    /**
     * Dates are set separately from the rest of the wizard so
     * changing them can revalidate the rest of the itinerary
     * without the traveller re-entering zone or party size.
     */
    @Transactional
    public TripDetailResponse setDates(
            Long userId,
            Long tripId,
            java.time.LocalDate start,
            java.time.LocalDate end
    ) {
        Trip trip = tripService.requireOwned(userId, tripId);
        trip.requireEditable();

        if (end.isBefore(start)) {
            throw PartnerApplicationException.badRequest(
                    "The end date cannot be before the start date"
            );
        }

        if (java.time.temporal.ChronoUnit.DAYS
                .between(start, end) < MIN_NIGHTS) {
            throw PartnerApplicationException.badRequest(
                    "A trip must be at least one night. Choose an "
                            + "end date at least a day after the start."
            );
        }

        if (start.isBefore(java.time.LocalDate.now())) {
            throw PartnerApplicationException.badRequest(
                    "A trip cannot start in the past"
            );
        }

        /*
         * Moves past dates, leave selections behind. Silently
         * re-dating a booked room would promise something the
         * partner has not agreed to, so the traveller is told
         * exactly what has to be re-picked.
         */
        if (trip.getStartDate() != null) {
            boolean datesChanged =
                    !trip.getStartDate().equals(start)
                            || !trip.getEndDate().equals(end);

            if (datesChanged) {
                tripService.warnStaleSelections(trip, start, end);
            }
        }

        trip.setStartDate(start);
        trip.setEndDate(end);

        return tripService.detailFor(
                tripRepository.save(trip)
        );
    }

    /* ============================================================
     * TP-06  CITY SUGGESTIONS
     * ============================================================ */

    /**
     * Proposes cities inside the chosen scope.
     *
     * <p>Suggestions, never selections: section 4.1 is explicit
     * that the engine proposes and the traveller decides. Nothing
     * here writes to trip_cities.
     */
    @Transactional(readOnly = true)
    public List<CitySuggestionResponse> suggestCities(
            Long userId,
            Long tripId
    ) {
        Trip trip = tripService.requireOwned(userId, tripId);

        if (trip.getZone() == null
                || trip.getZone().isBlank()) {
            throw PartnerApplicationException.badRequest(
                    "Choose a zone before asking for city suggestions"
            );
        }

        RegionZone zone = parseZone(trip.getZone());

        List<City> candidates = new ArrayList<>(
                cityRepository.findAll().stream()
                        .filter(c -> c.getState() != null)
                        .filter(c -> zone == null
                                || c.getState().getRegionZone() == zone)
                        .toList()
        );

        /*
         * Ranked by how much the traveller already has reason to
         * care: attractions count, then centrality. Popularity is
         * approximated by content rather than stored, because a
         * view counter nobody resets is worse than an honest
         * proxy.
         */
        record Scored(City city, int places, String reason) {
        }

        List<Scored> scored = new ArrayList<>();

        for (City city : candidates) {
            long places = placeRepository
                    .findAll().stream()
                    .filter(p -> p.getState() != null
                            && p.getState().getStateId()
                            .equals(city.getState().getStateId()))
                    .count();

            scored.add(new Scored(
                    city,
                    (int) Math.min(places, Integer.MAX_VALUE),
                    reasonFor(city, (int) Math.min(
                            places, Integer.MAX_VALUE))
            ));
        }

        scored.sort(
                Comparator.comparingInt(Scored::places)
                        .reversed()
                        .thenComparing(s -> s.city().getName())
        );

        return scored.stream()
                .limit(MAX_SUGGESTIONS)
                .map(s -> new CitySuggestionResponse(
                        s.city().getCityId(),
                        s.city().getName(),
                        s.city().getState().getName(),
                        s.city().getLatitude(),
                        s.city().getLongitude(),
                        s.places(),
                        s.reason()
                ))
                .toList();
    }

    /**
     * FR-40: every suggestion says why. An empty string would be
     * worse than no reason at all, because it looks like a
     * missing feature.
     */
    private String reasonFor(City city, int places) {
        StringBuilder reason = new StringBuilder();

        if (city.isCapital()) {
            reason.append("Regional capital");
        }

        if (places > 0) {
            if (reason.length() > 0) {
                reason.append(", ");
            }
            reason.append(places)
                    .append(places == 1
                            ? " popular place"
                            : " popular places");
        }

        if (city.hasCoordinates()) {
            if (reason.length() > 0) {
                reason.append(", ");
            }
            reason.append("available on the map");
        }

        return reason.length() == 0
                ? "Available in your chosen zone"
                : reason.toString();
    }

    private RegionZone parseZone(String zone) {
        try {
            return RegionZone.valueOf(zone.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            /*
             * A zone the platform does not recognise is treated as
             * "no zone constraint" rather than an error: the
             * traveller may be planning somewhere not yet
             * classified, and refusing outright would be worse
             * than showing too much.
             */
            return null;
        }
    }
}