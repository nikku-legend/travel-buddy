package com.Travel.Buddy.dto.trip;

import com.Travel.Buddy.entity.ReviewStatus;
import com.Travel.Buddy.entity.ReviewTargetType;
import com.Travel.Buddy.entity.ReviewUnlockStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One reviewable service in the Review Center. (SRS 2.3 TP-12,
 * section 10)
 *
 * <p>Section 10: "each eligible hotel/guide/transport service
 * receives a review card." This is that card.
 *
 * <p>Deliberately carries the stay context -- dates, city, guests --
 * so the traveller can recognise the service without cross-checking
 * against the trip. A list of bare property names is not enough to
 * know which three nights they are being asked about.
 *
 * <p>DTO rather than entity, per the architecture rule that JPA
 * entities never cross the API boundary.
 */
public record TripReviewCardResponse(

        Long unlockId,

        ReviewTargetType targetType,

        Long targetId,

        /** Human name of the hotel, guide, cab or place. */
        String targetName,

        ReviewUnlockStatus status,

        /**
         * Whether the UI should offer a review form. False once a
         * review exists, so the card can show the written review
         * instead of a button.
         */
        boolean actionable,

        /** Why this card exists, in words worth showing. */
        String reason,

        /**
         * The booking that proves entitlement. Null for cabs, which
         * are proven by a completed ride.
         */
        Long bookingId,

        String bookingReference,

        LocalDate checkIn,

        LocalDate checkOut,

        /** City the service belongs to, for grouping the cards. */
        String cityName,

        LocalDateTime eligibleAt,

        LocalDateTime submittedAt,

        /* What the traveller already wrote, if anything. */

        Long reviewId,

        Integer rating,

        String reviewTitle,

        String reviewComment,

        /**
         * The review's own moderation status, which is a different
         * machine from the card's. A card can be ELIGIBLE while its
         * review is still PENDING moderation, and collapsing the two
         * would make "review this" and "this review is published"
         * indistinguishable.
         */
        ReviewStatus reviewStatus,

        /**
         * Moderator note shown only when the review was rejected, so
         * the traveller learns why rather than guessing.
         */
        String moderationReason
) {
    /**
     * Compact form for counts and badges, where the detail would be
     * noise.
     */
    public record TripReviewSummaryResponse(
            int total,
            int awaitingReview,
            int submitted,
            int published,
            int flagged
    ) {
        public static TripReviewSummaryResponse empty() {
            return new TripReviewSummaryResponse(
                    0, 0, 0, 0, 0
            );
        }
    }

    /**
     * The whole Review Center for one trip.
     *
     * <p>Carries the summary alongside the cards so the UI can show
     * "2 of 3 reviewed" without a second request.
     */
    public record TripReviewCentreResponse(
            Long tripId,
            String tripTitle,
            String tripStatus,
            boolean reviewsUnlocked,
            TripReviewSummaryResponse summary,
            java.util.List<TripReviewCardResponse> cards
    ) {
    }
}