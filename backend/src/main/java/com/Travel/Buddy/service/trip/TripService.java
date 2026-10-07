package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.dto.trip.AddTripPlacesRequest;
import com.Travel.Buddy.dto.trip.CreateTripRequest;
import com.Travel.Buddy.dto.trip.SetTripRouteRequest;
import com.Travel.Buddy.dto.trip.TripCityResponse;
import com.Travel.Buddy.dto.trip.TripDetailResponse;
import com.Travel.Buddy.dto.trip.TripMilestoneResponse;
import com.Travel.Buddy.dto.trip.TripRecommendationResponse;
import com.Travel.Buddy.dto.trip.TripSelectionResponse;
import com.Travel.Buddy.entity.City;
import com.Travel.Buddy.entity.TouristPlace;
import com.Travel.Buddy.entity.Trip;
import com.Travel.Buddy.entity.TripCity;
import com.Travel.Buddy.entity.TripMilestone;
import com.Travel.Buddy.entity.TripMilestoneType;
import com.Travel.Buddy.entity.TripPlace;
import com.Travel.Buddy.entity.TripRecommendation;
import com.Travel.Buddy.entity.TripSelection;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.CityRepository;
import com.Travel.Buddy.repository.TouristPlaceRepository;
import com.Travel.Buddy.repository.TripCityRepository;
import com.Travel.Buddy.repository.TripMilestoneRepository;
import com.Travel.Buddy.repository.TripPlaceRepository;
import com.Travel.Buddy.repository.TripRecommendationRepository;
import com.Travel.Buddy.repository.TripRepository;
import com.Travel.Buddy.repository.TripSelectionRepository;
import com.Travel.Buddy.repository.UserRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Trip planning: dates, city route and places. (SRS 2.2
 * TP-01 .. TP-04)
 *
 * <p>Nothing here reserves inventory. A trip is a plan, freely
 * editable, that costs the traveller nothing until checkout.
 *
 * <p>One principle runs through the whole class: the traveller's
 * choices are authoritative. The engine proposes, warns, and
 * explains, but never silently reorders or replaces anything the
 * traveller picked.
 */
@Service
public class TripService {

    /**
     * A city stop shorter than this is flagged. Section 12 asks
     * for "impossible or extremely compressed itineraries" to be
     * warned about; below one night a traveller arrives and leaves
     * the same day, which is almost never what they meant.
     */
    private static final int MIN_SENSIBLE_NIGHTS = 1;

    /**
     * At most half the trip's nights in a single city before it
     * is called out as one-sided. Proportional rather than an
     * absolute number, so it behaves the same for a weekend and a
     * fortnight.
     */
    private static final double DOMINANT_CITY_SHARE = 0.5;

    private static final int MAX_PAGE_SIZE = 50;

    private final TripRepository tripRepository;
    private final TripCityRepository tripCityRepository;
    private final TripPlaceRepository tripPlaceRepository;
    private final TripSelectionRepository selectionRepository;
    private final TripRecommendationRepository recommendationRepository;
    private final TripMilestoneRepository milestoneRepository;
    private final TripMilestoneService milestoneService;
    private final CityRepository cityRepository;
    private final TouristPlaceRepository placeRepository;
    private final UserRepository userRepository;
    private final com.Travel.Buddy.repository.PropertyRepository propertyRepository;
    private final com.Travel.Buddy.repository.GuideRepository guideRepository;
    private final com.Travel.Buddy.repository.CabRepository cabRepository;
    private final GeoDistanceService geo;
    private final TripBillService billService;

