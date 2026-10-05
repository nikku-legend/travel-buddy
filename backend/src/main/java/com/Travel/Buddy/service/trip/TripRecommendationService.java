package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.dto.trip.TripRecommendationResponse;
import com.Travel.Buddy.entity.Property;
import com.Travel.Buddy.entity.PropertyStatus;
import com.Travel.Buddy.entity.ReviewSummary;
import com.Travel.Buddy.entity.ReviewTargetType;
import com.Travel.Buddy.entity.RoomInventoryDaily;
import com.Travel.Buddy.entity.RoomType;
import com.Travel.Buddy.entity.TouristPlace;
import com.Travel.Buddy.entity.TravelStyle;
import com.Travel.Buddy.entity.Trip;
import com.Travel.Buddy.entity.TripCity;
import com.Travel.Buddy.entity.TripPlace;
import com.Travel.Buddy.entity.TripRecommendation;
import com.Travel.Buddy.entity.TripRecommendationType;
import com.Travel.Buddy.repository.PropertyAmenityRepository;
import com.Travel.Buddy.repository.PropertyCancellationTermRepository;
import com.Travel.Buddy.repository.PropertyRepository;
import com.Travel.Buddy.repository.ReviewSummaryRepository;
import com.Travel.Buddy.repository.RoomInventoryRepository;
import com.Travel.Buddy.repository.RoomTypeRepository;
import com.Travel.Buddy.repository.TripCityRepository;
import com.Travel.Buddy.repository.TripPlaceRepository;
import com.Travel.Buddy.repository.TripRecommendationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hotel recommendations. (SRS 2.3 TP-08, sections 4.1 and 6.1)
 *
 * <p>Two rules shape this class more than any scoring detail:
 *
 * <ol>
 *   <li><strong>Section 12: "Never recommend a hotel as available
 *       if its selected dates are unavailable."</strong> A stay is
 *       only sellable if <em>every</em> night has inventory, and
 *       availability must be proven for the whole range, not
 *       sampled.</li>
 *   <li><strong>Section 4.1: recommendations must be
 *       explainable.</strong> Every result carries a reason
 *       string built from the signals that actually drove its
 *       score, and rejected candidates are stored with their
 *       reason too. An engine that cannot say why is a black box,
 *       and a black box is worse than no recommendation.</li>
 * </ol>
 *
 * <p>Nothing here books anything. Recommendations populate
 * trip_recommendations; the traveller chooses, which creates a
 * trip_selection, and only checkout reserves inventory.
 */
@Service
public class TripRecommendationService {

    /**
     * How many ranked hotels to keep per city stop. Enough to be
     * a real choice, few enough to stay a decision.
     */
    private static final int MAX_PER_CITY = 5;

    /**
     * Beyond this, "near your selected place" stops being a
     * selling point. Used to turn distance into a 0-1 proximity
     * score, so a hotel 200km away cannot outrank a good one
     * merely by being cheaper.
     */
    /**
     * A property at or above this many published amenities is
     * treated as fully equipped. Twelve is roughly what a
     * traveller would expect to find listed at a mainstream
     * property, so the score saturates there rather than
     * rewarding an encyclopaedic list.
     */
    private static final int AMENITY_TARGET = 12;

    /**
     * A free-cancellation window of this many days or more scores
     * full marks on policy. Two weeks is the point at which a
     * traveller can cancel without needing to think about it.
     */
    private static final int GENEROUS_CANCELLATION_DAYS = 14;

    private static final BigDecimal NEAR_CEILING_KM =
            new BigDecimal("25.00");

    /**
     * Cheapest qualifying nightly rate across the candidate set.
     * Used only to normalise price into a 0-1 score; the absolute
     * figures are what the traveller is shown.
     */
    private final PropertyRepository propertyRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomInventoryRepository inventoryRepository;
    private final ReviewSummaryRepository reviewSummaryRepository;
    private final PropertyAmenityRepository propertyAmenityRepository;
    private final PropertyCancellationTermRepository cancellationRepository;
    private final TripCityRepository tripCityRepository;
    private final TripPlaceRepository tripPlaceRepository;
    private final TripRecommendationRepository recommendationRepository;
    private final TripService tripService;
    private final GeoDistanceService geo;

