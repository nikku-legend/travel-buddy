package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.dto.trip.TripReviewCardResponse;
import com.Travel.Buddy.dto.trip.TripReviewCardResponse.TripReviewCentreResponse;
import com.Travel.Buddy.dto.trip.TripReviewCardResponse.TripReviewSummaryResponse;
import com.Travel.Buddy.entity.Booking;
import com.Travel.Buddy.entity.BookingStatus;
import com.Travel.Buddy.entity.CabRide;
import com.Travel.Buddy.entity.GuideReservation;
import com.Travel.Buddy.entity.Review;
import com.Travel.Buddy.entity.ReviewStatus;
import com.Travel.Buddy.entity.ReviewTargetType;
import com.Travel.Buddy.entity.ReviewUnlock;
import com.Travel.Buddy.entity.ReviewUnlockStatus;
import com.Travel.Buddy.entity.RideStatus;
import com.Travel.Buddy.entity.Trip;
import com.Travel.Buddy.entity.TripMilestoneType;
import com.Travel.Buddy.entity.TripSelection;
import com.Travel.Buddy.entity.TripSelectionStatus;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.CabRepository;
import com.Travel.Buddy.repository.CabRideRepository;
import com.Travel.Buddy.repository.GuideRepository;
import com.Travel.Buddy.repository.GuideReservationRepository;
import com.Travel.Buddy.repository.PropertyRepository;
import com.Travel.Buddy.repository.ReviewRepository;
import com.Travel.Buddy.repository.ReviewUnlockRepository;
import com.Travel.Buddy.repository.TouristPlaceRepository;
import com.Travel.Buddy.repository.TripRepository;
import com.Travel.Buddy.repository.TripSelectionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The post-trip Review Center. (SRS 2.3 TP-12, sections 8, 10, 11)
 *
 * <p>Section 10 is the specification this implements: "trip reaches
 * end date and required service completion states are recorded; the
 * system evaluates which bookings are eligible for review; Review
 * Center becomes available; each eligible hotel/guide/transport
 * service receives a review card."
 *
 * <h2>Why this existed as a gap</h2>
 *
 * <p>ReviewService already enforced entitlement, but only for a
 * single target the client had to name:
 * {@code GET /reviews/eligibility?targetType=&targetId=}. That answer
 * is only useful if the client already knows which services to ask
 * about, and nothing in the system could tell it. TripStatus had a
 * REVIEW_OPEN state and TripMilestoneType had REVIEW_OPENED, and
 * neither was ever reached.
 *
 * <h2>Eligibility is derived from completion, not from the date</h2>
 *
 * <p>Section 11 says "eligibility is booking-based". A card opens when
 * <em>that service</em> completes, so a traveller mid-itinerary is
 * offered the hotel they have already stayed in while their onward
 * cab stays locked. Using the trip's end date instead would unlock
 * everything at once, including services not yet rendered, and would
 * let a partner check a guest out early and still have the guest
 * vouch for a stay that never happened.
 *
 * <p>Cab is the deliberate exception, mirroring ReviewService: a ride
 * is not a booking, so a cab card follows its ride's status.
 */
@Service
public class TripReviewService {

    private final TripRepository tripRepository;
    private final TripSelectionRepository selectionRepository;
    private final ReviewUnlockRepository unlockRepository;
    private final ReviewRepository reviewRepository;
    private final GuideReservationRepository guideReservationRepository;
    private final CabRideRepository cabRideRepository;
    private final PropertyRepository propertyRepository;
    private final GuideRepository guideRepository;
    private final CabRepository cabRepository;
    private final TouristPlaceRepository touristPlaceRepository;
    private final TripMilestoneService milestoneService;

    public TripReviewService(
            TripRepository tripRepository,
            TripSelectionRepository selectionRepository,
            ReviewUnlockRepository unlockRepository,
            ReviewRepository reviewRepository,
            GuideReservationRepository guideReservationRepository,
            CabRideRepository cabRideRepository,
            PropertyRepository propertyRepository,
            GuideRepository guideRepository,
            CabRepository cabRepository,
            TouristPlaceRepository touristPlaceRepository,
            TripMilestoneService milestoneService
    ) {
        this.tripRepository = tripRepository;
        this.selectionRepository = selectionRepository;
        this.unlockRepository = unlockRepository;
        this.reviewRepository = reviewRepository;
        this.guideReservationRepository = guideReservationRepository;
        this.cabRideRepository = cabRideRepository;
        this.propertyRepository = propertyRepository;
        this.guideRepository = guideRepository;
        this.cabRepository = cabRepository;
        this.touristPlaceRepository = touristPlaceRepository;
        this.milestoneService = milestoneService;
    }
/* ============================================================
     * READING
     * ============================================================ */