    public TripService(
            TripRepository tripRepository,
            TripCityRepository tripCityRepository,
            TripPlaceRepository tripPlaceRepository,
            TripSelectionRepository selectionRepository,
            TripRecommendationRepository recommendationRepository,
            TripMilestoneRepository milestoneRepository,
            TripMilestoneService milestoneService,
            CityRepository cityRepository,
            TouristPlaceRepository placeRepository,
            UserRepository userRepository,
            com.Travel.Buddy.repository.PropertyRepository propertyRepository,
            com.Travel.Buddy.repository.GuideRepository guideRepository,
            com.Travel.Buddy.repository.CabRepository cabRepository,
            GeoDistanceService geo,
            TripBillService billService
    ) {
        this.tripRepository = tripRepository;
        this.tripCityRepository = tripCityRepository;
        this.tripPlaceRepository = tripPlaceRepository;
        this.selectionRepository = selectionRepository;
        this.recommendationRepository = recommendationRepository;
        this.milestoneRepository = milestoneRepository;
        this.milestoneService = milestoneService;
        this.cityRepository = cityRepository;
        this.placeRepository = placeRepository;
        this.userRepository = userRepository;
        this.propertyRepository = propertyRepository;
        this.guideRepository = guideRepository;
        this.cabRepository = cabRepository;
        this.geo = geo;
        this.billService = billService;
    }

    /* ============================================================
     * TP-01  DATES
     * ============================================================ */

    @Transactional
    public TripDetailResponse create(
            Long userId,
            CreateTripRequest request
    ) {
        User user = requireUser(userId);

        if (request.endDate().isBefore(
                request.startDate())) {
            throw PartnerApplicationException.badRequest(
                    "The end date cannot be before the start date"
            );
        }

        if (request.startDate().isBefore(
                LocalDate.now().minusDays(1))) {
            throw PartnerApplicationException.badRequest(
                    "A trip cannot start in the past"
            );
        }

        String currency = request.currency() == null
                ? "INR"
                : request.currency().toUpperCase();

        Trip trip = new Trip(
                user,
                titleFor(request),
                request.startDate(),
                request.endDate(),
                request.plannedCityCount(),
                request.budget(),
                currency
        );

        Trip saved = tripRepository.save(trip);

        seedMilestones(saved);

        return detailFor(saved);
    }

    /**
     * A title the traveller recognises in a list. Generated only
     * when they did not supply one, because "My Trip" repeated
     * forty times is useless in a sidebar.
     */
    private String titleFor(CreateTripRequest request) {
        if (request.title() != null
                && !request.title().isBlank()) {
            return request.title().trim();
        }

        int nights = (int) ChronoUnit.DAYS.between(
                request.startDate(), request.endDate()
        );

        return nights == 0
                ? "Day trip "
                + request.startDate().toString()
                : nights + "-day trip from "
                + request.startDate().toString();
    }

    /**
     * Pre-creates the two map checkpoints that bracket the whole
     * trip, incomplete. Section 15 requires the map to be driven
     * by milestone rows, which means the rows have to exist before
     * anything is reached rather than being conjured on the fly.
     */
    private void seedMilestones(Trip trip) {
        milestoneRepository.save(
                new TripMilestone(
                        trip, null, TripMilestoneType.TRIP_STARTED,
                        0, "Trip starts"
                )
        );

        /*
         * Sits at 99, immediately before the finish.
         *
         * A review is written after the journey, so placing it last
         * would make it the end of the trip. Putting it at 99 keeps
         * "Trip complete" as the closing pin while still showing the
         * traveller that sharing is a final step they own.
         */
        milestoneRepository.save(
                new TripMilestone(
                        trip, null, TripMilestoneType.REVIEW_OPENED,
                        99, "Write a review"
                )
        );

        milestoneRepository.save(
                new TripMilestone(
                        trip, null, TripMilestoneType.TRIP_COMPLETED,
                        100, "Trip complete"
                )
        );
    }

    /* ============================================================
     * TP-02, TP-03  CITY ROUTE
     * ============================================================ */

