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

@Service
public class GuideService {

    private final GuideRepository guideRepository;
    private final GuideAvailabilityRepository guideAvailabilityRepository;
    private final GuideReservationRepository guideReservationRepository;
    private final StateRepository stateRepository;
    private final ReviewSummaryRepository reviewSummaryRepository;

    public GuideService(
            GuideRepository guideRepository,
            GuideAvailabilityRepository guideAvailabilityRepository,
            GuideReservationRepository guideReservationRepository,
            StateRepository stateRepository,
            ReviewSummaryRepository reviewSummaryRepository
    ) {
        this.guideRepository = guideRepository;
        this.guideAvailabilityRepository = guideAvailabilityRepository;
        this.guideReservationRepository = guideReservationRepository;
        this.stateRepository = stateRepository;
        this.reviewSummaryRepository = reviewSummaryRepository;
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
