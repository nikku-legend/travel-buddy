package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.entity.Booking;
import com.Travel.Buddy.entity.BookingStatus;
import com.Travel.Buddy.entity.Trip;
import com.Travel.Buddy.entity.TripCity;
import com.Travel.Buddy.entity.TripMilestone;
import com.Travel.Buddy.entity.TripMilestoneType;
import com.Travel.Buddy.entity.TripSelection;
import com.Travel.Buddy.entity.TripSelectionStatus;
import com.Travel.Buddy.repository.TripMilestoneRepository;
import com.Travel.Buddy.repository.TripSelectionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Drives the treasure map. (SRS 2.2 TP-11)
 *
 * <p>The map is not decoration. A checkpoint is marked reached only
 * when something real happened, so a traveller never sees progress
 * the platform invented to make a demo look finished.
 *
 * <p>{@code progressPercent} is a checkpoint's POSITION on the map,
 * not a measure of how much is done. It is what
 * {@code findByTrip_TripIdOrderByProgressPercentAsc} orders on, so
 * the map renders in journey order. Completion is the separate
 * {@code completed} flag.
 */
@Service
@Transactional
public class TripMilestoneService {

    private static final int START = 0;
    private static final int FINISH = 100;

    private final TripMilestoneRepository milestoneRepository;
    private final TripSelectionRepository selectionRepository;

    public TripMilestoneService(
            TripMilestoneRepository milestoneRepository,
            TripSelectionRepository selectionRepository
    ) {
        this.milestoneRepository = milestoneRepository;
        this.selectionRepository = selectionRepository;
    }

    /* ============================================================
     SEEDING
     ============================================================ */

    /**
     * Adds the arrival and departure checkpoint for every stop.
     *
     * <p>Spread evenly between the start and finish checkpoints so
     * the map has intermediate pins at all. Two checkpoints and no
     * middle is a progress bar, not a journey.
     */
    public void seedForRoute(
            Trip trip,
            List<TripCity> cities
    ) {
        if (cities == null || cities.isEmpty()) {
            return;
        }

        /*
         * Span runs 1..99 rather than 0..100 so a city checkpoint
         * can never land on the same position as the trip start
         * or finish, which would make the order ambiguous.
         */
        final int span = FINISH - START - 2;
        final int perCity = Math.max(1, span / cities.size());

        for (int index = 0; index < cities.size(); index++) {
            TripCity city = cities.get(index);
            int base = START + 1 + (index * perCity);

            milestoneRepository.save(
                    new TripMilestone(
                            trip, city, TripMilestoneType.CITY_ARRIVED,
                            clamp(base + (perCity / 3)),
                            "Arrive in " + cityNameOf(city)
                    )
            );

            milestoneRepository.save(
                    new TripMilestone(
                            trip, city, TripMilestoneType.CITY_DEPARTED,
                            clamp(base + ((perCity * 2) / 3)),
                            "Leave " + cityNameOf(city)
                    )
            );
        }
    }

    /**
     * Removes the per-city checkpoints before a route is replaced.
     *
     * <p>The route is replaced wholesale rather than merged, so
     * without this the old stops' checkpoints would outlive the
     * stops themselves, and the unique constraint on
     * (trip, type, city) would then refuse the new route's own
     * checkpoints. Checkpoints that are not per city are left
     * alone, so the review and finish pins survive.
     */
    public void clearRouteCheckpoints(Long tripId) {
        milestoneRepository
                .deleteByTrip_TripIdAndTripCityIsNotNull(tripId);
    }

    /**
     * Adds the arrival and departure checkpoints for a stop that
     * has an actual stay booked in it.
     *
     * <p>Separate from the plain arrival and departure pins
     * because a city the traveller merely passes through is not
     * one they checked in to. Only a stop with a selected room
     * gets the stronger pair.
     *
     * <p>Placed a hair either side of the plain pair so the map
     * reads in the order things actually happen: arrive, check
     * in, check out, leave.
     */
    public void seedStayCheckpoints(
            Trip trip,
            TripCity city
    ) {
        if (city == null
                || milestoneRepository
                        .existsByTrip_TripIdAndMilestoneTypeAndTripCity_TripCityId(
                                trip.getTripId(),
                                TripMilestoneType.CHECKED_IN,
                                city.getTripCityId())) {
            return;
        }

        int anchor = positionOf(
                trip, TripMilestoneType.CITY_ARRIVED, city
        );

        milestoneRepository.save(
                new TripMilestone(
                        trip, city, TripMilestoneType.CHECKED_IN,
                        clamp(anchor + 1),
                        "Check in to " + cityNameOf(city)
                )
        );

        milestoneRepository.save(
                new TripMilestone(
                        trip, city, TripMilestoneType.CHECKED_OUT,
                        clamp(anchor + 2),
                        "Check out of " + cityNameOf(city)
                )
        );
    }