    @Transactional
    public TripDetailResponse setRoute(
            Long userId,
            Long tripId,
            SetTripRouteRequest request
    ) {
        Trip trip = requireOwned(userId, tripId);
        trip.requireEditable();

        Set<Long> requestedCityIds = new LinkedHashSet<>();
        for (SetTripRouteRequest.CityStopRequest stop
                : request.stops()) {
            requestedCityIds.add(stop.cityId());
        }

        if (requestedCityIds.size() != request.stops().size()) {
            throw PartnerApplicationException.badRequest(
                    "The same city cannot appear twice in one route"
            );
        }

        /*
         * Replaced wholesale rather than merged. A stale stop
         * that the traveller thought they had removed would
         * otherwise survive and quietly appear in the bill.
         *
         * Everything pointing at an existing stop is detached
         * before the delete. The schema declares these references
         * ON DELETE SET NULL, but relying on the database alone
         * left the test schema and the migrated schema disagreeing
         * about referential behaviour, which fails on one and
         * passes on the other. Detaching explicitly behaves the
         * same on either.
         */
        List<TripCity> existing = tripCityRepository
                .findByTrip_TripIdOrderBySequenceAsc(tripId);

        if (!existing.isEmpty()) {
            List<Long> stopIds = existing.stream()
                    .map(TripCity::getTripCityId)
                    .toList();

            for (TripSelection selection : selectionRepository
                    .findByTrip_TripIdOrderBySelectionIdAsc(tripId)) {
                if (selection.getTripCity() != null
                        && stopIds.contains(
                        selection.getTripCity()
                                .getTripCityId()
                )) {
                    selection.setTripCity(null);
                    selectionRepository.save(selection);
                }
            }

            recommendationRepository
                    .findByTrip_TripIdOrderByRankPositionAsc(tripId)
                    .stream()
                    .filter(r -> r.getTripCity() != null
                            && stopIds.contains(
                            r.getTripCity()
                                    .getTripCityId()))
                    .forEach(
                            recommendationRepository::delete);

            tripPlaceRepository
                    .findByTrip_TripIdOrderByTripPlaceIdAsc(tripId)
                    .stream()
                    .filter(p -> stopIds.contains(
                            p.getTripCity().getTripCityId()))
                    .forEach(tripPlaceRepository::delete);

            /*
             * Detached before the delete for the same reason the
             * selections above are: the migrated schema declares
             * ON DELETE SET NULL for this reference and the test
             * schema built from the entities does not, so relying
             * on the database alone fails on one and passes on
             * the other. Deleting explicitly behaves the same on
             * either.
             */
            milestoneService.clearRouteCheckpoints(tripId);

            tripCityRepository.deleteAll(existing);
            tripCityRepository.flush();
        }

        List<TripCity> saved = new ArrayList<>();

        int sequence = 1;
        for (SetTripRouteRequest.CityStopRequest stop
                : request.stops()) {

            City city = cityRepository.findById(stop.cityId())
                    .orElseThrow(() ->
                            PartnerApplicationException.notFound(
                                    "City not found: "
                                            + stop.cityId()
                            )
                    );

            TripCity tripCity = new TripCity(
                    trip, city, sequence
            );
            tripCity.stayDates(
                    stop.arrivalDate(), stop.departureDate()
            );

            saved.add(
                    tripCityRepository.save(tripCity)
            );
            sequence++;
        }

        for (TripCity tripCity : saved) {
            if (tripCity.getArrivalDate() != null
                    && tripCity.getDepartureDate() != null) {
                validateStayDates(trip, tripCity);
            }
        }

        /*
         * Only the per-city checkpoints, which the block above
         * already cleared for any replaced route. The start and
         * finish checkpoints are not tied to a stop and must
         * survive, along with any completion already recorded.
         */
        milestoneService.seedForRoute(trip, saved);

        trip.beginPlanningIfDraft();

        return detailFor(tripRepository.save(trip));
    }