    /**
     * The Review Center for one trip.
     *
     * <p>Derives the window first, then reads it. Deriving on read is
     * what makes this correct without a scheduled job: a service can
     * complete at any moment, and the traveller's next visit is
     * exactly when the card should appear. Re-deriving is idempotent,
     * so this is safe to call on every load.
     *
     * <p>Ownership is enforced before anything is written, so one
     * traveller cannot force unlock rows onto another's trip by
     * asking for its Review Center.
     */
    @Transactional
    public TripReviewCentreResponse reviewCentre(
            Long userId,
            Long tripId
    ) {
        Trip trip = requireOwnedTrip(tripId, userId);

        derive(trip);

        List<ReviewUnlock> unlocks = unlockRepository
                .findByTrip_TripIdOrderByUnlockIdAsc(tripId);

        List<TripReviewCardResponse> cards = unlocks.stream()
                .filter(this::isShowable)
                .map(this::toCard)
                .toList();

        return new TripReviewCentreResponse(
                trip.getTripId(),
                trip.getTitle(),
                trip.getStatus().name(),
                !cards.isEmpty(),
                summarise(unlocks),
                cards
        );
    }

    /**
     * How many services are still waiting for a review.
     *
     * <p>Drives the badge on My Trips, so it deliberately excludes
     * locked cards: a traveller should be nudged about the stay they
     * have finished, not the one they have not reached yet.
     */
    @Transactional(readOnly = true)
    public int pendingCount(Long userId, Long tripId) {
        requireOwnedTrip(tripId, userId);

        return (int) unlockRepository.countByTrip_TripIdAndStatusIn(
                tripId,
                List.of(ReviewUnlockStatus.ELIGIBLE)
        );
    }

/* ============================================================
     * DERIVATION
     * ============================================================ */

    /**
     * Creates or advances a card per booked service.
     *
     * <p>Idempotent in two independent ways. The UNIQUE constraint on
     * (trip_id, target_type, target_id) means a repeat visit cannot
     * produce a second card, and a card already past LOCKED is never
     * rewound, so re-deriving cannot undo a review that has since
     * been submitted or published.
     */
    private void derive(Trip trip) {
        boolean opened = false;

        for (TripSelection selection
                : selectionRepository
                        .findByTrip_TripIdOrderBySelectionIdAsc(
                                trip.getTripId())) {

            if (!isBookable(selection)) {
                continue;
            }

            Candidate candidate = resolve(selection);

            if (candidate == null) {
                continue;
            }

            ReviewUnlock unlock = unlockRepository
                    .findByTrip_TripIdAndTargetTypeAndTargetId(
                            trip.getTripId(),
                            candidate.type(),
                            candidate.targetId()
                    )
                    .orElseGet(() -> new ReviewUnlock(
                            trip,
                            candidate.type(),
                            candidate.targetId(),
                            candidate.booking(),
                            candidate.reason()
                    ));

            unlock.attachStay(
                    candidate.selection().getCheckIn(),
                    candidate.selection().getCheckOut(),
                    cityNameOf(candidate.selection())
            );

            if (unlock.getStatus() == ReviewUnlockStatus.LOCKED
                    && candidate.eligible()) {

                unlock.markEligible();

                /*
                 * Only a card that has actually opened counts. A
                 * LOCKED card exists for every booked service, so
                 * ticking the pin on those would light up "Write a
                 * review" for a traveller with nothing to review.
                 */
                opened = true;
            }

            linkExistingReview(unlock, trip);

            unlockRepository.save(unlock);
        }

        if (opened) {
            milestoneService.complete(
                    trip, TripMilestoneType.REVIEW_OPENED
            );
        }
    }