    public TripRecommendationService(
            PropertyRepository propertyRepository,
            RoomTypeRepository roomTypeRepository,
            RoomInventoryRepository inventoryRepository,
            ReviewSummaryRepository reviewSummaryRepository,
            PropertyAmenityRepository propertyAmenityRepository,
            PropertyCancellationTermRepository cancellationRepository,
            TripCityRepository tripCityRepository,
            TripPlaceRepository tripPlaceRepository,
            TripRecommendationRepository recommendationRepository,
            TripService tripService,
            GeoDistanceService geo
    ) {
        this.propertyRepository = propertyRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.inventoryRepository = inventoryRepository;
        this.reviewSummaryRepository = reviewSummaryRepository;
        this.propertyAmenityRepository = propertyAmenityRepository;
        this.cancellationRepository = cancellationRepository;
        this.tripCityRepository = tripCityRepository;
        this.tripPlaceRepository = tripPlaceRepository;
        this.recommendationRepository = recommendationRepository;
        this.tripService = tripService;
        this.geo = geo;
    }

    /* ============================================================
     * ENTRY POINT
     * ============================================================ */

    /**
     * Recomputes hotel recommendations for every city stop.
     *
     * <p>Replaces the previous set rather than accumulating, so a
     * traveller who changes dates or places sees the current
     * answer instead of a mix of old and new.
     */
    @Transactional
    public List<TripRecommendationResponse> recommendHotels(
            Long userId,
            Long tripId
    ) {
        Trip trip = tripService.requireOwned(userId, tripId);

        List<TripCity> stops = tripCityRepository
                .findByTrip_TripIdOrderBySequenceAsc(tripId);

        if (stops.isEmpty()) {
            throw com.Travel.Buddy.exception
                    .PartnerApplicationException.badRequest(
                    "Choose the cities on your trip before "
                            + "asking for hotel recommendations"
            );
        }

        // Fresh set, so stale reasons never linger.
        recommendationRepository.deleteByTrip_TripId(tripId);

        TravelStyle style = trip.getTravelStyle() == null
                ? TravelStyle.BUDGET
                : trip.getTravelStyle();

        for (TripCity stop : stops) {
            recommendForStop(trip, stop, style);
        }

        return tripService.detailFor(trip).recommendations();
    }

    /* ============================================================
     * PER STOP
     * ============================================================ */

    private void recommendForStop(
            Trip trip,
            TripCity stop,
            TravelStyle style
    ) {
        LocalDate checkIn = stop.getArrivalDate();
        LocalDate checkOut = stop.getDepartureDate();

        /*
         * No dates means no availability can be proven, and
         * section 12 forbids recommending on an unproven date.
         * The traveller is told why rather than shown a list that
         * would collapse at checkout.
         */
        if (checkIn == null || checkOut == null
                || !checkOut.isAfter(checkIn)) {
            return;
        }

        List<TouristPlace> places = placesForStop(trip, stop);
        int guests = trip.getTravelerCount() == null
                ? 2
                : trip.getTravelerCount();

        List<Property> candidates = propertyRepository
                .findByCity_CityIdAndActiveTrueAndVerifiedTrueOrderByNameAsc(
                        stop.getCity().getCityId()
                );

        List<Candidate> scored = new ArrayList<>();
        List<TripRecommendation> rejected = new ArrayList<>();

        for (Property property : candidates) {
            Candidate candidate = evaluate(
                    trip, stop, property, places, checkIn,
                    checkOut, guests
            );

            if (candidate == null) {
                rejected.add(rejection(
                        trip, stop, property, checkIn, checkOut
                ));
            } else {
                scored.add(candidate);
            }
        }

        persistRejections(trip, rejected);

        /*
         * Price can only be scored relative to something. The
         * cheapest qualifying rate in this city is the honest
         * reference: it is the price the traveller could
         * actually get, so a rate twice that is measurably
         * expensive rather than merely a larger number.
         *
         * Scored after collection rather than inside the loop,
         * because the reference is not known until every
         * candidate has been seen.
         */
        BigDecimal floor = scored.stream()
                .map(c -> c.nightlyRate())
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.ONE);

        BigDecimal ceiling = scored.stream()
                .map(c -> c.nightlyRate())
                .max(Comparator.naturalOrder())
                .orElse(floor);

        for (Candidate candidate : scored) {
            double price = priceScore(
                    candidate.nightlyRate(), floor, ceiling
            );
            candidate.score(price, style, places.isEmpty());
        }

        scored.sort(
                Comparator.comparingDouble(
                                (Candidate c) -> c.score())
                        .reversed()
                        .thenComparing(c -> c.property().getName())
        );