    /**
     * A stay that runs past the end of the trip, or starts before
     * it, cannot be honoured. Caught here rather than at checkout
     * because a traveller fixing it now costs one edit, whereas
     * failing at payment costs the whole cart.
     */
    private void validateStayDates(
            Trip trip,
            TripCity stop
    ) {
        if (stop.getArrivalDate()
                .isBefore(trip.getStartDate())) {
            throw PartnerApplicationException.badRequest(
                    stop.getCity().getName()
                            + " starts before the trip does"
            );
        }

        if (stop.getDepartureDate()
                .isAfter(trip.getEndDate())) {
            throw PartnerApplicationException.badRequest(
                    stop.getCity().getName()
                            + " ends after the trip does"
            );
        }
    }

    /**
     * Proposes a practical order. (TP-03, section 4.2)
     *
     * <p>Nearest-neighbour from the first city. It is not a
     * TSP solver and does not claim to be: at the scale a
     * traveller actually plans, avoiding immediate backtracking
     * is what matters, and the order stays editable.
     */
    @Transactional
    public RouteSuggestionResponse suggestRoute(
            Long userId,
            Long tripId
    ) {
        Trip trip = requireOwned(userId, tripId);

        List<City> cities = new ArrayList<>(
                cityRepository.findAllById(
                        tripCityRepository
                                .findByTrip_TripIdOrderBySequenceAsc(
                                        tripId
                                )
                                .stream()
                                .map(TripCity::getCity)
                                .map(City::getCityId)
                                .toList()
                )
        );

        if (cities.size() < 2) {
            return new RouteSuggestionResponse(
                    cities.stream().map(c -> new SuggestedStop(
                            c.getCityId(), c.getName(), 1, null
                    )).toList(),
                    List.of(),
                    0.0
            );
        }

        List<City> ordered = nearestNeighbourOrder(cities);

        double totalKm = routeLengthKm(ordered);

        List<SuggestedStop> stops = new ArrayList<>();
        int sequence = 1;
        String previous = null;

        for (City city : ordered) {
            String reason = previous == null
                    ? "Starting point for this route"
                    : previous + " is the closest of your remaining "
                    + "cities, at " + describeLeg(ordered, previous)
                    + " away";

            stops.add(
                    new SuggestedStop(
                            city.getCityId(),
                            city.getName(),
                            sequence,
                            reason
                    )
            );
            previous = city.getName();
            sequence++;
        }

        List<String> warnings = itineraryWarnings(
                trip, stops.stream()
                        .map(s -> cityRepository
                                .findById(s.cityId())
                                .orElseThrow())
                        .toList()
        );

        return new RouteSuggestionResponse(
                stops, warnings, totalKm
        );
    }

    private List<City> nearestNeighbourOrder(
            List<City> cities
    ) {
        List<City> remaining = new ArrayList<>(cities);
        List<City> ordered = new ArrayList<>();

        City current = remaining.remove(0);
        ordered.add(current);

        while (!remaining.isEmpty()) {
            City nearest = null;
            BigDecimal best = null;

            for (City candidate : remaining) {
                BigDecimal km = legKm(current, candidate);

                /*
                 * A city without coordinates cannot be compared.
                 * It is appended at the end rather than dropped:
                 * silently removing a city the traveller chose
                 * would be the worst possible outcome here.
                 */
                if (km == null) {
                    continue;
                }

                if (best == null
                        || km.compareTo(best) < 0) {
                    best = km;
                    nearest = candidate;
                }
            }

            if (nearest == null) {
                ordered.addAll(remaining);
                break;
            }

            ordered.add(nearest);
            remaining.remove(nearest);
            current = nearest;
        }

        return ordered;
    }

    private BigDecimal legKm(City a, City b) {
        if (!a.hasCoordinates() || !b.hasCoordinates()) {
            return null;
        }
        return geo.distanceKm(
                a.getLatitude(), a.getLongitude(),
                b.getLatitude(), b.getLongitude()
        );
    }