    private int positionOf(
            Trip trip,
            TripMilestoneType type,
            TripCity city
    ) {
        return find(trip, type, city)
                .map(TripMilestone::getProgressPercent)
                .orElse(START + 1);
    }

    private String cityNameOf(TripCity city) {
        if (city.getCity() != null
                && city.getCity().getName() != null) {
            return city.getCity().getName();
        }

        /*
         * No usable name, so the label stays generic. A checkpoint
         * called "Arrive in City #41" is worse on a map than one
         * that simply says "Arrive".
         */
        return "the next stop";
    }

    private int clamp(int value) {
        return Math.max(START + 1, Math.min(FINISH - 1, value));
    }

    /* ============================================================
     COMPLETION
     ============================================================ */

    /**
     * Records that a real event reached this checkpoint.
     *
     * <p>Silently does nothing when the checkpoint does not exist,
     * because a caller reporting "the traveller arrived" for a
     * trip whose route was never set is not an error worth
     * failing a webhook over.
     */
    public boolean complete(
            Trip trip,
            TripMilestoneType type,
            TripCity city
    ) {
        TripMilestone milestone = find(trip, type, city)
                .orElse(null);

        if (milestone == null) {
            return false;
        }

        boolean changed = !milestone.isCompleted();
        milestone.markComplete();
        milestoneRepository.save(milestone);

        return changed;
    }

    /**
     * Convenience for the checkpoints that are not per city.
     */
    public boolean complete(Trip trip, TripMilestoneType type) {
        return complete(trip, type, null);
    }

    /**
     * Ticks the closing pin once the trip is genuinely over.
     *
     * <p>Two conditions, and both are required.
     *
     * <p><b>The end date has passed.</b> Without this a traveller who
     * abandons a trip would see it marked complete on the day they
     * cancelled, which is a claim about a journey nobody took.
     *
     * <p><b>Every booked service has reached a terminal state.</b>
     * This is the half that matters. The date alone is not evidence:
     * a partner who checks a guest out a week early would otherwise
     * close the whole trip, and the map would record a finished
     * journey containing a stay that never happened. Cancelled counts
     * as terminal on purpose, because a traveller who legitimately
     * drops one leg and takes the rest has still finished the trip.
     *
     * <p>Called on read rather than by a scheduler, so the map
     * cannot show progress a background job has not reached yet. That
     * makes idempotence the whole safety property, and
     * {@code complete} already is: a reached pin keeps its original
     * timestamp.
     */
    public boolean completeTripIfFinished(Trip trip) {
        if (trip.getEndDate() == null
                || trip.getEndDate().isAfter(LocalDate.now())) {

            return false;
        }

        List<TripSelection> booked = selectionRepository
                .findByTrip_TripIdOrderBySelectionIdAsc(
                        trip.getTripId())
                .stream()
                .filter(selection -> selection.getStatus()
                        == TripSelectionStatus.BOOKED)
                .toList();

        /*
         * Nothing was ever booked, so nothing can be said to have
         * finished. A trip the traveller planned and then abandoned
         * is not a completed trip.
         */
        if (booked.isEmpty()) {
            return false;
        }

        boolean allSettled = booked.stream()
                .allMatch(selection -> {
                    Booking booking = selection.getBooking();

                    return booking != null
                            && (booking.getBookingStatus()
                            == BookingStatus.COMPLETED
                            || booking.getBookingStatus()
                            == BookingStatus.CANCELLED);
                });

        if (!allSettled) {
            return false;
        }

        return complete(trip, TripMilestoneType.TRIP_COMPLETED);
    }

    private java.util.Optional<TripMilestone> find(
            Trip trip,
            TripMilestoneType type,
            TripCity city
    ) {
        if (city == null) {
            return milestoneRepository
                    .findByTrip_TripIdAndMilestoneTypeAndTripCityIsNull(
                            trip.getTripId(), type
                    );
        }

        return milestoneRepository
                .findByTrip_TripIdAndMilestoneTypeAndTripCity_TripCityId(
                        trip.getTripId(), type,
                        city.getTripCityId()
                );
    }
}