        int rank = 1;
        for (Candidate candidate : scored) {
            if (rank > MAX_PER_CITY) {
                break;
            }

            TripRecommendation recommendation =
                    new TripRecommendation(
                            trip, stop,
                            TripRecommendationType.HOTEL,
                            candidate.property.getPropertyId(),
                            candidate.property.getName()
                    );

            recommendation.rank(
                    rank,
                    candidate.totalCost(),
                    trip.getCurrency(),
                    candidate.distanceKm(),
                    candidate.reason(),
                    candidate.room()
            );

            recommendationRepository.save(recommendation);
            rank++;
        }
    }

    private List<TouristPlace> placesForStop(
            Trip trip,
            TripCity stop
    ) {
        return tripPlaceRepository
                .findByTripCity_TripCityId(stop.getTripCityId())
                .stream()
                .map(TripPlace::getPlace)
                .toList();
    }

    /* ============================================================
     * EVALUATION
     * ============================================================ */

    /**
     * @return a scored candidate, or null when the property must
     * not be recommended at all
     */
    private Candidate evaluate(
            Trip trip,
            TripCity stop,
            Property property,
            List<TouristPlace> places,
            LocalDate checkIn,
            LocalDate checkOut,
            int guests
    ) {
        if (property.getStatus() == null
                || !property.getStatus().isLive()) {
            return null;
        }

        List<RoomType> rooms = roomTypeRepository
                .findByProperty_PropertyIdAndActiveTrueOrderByBasePriceAsc(
                        property.getPropertyId()
                );

        if (rooms.isEmpty()) {
            return null;
        }

        int nights = (int) ChronoUnit.DAYS.between(
                checkIn, checkOut
        );

        BigDecimal cheapestAvailable = null;
        RoomType bestRoom = null;
        int roomsNeeded = trip.roomsRequired();

        for (RoomType room : rooms) {
            if (room.getMaxOccupancy() != null
                    && room.getMaxOccupancy() < guests) {
                continue;
            }

            if (!hasAvailabilityEveryNight(
                    room.getRoomTypeId(), checkIn, checkOut)) {
                continue;
            }

            if (room.getBasePrice() == null) {
                continue;
            }

            if (cheapestAvailable == null
                    || room.getBasePrice()
                    .compareTo(cheapestAvailable) < 0) {
                cheapestAvailable = room.getBasePrice();
                bestRoom = room;
            }
        }

        if (bestRoom == null) {
            return null;
        }

        BigDecimal totalCost = cheapestAvailable
                .multiply(BigDecimal.valueOf(
                        (long) nights * roomsNeeded))
                .setScale(2, RoundingMode.HALF_UP);

        /*
         * Over budget is a warning, never a filter. Section 4.5
         * says warn rather than block, and a premium traveller
         * may knowingly exceed a figure they set themselves.
         */
        BigDecimal distanceKm = nearestDistance(property, places);
        ReviewSummary summary = reviewSummaryRepository
                .findByTargetTypeAndTargetId(
                        ReviewTargetType.HOTEL,
                        property.getPropertyId()
                )
                .orElse(null);

        double proximity = proximityScore(distanceKm);
        double quality = qualityScore(summary);

        /*
         * Section 6.1 factors 7 and 8, resolved before the reason
         * is written so the explanation can state them.
         */
        double amenities = amenityScore(property.getPropertyId());
        double cancellation = cancellationScore(
                property.getPropertyId()
        );

        int amenityCount = propertyAmenityRepository
                .findByIdPropertyIdOrderByAmenityCategoryAscAmenityLabelAsc(
                        property.getPropertyId()
                ).size();

        List<com.Travel.Buddy.entity.PropertyCancellationTerm> terms =
                cancellationRepository
                        .findByProperty_PropertyIdOrderByDaysBeforeCheckInDesc(
                                property.getPropertyId()
                        );

        Integer mostGenerousCancellation = terms.isEmpty()
                ? null
                : terms.get(0).getDaysBeforeCheckIn();

        String reason = reasonFor(
                distanceKm, nights, bestRoom, guests,
                roomsNeeded, totalCost, summary, trip,
                amenityCount, mostGenerousCancellation
        );

        /*
         * Price is deliberately not scored here. It can only be
         * normalised against the cheapest qualifying rate in this
         * city, which is not known until every candidate has been
         * evaluated, so Candidate.score() applies it afterwards.
         */
        return new Candidate(
                property, bestRoom, cheapestAvailable,
                totalCost, distanceKm,
                proximity, quality, amenities, cancellation, reason
        );
    }

    /**
     * Section 12: availability must hold for the complete stay.
     *
     * <p>Every night, and the row count must match the nights
     * requested. A gap in the inventory table means a night nobody
     * has priced, which is not the same as an available night.
     */
    private boolean hasAvailabilityEveryNight(
            Long roomTypeId,
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        List<RoomInventoryDaily> nights = inventoryRepository
                .findByRoomTypeAndDateRange(
                        roomTypeId,
                        checkIn,
                        checkOut.minusDays(1)
                );

        long expected = ChronoUnit.DAYS.between(
                checkIn, checkOut
        );

        if (nights.size() != expected) {
            return false;
        }

        return nights.stream().allMatch(
                day -> day.getAvailableInventory() != null
                        && day.getAvailableInventory() > 0
        );
    }

    /* ============================================================
     * SCORING
     * ============================================================ */

    /**
     * Distance to the closest selected place, or to the city
     * centre when the traveller picked no places.
     *
     * <p>Using the nearest rather than the average is the
     * honest reading of "near your selected place": a hotel
     * beside one attraction is near the trip.
     */
    private BigDecimal nearestDistance(
            Property property,
            List<TouristPlace> places
    ) {
        if (property.getLatitude() == null
                || property.getLongitude() == null) {
            return null;
        }

        List<double[]> points = new ArrayList<>();

        for (TouristPlace place : places) {
            if (place.getLatitude() != null
                    && place.getLongitude() != null) {
                points.add(new double[]{
                        place.getLatitude().doubleValue(),
                        place.getLongitude().doubleValue()
                });
            }
        }

        if (points.isEmpty() && property.getCity() != null
                && property.getCity().getLatitude() != null) {
            /*
             * No places selected, so proximity to the city centre
             * is the only meaningful claim. Recorded as null
             * distance in the output because it is not a
             * place-distance.
             */
            return null;
        }

        return geo.nearestDistanceKm(
                property.getLatitude().doubleValue(),
                property.getLongitude().doubleValue(),
                points
        );
    }

    private double proximityScore(BigDecimal distanceKm) {
        if (distanceKm == null) {
            /*
             * No claim to make. A neutral score rather than zero,
             * so a property with no coordinates is not pushed to
             * the bottom for a reason the traveller cannot see.
             */
            return 0.5;
        }

        if (distanceKm.compareTo(NEAR_CEILING_KM) >= 0) {
            return 0.0;
        }

        return 1.0 - distanceKm.doubleValue()
                / NEAR_CEILING_KM.doubleValue();
    }

    /**
     * Price scored against the real candidate set.
     *
     * <p>Normalised between the cheapest and dearest qualifying
     * rate actually available in this city. A fixed ceiling would
     * be arbitrary: a 1,500/night room is unremarkable in Jaipur
     * and extravagant in Puri, and only the local market makes
     * "expensive" mean anything.
     *
     * <p>When every candidate prices identically all score 1.0
     * rather than dividing by zero, which would otherwise hand
     * the whole set NaN and make the sort meaningless.
     */
    private double priceScore(
            BigDecimal nightly,
            BigDecimal floor,
            BigDecimal ceiling
    ) {
        if (nightly == null || nightly.signum() <= 0) {
            return 0.0;
        }

        if (ceiling.compareTo(floor) <= 0) {
            return 1.0;
        }

        double span = ceiling.doubleValue() - floor.doubleValue();
        double position = nightly.doubleValue() - floor.doubleValue();

        return 1.0 - (position / span);
    }

    private double qualityScore(ReviewSummary summary) {
        if (summary == null
                || summary.getAverageRating() == null) {
            /*
             * Unrated is not bad. Awarding a zero would let any
             * rated property outrank a perfectly good new one,
             * which quietly punishes new listings.
             */
            return 0.5;
        }

        double rating = summary.getAverageRating()
                .doubleValue();

        /*
         * Confidence weighting: one five-star review is weaker
         * evidence than fifty. This is what stops a brand new
         * listing from outranking an established one on a
         * single review.
         */
        int count = summary.getReviewCount() == null
                ? 0
                : summary.getReviewCount();

        double confidence = Math.min(
                1.0,
                Math.log10(count + 1) / 2.0
        );

        return (rating / 5.0) * (0.6 + 0.4 * confidence);
    }

    /**
     * Section 6.1 factor 7: amenities and room facilities.
     *
     * <p>Scored on breadth, not on any particular amenity. Weighting
     * a specific list would make the engine argue for a pool at a
     * heritage guesthouse, which is a worse recommendation than
     * making no claim at all.
     *
     * <p>A property that has published no amenities is treated as
     * neutral rather than as poor, for the same reason an unrated
     * property is not scored zero: silence is not evidence.
     */
    private double amenityScore(Long propertyId) {
        int count = propertyAmenityRepository
                .findByIdPropertyIdOrderByAmenityCategoryAscAmenityLabelAsc(
                        propertyId
                ).size();

        if (count == 0) {
            return 0.5;
        }

        return Math.min(1.0, count / (double) AMENITY_TARGET);
    }

    /**
     * Section 6.1 factor 8: cancellation policy.
     *
     * <p>Driven by the published free-cancellation window, because
     * that is the number a traveller actually compares. A property
     * with no terms is non-refundable by definition and scores zero
     * rather than being treated as unknown: assuming a generous
     * policy for a property that never published one would
     * misrepresent it.
     */
    private double cancellationScore(Long propertyId) {
        List<com.Travel.Buddy.entity.PropertyCancellationTerm> terms =
                cancellationRepository
                        .findByProperty_PropertyIdOrderByDaysBeforeCheckInDesc(
                                propertyId
                        );

        if (terms.isEmpty()) {
            return 0.0;
        }

        com.Travel.Buddy.entity.PropertyCancellationTerm best =
                terms.get(0);

        double window = Math.min(
                1.0,
                best.getDaysBeforeCheckIn()
                        / (double) GENEROUS_CANCELLATION_DAYS
        );

        double refund = best.getRefundPercent()
                .divide(
                        java.math.BigDecimal.valueOf(100),
                        4,
                        java.math.RoundingMode.HALF_UP
                )
                .doubleValue();

        return (window * 0.5) + (refund * 0.5);
    }

    /* ============================================================
     * EXPLANATIONS
     * ============================================================ */

    private String reasonFor(
            BigDecimal distanceKm,
            int nights,
            RoomType room,
            int guests,
            int roomsNeeded,
            BigDecimal totalCost,
            ReviewSummary summary,
            Trip trip,
            int amenityCount,
            Integer cancellationDays
    ) {
        List<String> reasons = new ArrayList<>();

        TravelStyle style = trip.getTravelStyle() == null
                ? TravelStyle.BUDGET
                : trip.getTravelStyle();

        if (distanceKm != null) {
            reasons.add(geo.describeDistance(distanceKm));
        }

        reasons.add("Available for all " + nights
                + (nights == 1 ? " night" : " nights"));

        reasons.add(room.getCategoryName() + " fits " + guests
                + (roomsNeeded > 1
                ? ", " + roomsNeeded + " rooms needed"
                : ""));

        if (summary != null
                && summary.getAverageRating() != null
                && summary.getReviewCount() != null
                && summary.getReviewCount() > 0) {
            reasons.add("Rated "
                    + summary.getAverageRating().stripTrailingZeros()
                    .toPlainString()
                    + " from " + summary.getReviewCount()
                    + (summary.getReviewCount() == 1
                    ? " review"
                    : " reviews"));
        }

        if (style == TravelStyle.PREMIUM) {
            reasons.add("Matches your Premium preference");
        }

        /*
         * Section 6.1 factors 7 and 8. Stated only when they are
         * actually good, because a reason that lists "1 amenity"
         * as though it were an advantage is worse than no
         * mention. A property with published terms earns the
         * line; one with none is non-refundable and is not
         * dressed up as anything else.
         */
        if (amenityCount >= AMENITY_TARGET) {
            reasons.add(amenityCount + " amenities listed");
        }

        if (cancellationDays != null
                && cancellationDays > 0) {
            reasons.add("Free cancellation up to "
                    + cancellationDays + " days before");
        }

        if (trip.getBudgetAmount() != null) {
            if (totalCost.compareTo(trip.getBudgetAmount()) <= 0) {
                reasons.add("Within your budget");
            } else {
                reasons.add("Above your budget of "
                        + trip.getBudgetAmount().stripTrailingZeros()
                        .toPlainString());
            }
        }

        return String.join(". ", reasons);
    }

    /**
     * Records why a candidate was dropped.
     *
     * <p>Kept, not discarded. Section 4.1 is about transparency,
     * and a traveller who was shown a city full of hotels and is
     * offered none deserves to know whether that was a data gap
     * or a genuine answer.
     */
    private TripRecommendation rejection(
            Trip trip,
            TripCity stop,
            Property property,
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        TripRecommendation recommendation =
                new TripRecommendation(
                        trip, stop,
                        TripRecommendationType.HOTEL,
                        property.getPropertyId(),
                        property.getName()
                );

        recommendation.rejectAs(rejectionReason(
                property, checkIn, checkOut
        ));

        return recommendation;
    }

    private String rejectionReason(
            Property property,
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        if (property.getStatus() == null
                || !property.getStatus().isLive()) {
            return "Not currently bookable";
        }

        List<RoomType> rooms = roomTypeRepository
                .findByProperty_PropertyIdAndActiveTrueOrderByBasePriceAsc(
                        property.getPropertyId()
                );

        if (rooms.isEmpty()) {
            return "No rooms listed";
        }

        boolean anyAvailable = rooms.stream()
                .anyMatch(room ->
                        hasAvailabilityEveryNight(
                                room.getRoomTypeId(),
                                checkIn, checkOut
                        ));

        if (!anyAvailable) {
            return "No rooms available for "
                    + checkIn + " to " + checkOut;
        }

        return "No room fits the requested occupancy";
    }

    private void persistRejections(
            Trip trip,
            List<TripRecommendation> rejected
    ) {
        for (TripRecommendation recommendation : rejected) {
            recommendationRepository.save(recommendation);
        }
    }

    /* ============================================================
     * INTERNALS
     * ============================================================ */

    /**
     * A surviving candidate, scored after the whole set has been
     * seen.
     *
     * <p>A class rather than a record because price can only be
     * normalised once every candidate is known, so the final
     * score is assigned after construction.
     */
    private static final class Candidate {

        private final Property property;

        /*
         * The room this candidate was scored and priced against.
         * It has to reach the client, because a ranked
         * recommendation is meant to be added to the cart, and the
         * cart prices a hotel from its room type.
         */
        private final RoomType room;

        private final BigDecimal nightlyRate;
        private final BigDecimal totalCost;
        private final BigDecimal distanceKm;
        private final double proximityScore;
        private final double qualityScore;
        private final double amenityScore;
        private final double cancellationScore;
        private final String reason;

        private double score;

        private Candidate(
                Property property,
                RoomType room,
                BigDecimal nightlyRate,
                BigDecimal totalCost,
                BigDecimal distanceKm,
                double proximityScore,
                double qualityScore,
                double amenityScore,
                double cancellationScore,
                String reason
        ) {
            this.property = property;
            this.room = room;
            this.nightlyRate = nightlyRate;
            this.totalCost = totalCost;
            this.distanceKm = distanceKm;
            this.proximityScore = proximityScore;
            this.qualityScore = qualityScore;
            this.amenityScore = amenityScore;
            this.cancellationScore = cancellationScore;
            this.reason = reason;
        }

        /**
         * Combines the signals using the traveller's travel style.
         *
         * <p>Proximity is dropped entirely when no places were
         * selected, because there is then no proximity claim to
         * weigh. Including a neutral 0.5 for an unmeasurable
         * factor would let it dilute a real price signal.
         */
        private void score(
                double priceScore,
                TravelStyle style,
                boolean noPlaces
        ) {
            double priceWeight = style.priceWeight();
            double qualityWeight = style.qualityWeight();
            double proximityWeight = noPlaces ? 0.0 : 0.45;

            /*
             * Amenities and cancellation terms are section 6.1
             * factors 7 and 8. They are weighted lightly, because
             * a rich amenity list and a forgiving cancellation
             * window both make for a worse stay if the room is
             * unavailable or the location is wrong.
             */
            double amenityWeight = 0.10;
            double cancellationWeight = 0.10;

            double total = proximityWeight + priceWeight
                    + qualityWeight + amenityWeight
                    + cancellationWeight;

            this.score = total <= 0
                    ? 0
                    : (proximityScore * proximityWeight
                    + priceScore * priceWeight
                    + qualityScore * qualityWeight
                    + amenityScore * amenityWeight
                    + cancellationScore * cancellationWeight)
                    / total;
        }

        private Property property() {
            return property;
        }

        private RoomType room() {
            return room;
        }

        private BigDecimal nightlyRate() {
            return nightlyRate;
        }

        private BigDecimal totalCost() {
            return totalCost;
        }

        private BigDecimal distanceKm() {
            return distanceKm;
        }

        private String reason() {
            return reason;
        }

        private double score() {
            return score;
        }
    }
}