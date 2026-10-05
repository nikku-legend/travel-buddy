package com.Travel.Buddy.service.review;

import com.Travel.Buddy.dto.review.ReviewDecisionRequest;
import com.Travel.Buddy.dto.review.ReviewRequest;
import com.Travel.Buddy.dto.review.ReviewResponse;
import com.Travel.Buddy.dto.review.ReviewSummaryResponse;
import com.Travel.Buddy.entity.*;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.*;
import com.Travel.Buddy.service.notification.NotificationEvents;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Reviews, ratings and moderation. (FR-26, FR-29)
 *
 * <p>The rule the SRS states outright is
 * "only eligible users should review": a review requires a COMPLETED
 * booking that genuinely relates to the subject. Entitlement is
 * resolved server-side from the user's own bookings, so a client
 * cannot nominate a booking it does not own.
 *
 * <p>Nothing is public until a moderator publishes it, so a review
 * cannot be used to harass a partner before a human has looked.
 */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;

    private final ReviewSummaryRepository summaryRepository;

    private final BookingRepository bookingRepository;

    private final PropertyRepository propertyRepository;

    private final GuideRepository guideRepository;

    private final CabRepository cabRepository;

    private final TouristPlaceRepository touristPlaceRepository;

    private final UserRepository userRepository;

    private final CabRideRepository cabRideRepository;

    private final NotificationEvents notificationEvents;

    public ReviewService(
            ReviewRepository reviewRepository,
            ReviewSummaryRepository summaryRepository,
            BookingRepository bookingRepository,
            PropertyRepository propertyRepository,
            GuideRepository guideRepository,
            CabRepository cabRepository,
            TouristPlaceRepository touristPlaceRepository,
            UserRepository userRepository,
            CabRideRepository cabRideRepository,
            NotificationEvents notificationEvents
    ) {
        this.reviewRepository = reviewRepository;
        this.summaryRepository = summaryRepository;
        this.bookingRepository = bookingRepository;
        this.propertyRepository = propertyRepository;
        this.guideRepository = guideRepository;
        this.cabRepository = cabRepository;
        this.touristPlaceRepository = touristPlaceRepository;
        this.userRepository = userRepository;
        this.cabRideRepository = cabRideRepository;
        this.notificationEvents = notificationEvents;
    }

    /* ============================================================
     * SUBMITTING
     * ============================================================ */

    @Transactional
    public ReviewResponse submit(
            Long userId,
            ReviewRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "User not found"
                        )
                );

        requireTargetExists(request.targetType(), request.targetId());

        if (reviewRepository
                .findByUser_UserIdAndTargetTypeAndTargetId(
                        userId,
                        request.targetType(),
                        request.targetId()
                ).isPresent()) {

            throw PartnerApplicationException.conflict(
                    "You have already reviewed this. You can edit your existing review instead."
            );
        }

        /*
         * Entitlement. The booking is chosen from the caller's own
         * completed bookings, never taken from the request body, so a
         * review can never be attached to someone else's stay.
         */
        Entitlement entitlement = findEntitlement(
                userId,
                request.targetType(),
                request.targetId()
        );

        if (!entitlement.eligible()) {

            throw PartnerApplicationException.forbidden(
                    eligibilityMessage(request.targetType())
            );
        }

        Review review = new Review();

        review.setUser(user);
        review.setTargetType(request.targetType());
        review.setTargetId(request.targetId());
        review.setBooking(entitlement.booking());
        review.setRating(request.rating());
        review.setTitle(trim(request.title()));
        review.setComment(trim(request.comment()));
        review.setStatus(ReviewStatus.PENDING);

        return toResponse(reviewRepository.save(review), userId);
    }
    /* ============================================================
     * READING
     * ============================================================ */

    /**
     * Public reviews for a subject. Published only, newest first.
     */
    @Transactional(readOnly = true)
    public List<ReviewResponse> publicReviews(
            ReviewTargetType targetType,
            Long targetId
    ) {
        requireTargetExists(targetType, targetId);

        return reviewRepository
                .findByTargetTypeAndTargetIdAndStatusOrderByCreatedAtDesc(
                        targetType,
                        targetId,
                        ReviewStatus.PUBLISHED
                )
                .stream()
                /* The reader is not signed in as the author. */
                .map(review -> toResponse(review, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> myReviews(Long userId) {
        return reviewRepository
                .findByUser_UserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(review -> toResponse(review, userId))
                .toList();
    }

    /**
     * Whether the signed-in user could review a subject right now.
     *
     * <p>Lets the UI show or hide the review prompt instead of
     * failing after the user has already written something.
     */
    @Transactional(readOnly = true)
    public boolean isEligible(
            Long userId,
            ReviewTargetType targetType,
            Long targetId
    ) {
        return findEntitlement(
                userId, targetType, targetId
        ).eligible();
    }

    @Transactional(readOnly = true)
    public ReviewSummaryResponse summary(
            ReviewTargetType targetType,
            Long targetId
    ) {
        return summaryRepository
                .findByTargetTypeAndTargetId(targetType, targetId)
                .map(this::toSummaryResponse)
                .orElse(
                        new ReviewSummaryResponse(
                                targetType.name(),
                                targetId,
                                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                                0, 0, 0, 0, 0, 0
                        )
                );
    }
    /* ============================================================
     * AUTHOR ACTIONS
     * ============================================================ */

    /**
     * Edits the author's own review.
     *
     * <p>Only while it is still PENDING or REJECTED. Once a
     * moderator has published it, editing would let a reviewer
     * change the meaning of something already approved, so the
     * review is withdrawn and must be moderated again instead.
     */
    @Transactional
    public ReviewResponse edit(
            Long userId,
            Long reviewId,
            ReviewRequest request
    ) {
        Review review = requireOwnedReview(userId, reviewId);

        if (review.getStatus() == ReviewStatus.PUBLISHED) {

            throw PartnerApplicationException.conflict(
                    "A published review cannot be edited. Withdraw it first if you need to change it."
            );
        }

        if (!review.getTargetType()
                .equals(request.targetType())
                || !review.getTargetId()
                .equals(request.targetId())) {

            throw PartnerApplicationException.badRequest(
                    "A review cannot be moved to a different subject"
            );
        }

        review.setRating(request.rating());
        review.setTitle(trim(request.title()));
        review.setComment(trim(request.comment()));

        /* A corrected review goes back to the queue. */
        review.setStatus(ReviewStatus.PENDING);
        review.setModeratedAt(null);
        review.setModerationReason(null);

        return toResponse(reviewRepository.save(review), userId);
    }

    /**
     * Withdraws the author's own review and removes it from the
     * subject's rating.
     */
    @Transactional
    public void withdraw(
            Long userId,
            Long reviewId
    ) {
        Review review = requireOwnedReview(userId, reviewId);

        ReviewTargetType targetType = review.getTargetType();
        Long targetId = review.getTargetId();

        reviewRepository.delete(review);

        /*
         * Rebuild AFTER the delete. Rebuilding first would still
         * count this review as published, leaving the subject's
         * average permanently one star too high.
         */
        rebuildSummary(targetType, targetId);
    }

    /**
     * Reports a review for moderator attention.
     *
     * <p>Crosses a threshold into FLAGGED so a burst of reports
     * becomes visible without a human reading every one.
     */
    @Transactional
    public ReviewResponse flag(
            Long userId,
            Long reviewId
    ) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Review not found"
                        )
                );

        if (review.getUser()
                .getUserId()
                .equals(userId)) {

            throw PartnerApplicationException.badRequest(
                    "You cannot report your own review"
            );
        }

        if (review.getStatus() != ReviewStatus.PUBLISHED) {

            throw PartnerApplicationException.conflict(
                    "Only a published review can be reported"
            );
        }

        review.setFlaggedCount(
                (review.getFlaggedCount() == null
                        ? 0
                        : review.getFlaggedCount()) + 1
        );

        if (review.getFlaggedCount() >= 3) {

            review.setStatus(ReviewStatus.FLAGGED);

            rebuildSummary(
                    review.getTargetType(),
                    review.getTargetId()
            );
        }

        return toResponse(reviewRepository.save(review), userId);
    }

    /* ============================================================
     * MODERATION (FR-29)
     * ============================================================ */

    @Transactional(readOnly = true)
    public List<ReviewResponse> moderationQueue() {
        return reviewRepository
                .findByStatusOrderByCreatedAtAsc(
                        ReviewStatus.PENDING,
                        org.springframework.data.domain
                                .PageRequest.of(0, 200)
                )
                .getContent()
                .stream()
                .map(review -> toResponse(review, null))
                .toList();
    }

    /**
     * Publishes or rejects a review, then rebuilds the subject's
     * rating either way so the aggregate never drifts.
     */
    @Transactional
    public ReviewResponse moderate(
            Long adminId,
            Long reviewId,
            ReviewDecisionRequest request
    ) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Admin not found"
                        )
                );

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Review not found"
                        )
                );

        if (review.getStatus() != ReviewStatus.PENDING
                && review.getStatus() != ReviewStatus.FLAGGED) {

            throw PartnerApplicationException.conflict(
                    "This review is " + review.getStatus()
                            + " and cannot be moderated"
            );
        }

        String reason = request.validatedReason();

        review.setModeratedBy(admin);
        review.setModeratedAt(LocalDateTime.now());
        review.setModerationReason(reason);

        if (Boolean.TRUE.equals(request.approved())) {
            review.setStatus(ReviewStatus.PUBLISHED);
        } else {
            review.setStatus(ReviewStatus.REJECTED);
        }

        Review saved = reviewRepository.save(review);

        rebuildSummary(
                saved.getTargetType(),
                saved.getTargetId()
        );

        notificationEvents.reviewModerated(
                saved,
                saved.getUser(),
                targetNameOf(saved),
                Boolean.TRUE.equals(request.approved()),
                reason
        );

        return toResponse(saved, null);
    }
    /* ============================================================
     * ENTITLEMENT
     * ============================================================ */

    /**
     * Finds a completed booking of the caller's that genuinely
     * relates to the subject.
     *
     * <p>Always resolved from the caller's own bookings. The client
     * never nominates which booking to use, so a review cannot be
     * attached to a stay the reviewer did not pay for.
     */
    /**
     * The completed stay that entitles a user to review a subject.
     *
     * <p>{@code booking} is null for cab reviews: a ride is not a
     * booking, so there is no booking to cite. Eligibility is still
     * proven, just by a completed ride instead.
     */
    private Entitlement findEntitlement(
            Long userId,
            ReviewTargetType targetType,
            Long targetId
    ) {
        if (targetType == ReviewTargetType.CAB) {

            boolean completed =
                    cabRideRepository.existsByCab_CabIdAndUser_UserIdAndStatus(
                            targetId,
                            userId,
                            RideStatus.COMPLETED
                    );

            return completed
                    ? new Entitlement(true, null)
                    : new Entitlement(false, null);
        }

        List<Booking> candidates =
                switch (targetType) {

                    case HOTEL ->
                            reviewRepository.findCompletedHotelBookings(
                                    userId, targetId
                            );

                    case GUIDE ->
                            reviewRepository.findCompletedGuideBookings(
                                    userId, targetId
                            );

                    case DESTINATION ->
                            reviewRepository.findAnyCompletedBookings(
                                    userId
                            );

                    case CAB -> List.of();
                };

        return candidates.stream()
                .findFirst()
                .map(booking ->
                        new Entitlement(true, booking))
                .orElseGet(() ->
                        new Entitlement(false, null)
                );
    }

    /**
     * Whether the user qualifies, and the stay that proves it.
     */
    private record Entitlement(
            boolean eligible,
            Booking booking
    ) {
    }

    private String eligibilityMessage(ReviewTargetType type) {
        return switch (type) {
            case HOTEL ->
                    "You can review a hotel only after completing a stay there";
            case GUIDE ->
                    "You can review a guide only after a completed tour";
            case CAB ->
                    "You can review a cab only after a completed ride";
            case DESTINATION ->
                    "You can review a destination only after completing a trip";
        };
    }

    /**
     * Confirms the subject exists, so a review cannot be filed
     * against an id that points at nothing.
     */
    private void requireTargetExists(
            ReviewTargetType targetType,
            Long targetId
    ) {
        boolean exists =
                switch (targetType) {
                    case HOTEL ->
                            propertyRepository.existsById(targetId);
                    case GUIDE ->
                            guideRepository.existsById(targetId);
                    case CAB ->
                            cabRepository.existsById(targetId);
                    case DESTINATION ->
                            touristPlaceRepository.existsById(targetId);
                };

        if (!exists) {
            throw PartnerApplicationException.notFound(
                    "No "
                            + targetType.name().toLowerCase()
                            + " exists with id " + targetId
            );
        }
    }

    private Review requireOwnedReview(
            Long userId,
            Long reviewId
    ) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Review not found"
                        )
                );

        if (!review.getUser()
                .getUserId()
                .equals(userId)) {

            throw PartnerApplicationException.forbidden(
                    "This review is not yours"
            );
        }

        return review;
    }

    /* ============================================================
     * RATING AGGREGATE
     * ============================================================ */

    /**
     * Recomputes a subject's average and histogram from its
     * PUBLISHED reviews.
     *
     * <p>Rebuilt from the reviews rather than adjusted in place,
     * because a publish, a rejection, a withdrawal and a flag all
     * have to converge on the same correct number.
     */
    @Transactional
    public ReviewSummaryResponse rebuildSummary(
            ReviewTargetType targetType,
            Long targetId
    ) {
        List<Review> published =
                reviewRepository.findForSummary(
                        targetType,
                        targetId,
                        ReviewStatus.PUBLISHED
                );

        ReviewSummary summary = summaryRepository
                .findByTargetTypeAndTargetId(targetType, targetId)
                .orElseGet(() -> {
                    ReviewSummary fresh = new ReviewSummary();
                    fresh.setTargetType(targetType);
                    fresh.setTargetId(targetId);

                    return fresh;
                });

        int total = 0;
        int sum = 0;
        int[] buckets = new int[6];

        for (Review review : published) {

            int rating = review.getRating() == null
                    ? 0
                    : review.getRating();

            if (rating < 1 || rating > 5) {
                continue;
            }

            total++;
            sum += rating;
            buckets[rating]++;
        }

        summary.setReviewCount(total);
        summary.setFiveStarCount(buckets[5]);
        summary.setFourStarCount(buckets[4]);
        summary.setThreeStarCount(buckets[3]);
        summary.setTwoStarCount(buckets[2]);
        summary.setOneStarCount(buckets[1]);

        summary.setAverageRating(
                total == 0
                        ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.valueOf(sum)
                        .divide(
                                BigDecimal.valueOf(total),
                                2,
                                RoundingMode.HALF_UP
                        )
        );

        return toSummaryResponse(
                summaryRepository.save(summary)
        );
    }
    /* ============================================================
     * MAPPERS
     * ============================================================ */

    private ReviewResponse toResponse(
            Review review,
            Long viewerId
    ) {
        boolean isAuthor =
                viewerId != null
                        && review.getUser()
                        .getUserId()
                        .equals(viewerId);

        return new ReviewResponse(
                review.getReviewId(),
                review.getTargetType(),
                review.getTargetId(),
                targetNameOf(review),
                review.getRating(),
                review.getTitle(),
                review.getComment(),
                review.getStatus(),

                /*
                 * Display name only. A public review must not carry
                 * the author's contact details.
                 */
                review.getUser().getFullName(),

                review.getBooking() == null
                        ? "Verified traveller"
                        : "Verified stay " + review.getBooking()
                        .getBookingReference(),
                review.getHelpfulCount(),
                review.getFlaggedCount(),
                review.getCreatedAt(),
                review.getModeratedAt(),
                review.getModerationReason(),

                isAuthor
                        && (review.getStatus() == ReviewStatus.PENDING
                        || review.getStatus() == ReviewStatus.REJECTED),
                isAuthor
        );
    }

    private ReviewSummaryResponse toSummaryResponse(
            ReviewSummary summary
    ) {
        return new ReviewSummaryResponse(
                summary.getTargetType().name(),
                summary.getTargetId(),
                summary.getAverageRating(),
                summary.getReviewCount() == null
                        ? 0
                        : summary.getReviewCount(),
                value(summary.getFiveStarCount()),
                value(summary.getFourStarCount()),
                value(summary.getThreeStarCount()),
                value(summary.getTwoStarCount()),
                value(summary.getOneStarCount())
        );
    }

    private long value(Integer number) {
        return number == null ? 0 : number;
    }

    private String targetNameOf(Review review) {
        Long id = review.getTargetId();

        return switch (review.getTargetType()) {
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

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}