    private double routeLengthKm(List<City> ordered) {
        double total = 0;

        for (int i = 0; i < ordered.size() - 1; i++) {
            BigDecimal km = legKm(
                    ordered.get(i), ordered.get(i + 1)
            );
            if (km != null) {
                total += km.doubleValue();
            }
        }

        return Math.round(total * 10) / 10.0;
    }

    private String describeLeg(
            List<City> ordered,
            String previousName
    ) {
        for (int i = 0; i < ordered.size() - 1; i++) {
            if (ordered.get(i)
                    .getName()
                    .equals(previousName)) {
                BigDecimal km = legKm(
                        ordered.get(i), ordered.get(i + 1)
                );
                return km == null
                        ? "an unknown distance"
                        : km.setScale(1, java.math.RoundingMode
                                .HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString() + " km";
            }
        }
        return "an unknown distance";
    }

    /**
     * Section 12: warn about impossible or compressed itineraries.
     *
     * <p>Warns only. The traveller's route is never rewritten
     * behind their back.
     */
    private List<String> itineraryWarnings(
            Trip trip,
            List<City> cities
    ) {
        List<String> warnings = new ArrayList<>();

        if (cities.isEmpty()) {
            return warnings;
        }

        int nights = trip.nights();

        if (nights <= 0) {
            warnings.add(
                    "Your start and end dates are the same day, so "
                            + "there is no overnight stay. Every "
                            + "hotel selection will be removed at checkout."
            );
            return warnings;
        }

        if (cities.size() > nights) {
            warnings.add(
                    "You have picked " + cities.size()
                            + " cities for " + nights
                            + (nights == 1 ? " night" : " nights")
                            + ", so at least one stop will have no "
                            + "time in it."
            );
        }

        if (cities.size() > 1 && nights < cities.size()) {
            warnings.add(
                    "There is no free day anywhere in this "
                            + "itinerary. Every day is spent "
                            + "travelling between cities."
            );
        }

        long geocoded = cities.stream()
                .filter(City::hasCoordinates)
                .count();

        if (geocoded == cities.size() && geocoded > 1) {
            List<City> ordered = new ArrayList<>(cities);
            double straightLine = routeLengthKm(ordered);
            List<City> optimised =
                    nearestNeighbourOrder(ordered);
            double optimisedKm = routeLengthKm(optimised);

            /*
             * Twenty percent is the point at which the saving is
             * large enough to be worth interrupting someone
             * about. Below it the warning is noise.
             */
            if (optimisedKm > 0
                    && straightLine > optimisedKm * 1.2) {
                warnings.add(
                        "Your current order adds about "
                                + round1(straightLine - optimisedKm)
                                + " km of driving. A suggested order "
                                + "could cut that to "
                                + round1(optimisedKm) + " km."
                );
            }
        }

        if (nights > 0
                && cities.size() == 1
                && nights >= 7) {
            warnings.add(
                    "This is a single-city trip of "
                            + nights + " nights."
            );
        }

        return warnings;
    }

    private double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /**
     * Warnings for the route as it currently stands, including
     * per-stop date problems the caller can actually fix.
     */
    @Transactional(readOnly = true)
    public List<String> warn(
            Long userId,
            Long tripId
    ) {
        Trip trip = requireOwned(userId, tripId);

        List<TripCity> stops = tripCityRepository
                .findByTrip_TripIdOrderBySequenceAsc(tripId);

        List<String> warnings = new ArrayList<>(
                itineraryWarnings(
                        trip,
                        stops.stream()
                                .map(TripCity::getCity)
                                .toList()
                )
        );

        for (TripCity stop : stops) {
            if (stop.getArrivalDate() == null
                    || stop.getDepartureDate() == null) {
                continue;
            }

            int stopNights = (int) ChronoUnit.DAYS.between(
                    stop.getArrivalDate(),
                    stop.getDepartureDate()
            );

            if (stopNights < MIN_SENSIBLE_NIGHTS) {
                warnings.add(
                        stop.getCity().getName() + " is planned for "
                                + stopNights
                                + (stopNights == 1
                                ? " night"
                                : " nights")
                                + ". Check that is intentional."
                );
            }
        }

        /*
         * Overlapping hotel stays. The planner cannot see the
         * traveller's own selections from here, so this only
         * catches dates the traveller set on the stops
         * themselves.
         */
        for (int i = 0; i < stops.size() - 1; i++) {
            TripCity a = stops.get(i);
            TripCity b = stops.get(i + 1);

            if (a.getDepartureDate() == null
                    || b.getArrivalDate() == null) {
                continue;
            }

            if (a.getDepartureDate()
                    .isAfter(b.getArrivalDate())) {
                warnings.add(
                        a.getCity().getName() + " and "
                                + b.getCity().getName()
                                + " overlap by "
                                + ChronoUnit.DAYS.between(
                                b.getArrivalDate(),
                                a.getDepartureDate()
                        ) + " day(s)."
                );
            }
        }

        return warnings;
    }

    /* ============================================================
     * TP-04  PLACES
     * ============================================================ */

    @Transactional
    public TripDetailResponse addPlaces(
            Long userId,
            Long tripId,
            AddTripPlacesRequest request
    ) {
        Trip trip = requireOwned(userId, tripId);
        trip.requireEditable();

        for (AddTripPlacesRequest.PlaceSelection item
                : request.places()) {

            TripCity tripCity = tripCityRepository
                    .findById(item.tripCityId())
                    .orElseThrow(() ->
                            PartnerApplicationException.notFound(
                                    "Trip stop not found"
                            )
                    );

            requireStopBelongsToTrip(tripCity, trip);

            TouristPlace place = placeRepository
                    .findById(item.placeId())
                    .orElseThrow(() ->
                            PartnerApplicationException.notFound(
                                    "Place not found: "
                                            + item.placeId()
                            )
                    );

            if (place.getState() == null
                    || tripCity.getCity().getState() == null
                    || !place.getState().getStateId().equals(
                    tripCity.getCity().getState().getStateId()
            )) {
                throw PartnerApplicationException.badRequest(
                        "A place must belong to the state of its trip city"
                );
            }

            /*
             * Adding the same attraction twice would double its
             * entry fee in the bill, so it is treated as a no-op
             * rather than an error: a double tap should not
             * produce a scary message.
             */
            boolean already = tripPlaceRepository
                    .findByTrip_TripIdAndPlace_PlaceId(
                            tripId, item.placeId()
                    )
                    .isPresent();

            if (already) {
                continue;
            }

            TripPlace tripPlace = new TripPlace(
                    trip, tripCity, place
            );
            tripPlace.setNote(item.note());

            tripPlaceRepository.save(tripPlace);
        }

        /*
         * Places changed, so the persisted reasons describing
         * proximity to them are now stale. The engine's own copy
         * is refreshed on the next recommendation run; until
         * then the stored text would overstate how close the
         * hotel actually is.
         */
        recommendationRepository.deleteByTrip_TripId(tripId);

        return detailFor(trip);
    }

    @Transactional
    public TripDetailResponse removePlace(
            Long userId,
            Long tripId,
            Long tripPlaceId
    ) {
        Trip trip = requireOwned(userId, tripId);
        trip.requireEditable();

        TripPlace place = tripPlaceRepository.findById(tripPlaceId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Trip place not found"
                        )
                );

        if (!place.getTrip().getTripId().equals(tripId)) {
            throw PartnerApplicationException.forbidden(
                    "That place is not on this trip"
            );
        }

        tripPlaceRepository.delete(place);
        recommendationRepository.deleteByTrip_TripId(tripId);

        return detailFor(trip);
    }