    /**
     * Whether a selection can ever produce a review card.
     *
     * <p>Only a BOOKED selection has a real service behind it. An
     * activity has no listing to review, and a selection the
     * traveller removed or checkout marked unavailable was never
     * rendered, so neither belongs in a Review Center.
     */
    private boolean isBookable(TripSelection selection) {
        return selection.getStatus() == TripSelectionStatus.BOOKED
                && selection.getBooking() != null;
    }

    /**
     * The property a stay selection points at.
     *
     * <p>Null when the room type or its property is gone, which is
     * why the review cannot name a subject.
     */
    private Candidate hotelCandidate(
            TripSelection selection,
            Booking booking
    ) {
        if (selection.getRoomType() == null
                || selection.getRoomType()
                .getProperty() == null) {

            return null;
        }

        Long propertyId = selection.getRoomType()
                .getProperty()
                .getPropertyId();

        boolean done = booking.getBookingStatus()
                == BookingStatus.COMPLETED;

        return new Candidate(
                ReviewTargetType.HOTEL,
                propertyId,
                booking,
                done
                        ? "You completed your stay here"
                        : "Available to review once you check out",
                done,
                selection
        );
    }

    private Candidate guideCandidate(
            TripSelection selection,
            Booking booking
    ) {
        List<GuideReservation> reservations =
                guideReservationRepository
                        .findByBooking_BookingId(
                                booking.getBookingId()
                        );

        if (reservations.isEmpty()) {
            return null;
        }

        GuideReservation reservation = reservations.get(0);

        boolean done = booking.getBookingStatus()
                == BookingStatus.COMPLETED;

        return new Candidate(
                ReviewTargetType.GUIDE,
                reservation.getGuide().getGuideId(),
                booking,
                done
                        ? "Your tour with this guide is complete"
                        : "Available to review after your tour",
                done,
                selection
        );
    }

    /**
     * A cab card follows the ride, not the booking.
     *
     * <p>Matches ReviewService.findEntitlement, which proves cab
     * eligibility from a completed ride. Booking the vehicle is not
     * the service; the journey is.
     */
    private Candidate cabCandidate(
            TripSelection selection,
            Booking booking
    ) {
        CabRide ride = cabRideRepository
                .findByBooking_BookingId(booking.getBookingId())
                .orElse(null);

        boolean done = ride != null
                && ride.getStatus() == RideStatus.COMPLETED;

        return new Candidate(
                ReviewTargetType.CAB,
                selection.getTargetId(),
                booking,
                done
                        ? "Your ride with this cab is complete"
                        : "Available to review once your ride is done",
                done,
                selection
        );
    }

    /**
     * Points a card at a review that already exists.
     *
     * <p>Without this a traveller who reviewed a hotel from its own
     * page, outside the planner, would be offered the same review
     * again by the Review Center. The existing review wins, because
     * ReviewService already refuses a second review of the same
     * target and the card must not contradict it.
     */
    private void linkExistingReview(ReviewUnlock unlock, Trip trip) {
        if (unlock.getReview() != null
                || unlock.getStatus() == ReviewUnlockStatus.LOCKED) {

            return;
        }

        reviewRepository
                .findByUser_UserIdAndTargetTypeAndTargetId(
                        trip.getUser().getUserId(),
                        unlock.getTargetType(),
                        unlock.getTargetId()
                )
                .ifPresent(review -> {
                    switch (review.getStatus()) {
                        case PUBLISHED ->
                                unlock.markPublished(review);
                        case REJECTED ->
                                unlock.reopen();
                        default ->
                                unlock.markSubmitted(review);
                    }
                });
    }

     /**
     * Works out what a selection points at and whether it completed.
     *
     * @return null when the target can no longer be identified, in
     *         which case no card is created. Better a missing card
     *         than a card pointing at an id that resolves to nothing.
     */
    private Candidate resolve(TripSelection selection) {
        Booking booking = selection.getBooking();

        return switch (selection.getSelectionType()) {
            case HOTEL -> hotelCandidate(selection, booking);
            case GUIDE -> guideCandidate(selection, booking);
            case CAB -> cabCandidate(selection, booking);
            case ACTIVITY -> null;
        };
    }

    /* ============================================================
     * PRESENTATION
     * ============================================================ */

