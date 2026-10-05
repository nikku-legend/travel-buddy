package com.Travel.Buddy.service.guide;

import com.Travel.Buddy.dto.guide.GuideProfileRequest;
import com.Travel.Buddy.dto.guide.GuideReservationResponse;
import com.Travel.Buddy.dto.guide.GuideSummaryResponse;
import com.Travel.Buddy.entity.*;
import com.Travel.Buddy.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GuideService {

    /**
     * The only status moves a guide may report on a tour. (FR-16, FR-25)
     *
     * <p>The same rule {@code CabService} applies to rides, and for
     * the same reason: without it a tour could be walked backwards,
     * so a completed tour could be re-opened and marked complete
     * again.
     *
     * <p>Terminal states are absent on purpose. Once a tour is
     * finished or cancelled it is a historical record, and a guide
     * editing it would put the booking ledger and the review window
     * out of agreement with each other.
     */
    private static final Map<GuideReservationStatus, Set<GuideReservationStatus>>
            ALLOWED_TRANSITIONS =
            Map.of(
                    GuideReservationStatus.CONFIRMED,
                    Set.of(GuideReservationStatus.IN_PROGRESS,
                            GuideReservationStatus.CANCELLED),
                    GuideReservationStatus.IN_PROGRESS,
                    Set.of(GuideReservationStatus.COMPLETED,
                            GuideReservationStatus.CANCELLED)
            );

    private final GuideRepository guideRepository;
    private final GuideAvailabilityRepository guideAvailabilityRepository;
    private final GuideReservationRepository guideReservationRepository;
    private final StateRepository stateRepository;
    private final ReviewSummaryRepository reviewSummaryRepository;
    private final BookingRepository bookingRepository;
    private final TripSelectionRepository selectionRepository;
    private final com.Travel.Buddy.service.trip.TripMilestoneService
            milestoneService;

    public GuideService(
            GuideRepository guideRepository,
            GuideAvailabilityRepository guideAvailabilityRepository,
            GuideReservationRepository guideReservationRepository,
            StateRepository stateRepository,
            ReviewSummaryRepository reviewSummaryRepository,
            BookingRepository bookingRepository,
            TripSelectionRepository selectionRepository,
            com.Travel.Buddy.service.trip.TripMilestoneService milestoneService
    ) {
        this.guideRepository = guideRepository;
        this.guideAvailabilityRepository = guideAvailabilityRepository;
        this.guideReservationRepository = guideReservationRepository;
        this.stateRepository = stateRepository;
        this.reviewSummaryRepository = reviewSummaryRepository;
        this.bookingRepository = bookingRepository;
        this.selectionRepository = selectionRepository;
        this.milestoneService = milestoneService;
    }

    @Transactional(readOnly = true)
    public List<GuideSummaryResponse> getGuides(Integer stateId) {
        List<Guide> guides;
        if (stateId != null) {
            guides = guideRepository.findByState_StateIdAndIsActiveTrue(stateId);
        } else {
            guides = guideRepository.findAllActiveWithLanguages();
        }

        return guides.stream()
                .map(this::mapToSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public GuideSummaryResponse getGuideById(Long guideId) {
        Guide guide = guideRepository.findByIdWithLanguages(guideId)
                .orElseThrow(() -> new IllegalArgumentException("Guide not found with ID: " + guideId));

        return mapToSummary(guide);
    }

    @Transactional(readOnly = true)
    public GuideSummaryResponse getGuideProfileForUser(User user) {
        Guide guide = guideRepository.findByUser_UserId(user.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Guide profile does not exist for this account"));

        return mapToSummary(guide);
    }

    /**
     * The tours this guide has actually been given. (FR-16, FR-23)
     *
     * <p>Newest first, because a partner opening a portal wants the
     * next job, not the oldest one. Refuses rather than returning an
     * empty list when the account has no guide profile: an empty
     * list would be indistinguishable from "no tours yet", and the
     * partner would have no way to tell that they had never
     * completed onboarding.
     */
    @Transactional(readOnly = true)
    public List<GuideReservationResponse> myReservations(User user) {
        Guide guide = guideRepository.findByUser_UserId(user.getUserId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Guide profile does not exist for this account"
                ));

        return guideReservationRepository
                .findByGuide_GuideId(guide.getGuideId())
                .stream()
                .sorted(Comparator.comparing(
                                GuideReservation::getTourDate
                        ).reversed()
                        .thenComparing(
                                GuideReservation::getGuideReservationId
                        ).reversed())
                .map(GuideReservationResponse::of)
                .toList();
    }

    @Transactional
    public GuideReservationResponse updateReservationStatusForUser(
            User partner,
            Long reservationId,
            GuideReservationStatus newStatus
    ) {
        Guide guide = guideRepository.findByUser_UserId(partner.getUserId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Guide profile does not exist for this account"
                ));

        return updateReservationStatus(
                guide.getGuideId(), reservationId, newStatus
        );
    }

    /**
     * Reports how far a tour has got, and settles the booking when the
     * guide says it was given.
     *
     * <p>This is the only thing in the system that can move a guide
     * booking to COMPLETED. Without it the Review Center could never
     * offer a guide card, because
     * {@code TripReviewService} gates guide eligibility on a completed
     * booking, and any trip containing a guide could never be marked
     * complete, because
     * {@code TripMilestoneService.completeTripIfFinished} requires
     * every booked selection to be settled.
     *
     * <p>Mirrors {@code RoomStayService.checkOut} rather than inventing
     * its own rules: a guide who performed the tour is the authority,
     * the same way a host who checked a guest out is.
     *
     * @param guideId the guide asserting the change, used to prove the
     *                reservation is actually theirs
     */
    @Transactional
    public GuideReservationResponse updateReservationStatus(
            Long guideId,
            Long reservationId,
            GuideReservationStatus newStatus
    ) {
        GuideReservation reservation = guideReservationRepository
                .findByIdForUpdate(reservationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Guide reservation not found with ID: " + reservationId
                ));

        Guide guide = reservation.getGuide();

        if (guide == null
                || guide.getGuideId() == null
                || !guide.getGuideId().equals(guideId)) {

            throw new IllegalArgumentException(
                    "Guide reservation does not belong to this guide"
            );
        }

        GuideReservationStatus current = reservation.getStatus();

        if (current == newStatus) {
            throw new IllegalArgumentException(
                    "Tour is already " + newStatus
            );
        }

        Set<GuideReservationStatus> permitted =
                ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());

        if (!permitted.contains(newStatus)) {
            throw new IllegalArgumentException(
                    "A tour that is " + current + " cannot become " + newStatus
            );
        }

        reservation.setStatus(newStatus);
        guideReservationRepository.save(reservation);

        if (newStatus == GuideReservationStatus.COMPLETED) {
            completeBooking(reservation);
        }

        return GuideReservationResponse.of(reservation);
    }

    /**
     * Settles the booking behind a completed tour.
     *
     * <p>Guarded on the booking still being live: a tour can be
     * reported complete after the traveller cancelled the booking,
     * and overwriting CANCELLED with COMPLETED would claim the guide
     * was paid for a journey nobody took. This is the mirror image of
     * the cab rule in {@code CabService}, where a cancelled ride
     * that becomes COMPLETED is exactly the outcome to prevent.
     */
    private void completeBooking(GuideReservation reservation) {
        Booking booking = reservation.getBooking();

        if (booking == null || booking.getBookingId() == null) {
            return;
        }

        if (booking.getBookingStatus() == BookingStatus.CONFIRMED
                || booking.getBookingStatus() == BookingStatus.CHECKED_IN) {

            booking.setBookingStatus(BookingStatus.COMPLETED);
            bookingRepository.save(booking);
        }

        advanceTripMap(booking);
    }

    /**
     * Re-evaluates the trip's closing pin.
     *
     * <p>A tour has no milestone of its own to tick, so unlike
     * {@code RoomStayService.advanceTripMap} this completes no
     * checkpoint. What matters is that TRIP_COMPLETED is derived from
     * the booking states, and one of them has just changed, so the map
     * is stale until this re-reads it.
     */
    private void advanceTripMap(Booking booking) {
        selectionRepository
                .findByBooking_BookingId(booking.getBookingId())
                .ifPresent(selection -> {
                    Trip trip = selection.getTrip();

                    if (trip != null) {
                        milestoneService.completeTripIfFinished(trip);
                    }
                });
    }

    @Transactional
    public GuideSummaryResponse upsertGuideProfile(User user, GuideProfileRequest request) {
        State state = stateRepository.findById(request.stateId())
                .orElseThrow(() -> new IllegalArgumentException("State not found with ID: " + request.stateId()));

        Guide guide = guideRepository.findByUser_UserId(user.getUserId())
                .orElseGet(() -> {
                    Guide newGuide = new Guide();
                    newGuide.setUser(user);
                    return newGuide;
                });

        guide.setState(state);
        guide.setDailyRate(request.dailyRate());
        guide.setBio(request.bio());
        if (request.yearsOfExperience() != null) {
            guide.setYearsOfExperience(request.yearsOfExperience());
        }

        // Update languages
        if (request.languages() != null) {
            guide.getLanguages().clear();
            for (String lang : request.languages()) {
                if (lang != null && !lang.trim().isEmpty()) {
                    guide.getLanguages().add(new GuideLanguage(guide, lang.trim()));
                }
            }
        }

        Guide saved = guideRepository.save(guide);
        return mapToSummary(saved);
    }

    @Transactional(readOnly = true)
    public boolean checkAvailability(Long guideId, LocalDate date) {
        // First check reservations
        boolean hasBooking = guideReservationRepository.existsByGuide_GuideIdAndTourDate(guideId, date);
        if (hasBooking) {
            return false;
        }

        // Next check manual availability override
        return guideAvailabilityRepository.findByGuide_GuideIdAndAvailabilityDate(guideId, date)
                .map(GuideAvailability::getAvailable)
                .orElse(true); // Default to available if not explicitly marked unavailable
    }

    @Transactional
    public void setAvailability(User user, LocalDate date, boolean isAvailable) {
        Guide guide = guideRepository.findByUser_UserId(user.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Guide profile not found for this account"));

        GuideAvailability availability = guideAvailabilityRepository
                .findByGuide_GuideIdAndAvailabilityDate(guide.getGuideId(), date)
                .orElseGet(() -> {
                    GuideAvailability ga = new GuideAvailability();
                    ga.setGuide(guide);
                    ga.setAvailabilityDate(date);
                    return ga;
                });

        availability.setAvailable(isAvailable);
        guideAvailabilityRepository.save(availability);
    }

    private GuideSummaryResponse mapToSummary(Guide guide) {
        List<String> langs = guide.getLanguages() != null
                ? guide.getLanguages().stream().map(GuideLanguage::getLanguageName).toList()
                : List.of();

        return new GuideSummaryResponse(
                guide.getGuideId(),
                guide.getUser().getFullName(),
                guide.getUser().getEmail(),
                guide.getUser().getPhoneNumber(),
                guide.getState() != null ? guide.getState().getStateId() : null,
                guide.getState() != null ? guide.getState().getName() : null,
                guide.getDailyRate(),
                guide.getCurrencyCode(),
                guide.getBio(),
                guide.getYearsOfExperience(),
                ratingOf(guide),
                guide.getVerified(),
                langs,
                reviewCountOf(guide)
        );
    }

    /**
     * The published rating, or null when there are none.
     *
     * <p>Read from the review summary rather than the guide's own
     * {@code rating} column, which is never written by anything
     * and therefore always reports the 5.00 it was declared with.
     * A guide with forty one-star reviews was shown as perfect.
     *
     * <p>Null rather than a default, because a number on a card
     * reads as evidence.
     */
    private BigDecimal ratingOf(Guide guide) {
        ReviewSummary summary = summaryOf(guide.getGuideId());

        return summary == null || summary.getReviewCount() == 0
                ? null
                : summary.getAverageRating();
    }

    private Integer reviewCountOf(Guide guide) {
        ReviewSummary summary = summaryOf(guide.getGuideId());

        return summary == null || summary.getReviewCount() == null
                ? 0
                : summary.getReviewCount();
    }

    private ReviewSummary summaryOf(Long guideId) {
        return reviewSummaryRepository
                .findByTargetTypeAndTargetId(
                        ReviewTargetType.GUIDE, guideId)
                .orElse(null);
    }
}