    private void requireStopBelongsToTrip(
            TripCity tripCity,
            Trip trip
    ) {
        if (!tripCity.getTrip()
                .getTripId()
                .equals(trip.getTripId())) {
            throw PartnerApplicationException.badRequest(
                    "That stop belongs to a different trip"
            );
        }
    }

    /**
     * Notes that existing selections no longer match new dates.
     *
     * <p>Nothing is moved automatically. A booking or a quoted
     * rate belongs to specific dates, and silently re-dating it
     * would promise something the partner never agreed to. The
     * traveller is told what has to be re-picked and decides.
     */
    public void warnStaleSelections(
            Trip trip,
            java.time.LocalDate newStart,
            java.time.LocalDate newEnd
    ) {
        for (TripSelection selection : selectionRepository
                .findByTrip_TripIdOrderBySelectionIdAsc(
                        trip.getTripId()
                )) {

            if (selection.getCheckIn() == null
                    || selection.getCheckOut() == null) {
                continue;
            }

            boolean outside = selection.getCheckIn()
                    .isBefore(newStart)
                    || selection.getCheckOut()
                    .isAfter(newEnd);

            if (outside) {
                selection.markUnavailable(
                        "Outside your new trip dates. Re-select it "
                                + "or remove it."
                );
                selectionRepository.save(selection);
            }
        }
    }