    /**
     * Whether a card belongs in the Review Center.
     *
     * <p>A LOCKED card is hidden. Section 10 describes the center as
     * the list of services that became reviewable, so a hotel the
     * traveller has not reached yet is not part of it. It is not
     * "hidden with a lock icon", it is simply absent -- the same
     * distinction ReviewService draws by refusing a review outright
     * rather than accepting one and failing later.
     */
    private boolean isShowable(ReviewUnlock unlock) {
        return unlock.getStatus() != ReviewUnlockStatus.LOCKED;
    }

    private TripReviewCardResponse toCard(ReviewUnlock unlock) {
        Review review = unlock.getReview();

        Booking booking = unlock.getBooking();

        return new TripReviewCardResponse(
                unlock.getUnlockId(),
                unlock.getTargetType(),
                unlock.getTargetId(),
                nameOf(unlock),
                unlock.getStatus(),
                unlock.getStatus().acceptsReview(),
                unlock.getUnlockReason(),
                booking == null ? null : booking.getBookingId(),
                booking == null
                        ? null
                        : booking.getBookingReference(),
                unlock.getCheckIn(),
                unlock.getCheckOut(),
                unlock.getCityName(),
                unlock.getEligibleAt(),
                review == null
                        ? null
                        : review.getCreatedAt(),
                review == null ? null : review.getReviewId(),
                review == null ? null : review.getRating(),
                review == null ? null : review.getTitle(),
                review == null ? null : review.getComment(),
                review == null ? null : review.getStatus(),
                review == null ? null : review.getModerationReason()
        );
    }

    private TripReviewSummaryResponse summarise(
            List<ReviewUnlock> unlocks
    ) {
        int awaiting = 0;
        int submitted = 0;
        int published = 0;
        int flagged = 0;

        for (ReviewUnlock unlock : unlocks) {
            switch (unlock.getStatus()) {
                case ELIGIBLE -> awaiting++;
                case SUBMITTED -> submitted++;
                case PUBLISHED -> published++;
                case FLAGGED -> flagged++;
                case LOCKED -> {
                    /* Not counted: not part of the window. */
                }
            }
        }

        return new TripReviewSummaryResponse(
                awaiting + submitted + published + flagged,
                awaiting,
                submitted,
                published,
                flagged
        );
    }

    /**
     * The display name for a card's subject.
     *
     * <p>Falls back to a generic word rather than failing: a deleted
     * property must not break the whole Review Center, and the card
     * still carries the target id so the UI can link onward.
     */
    private String nameOf(ReviewUnlock unlock) {
        Long id = unlock.getTargetId();

        return switch (unlock.getTargetType()) {
            case HOTEL -> propertyRepository.findById(id)
                    .map(property -> property.getName())
                    .orElse("Property");
            case GUIDE -> guideRepository.findById(id)
                    .map(guide -> guide.getUser()
                            .getFullName())
                    .orElse("Guide");
            case CAB -> cabRepository.findById(id)
                    .map(cab -> cab.getVehicleName())
                    .orElse("Cab");
            case DESTINATION -> touristPlaceRepository.findById(id)
                    .map(place -> place.getName())
                    .orElse("Destination");
        };
    }

    /* ============================================================
     * GUARDS
     * ============================================================ */

    /**
     * Loads a trip the caller owns.
     *
     * <p>Not found and not yours are reported identically, so this
     * endpoint cannot be used to discover which trip ids exist.
     */
    private Trip requireOwnedTrip(Long tripId, Long userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Trip not found"
                        )
                );

        if (!trip.getUser()
                .getUserId()
                .equals(userId)) {

            throw PartnerApplicationException.forbidden(
                    "This trip is not yours"
            );
        }

        return trip;
    }

    /**
     * What one selection points at, and whether it has finished.
     *
     * @param eligible whether the service has completed, which is the
     *                 only thing that opens a card
     */
    private record Candidate(
            ReviewTargetType type,
            Long targetId,
            Booking booking,
            String reason,
            boolean eligible,
            TripSelection selection
    ) {
    }

    /**
     * The city a selection belongs to, for grouping the cards.
     *
     * <p>Null when a selection has no city yet, which is legal: the
     * SRS allows a selection to be added before the city stops are
     * known.
     */
    private String cityNameOf(TripSelection selection) {
        if (selection.getTripCity() == null
                || selection.getTripCity().getCity() == null) {

            return null;
        }

        return selection.getTripCity().getCity().getName();
    }
}