    /* ============================================================
     * READS
     * ============================================================ */
    @Transactional
    public TripDetailResponse get(
            Long userId,
            Long tripId
    ) {
        Trip trip = requireOwned(userId, tripId);

        milestoneService.completeTripIfFinished(trip);

        return detailFor(trip);
    }

    /**
     * Read-write on purpose, despite looking like a pure read.
     *
     * <p>{@code completeTripIfFinished} ticks the closing pin, and it
     * has to happen inside this transaction rather than a nested one:
     * under MySQL REPEATABLE READ a nested commit is invisible to the
     * outer snapshot, so the map would be assembled from stale state
     * and the pin would appear one request late.
     */
    @Transactional
    public List<TripDetailResponse> myTrips(
            Long userId,
            int page,
            int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        return tripRepository
                .findByUser_UserIdOrderByCreatedAtDesc(
                        userId,
                        PageRequest.of(safePage, safeSize)
                )
                .getContent()
                .stream()
                .map(trip -> {
                    milestoneService.completeTripIfFinished(trip);

                    return detailFor(trip);
                })
                .toList();
    }

    /**
     * Assembles the whole trip in one place, so every screen
     * sees the same state.
     */
    @Transactional(readOnly = true)
    public TripDetailResponse detailFor(Trip trip) {
        Long tripId = trip.getTripId();

        List<TripCity> stops = tripCityRepository
                .findByTrip_TripIdOrderBySequenceAsc(tripId);

        List<TripCityResponse> cityResponses = stops.stream()
                .map(stop -> new TripCityResponse(
                        stop.getTripCityId(),
                        stop.getCity().getCityId(),
                        stateIdOf(stop.getCity()),
                        stop.getCity().getName(),
                        stop.getCity().getSlug(),
                        stop.getSequence(),
                        stop.getArrivalDate(),
                        stop.getDepartureDate(),
                        stop.getSequenceReason(),
                        stop.getCity().getLatitude(),
                        stop.getCity().getLongitude(),
                        tripPlaceRepository
                                .findByTripCity_TripCityId(
                                        stop.getTripCityId()
                                ).size()
                ))
                .toList();

        List<TripSelectionResponse> selections =
                selectionRepository
                        .findByTrip_TripIdOrderBySelectionIdAsc(tripId)
                        .stream()
                        .map(s -> TripSelectionResponse.of(
                                s,
                                selectionTargetName(s),
                                s.getTripCity() == null
                                        ? null
                                        : s.getTripCity().getCity()
                                        .getName(),
                                s.getRoomType() == null
                                        ? null
                                        : s.getRoomType().getCategoryName(),
                                s.getPlace() == null
                                        ? null
                                        : s.getPlace().getName()
                        ))
                        .toList();

        List<TripRecommendationResponse> recommendations =
                recommendationRepository
                        .findByTrip_TripIdOrderByRankPositionAsc(
                                tripId
                        )
                        .stream()
                        .map(r -> TripRecommendationResponse.of(
                                r,
                                r.getTripCity() == null
                                        ? null
                                        : r.getTripCity().getCity()
                                        .getName()
                        ))
                        .toList();

        List<TripMilestoneResponse> milestones =
                milestoneRepository
                        .findByTrip_TripIdOrderByProgressPercentAsc(
                                tripId
                        )
                        .stream()
                        .map(TripMilestoneResponse::of)
                        .toList();

        return TripDetailResponse.of(
                trip, cityResponses, selections,
                recommendations, milestones
        );
    }

    /**
     * Resolves a display name for whatever the selection points
     * at. Polymorphic targets mean a failed lookup must produce
     * a readable row rather than an exception.
     */
    /**
     * The name a traveller will recognise.
     *
     * <p>Previously this resolved only ACTIVITY, and every other
     * type came back as the literal "Item 7": a database id
     * dressed up as a product name. A traveller who chose Coastal
     * Heritage Resort saw "Item 1" in their own cart.
     *
     * <p>Unresolvable returns null rather than a placeholder. An
     * absent name is honest about being unknown; "Item 7" reads
     * like something real that simply has no name yet.
     */
    /**
     * The stop's state, as a Long.
     *
     * <p>State.stateId is an Integer while every other id in this
     * response is a Long, so the conversion happens in one place
     * rather than as a cast at each call site.
     */
    private Long stateIdOf(
            com.Travel.Buddy.entity.City city
    ) {
        return city.getState() == null
                || city.getState().getStateId() == null
                ? null
                : city.getState().getStateId().longValue();
    }

    private String selectionTargetName(TripSelection s) {
        if (s.getSelectionType() == com.Travel.Buddy.entity
                .TripSelectionType.ACTIVITY
                && s.getPlace() != null) {
            return s.getPlace().getName();
        }

        Long targetId = s.getTargetId();

        if (targetId == null) {
            return null;
        }

        return switch (s.getSelectionType()) {
            case HOTEL -> propertyRepository
                    .findById(targetId)
                    .map(com.Travel.Buddy.entity.Property::getName)
                    .orElse(null);

            case GUIDE -> guideRepository
                    .findById(targetId)
                    .map(guide -> guide.getUser() == null
                            ? null
                            : guide.getUser().getFullName())
                    .orElse(null);

            case CAB -> cabRepository
                    .findById(targetId)
                    .map(com.Travel.Buddy.entity.Cab::getVehicleName)
                    .orElse(null);

            /*
             * The cart resolves the place when the selection is
             * created; this covers rows written before that
             * link existed so the bill can still name them.
             */
            case ACTIVITY -> placeRepository
                    .findById(targetId)
                    .map(com.Travel.Buddy.entity
                            .TouristPlace::getName)
                    .orElse(null);

            default -> null;
        };
    }

    /* ============================================================
     * HELPERS
     * ============================================================ */

    public Trip requireOwned(Long userId, Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Trip not found"
                        )
                );

        if (trip.getUser() == null
                || !trip.getUser()
                        .getUserId()
                        .equals(userId)) {
            throw PartnerApplicationException.forbidden(
                    "This is not your trip"
            );
        }

        return trip;
    }

    /** Alias for controllers that need the trip itself. */
    public Trip ownedTrip(Long userId, Long tripId) {
        return requireOwned(userId, tripId);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "User not found"
                        )
                );
    }

    /* ============================================================
     * NESTED TYPES
     * ============================================================ */

    public record SuggestedStop(
            Long cityId,
            String cityName,
            int sequence,
            String reason
    ) {
    }

    public record RouteSuggestionResponse(
            List<SuggestedStop> stops,
            List<String> warnings,
            double totalDistanceKm
    ) {
    }
}
