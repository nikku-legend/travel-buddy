package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.ReviewTargetType;
import com.Travel.Buddy.entity.ReviewUnlock;
import com.Travel.Buddy.entity.ReviewUnlockStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewUnlockRepository
        extends JpaRepository<ReviewUnlock, Long> {

    /**
     * Every card on a trip, oldest first.
     *
     * <p>Ordered by unlock id so the Review Center lists services in
     * the order they were booked, which is the order the traveller
     * remembers them in. Creation order is the only ordering available
     * that is stable across repeated derivations.
     */
    List<ReviewUnlock> findByTrip_TripIdOrderByUnlockIdAsc(Long tripId);

    /**
     * Cards that can accept a review right now.
     *
     * <p>Locked cards are excluded deliberately: section 10 is about
     * the services that became reviewable, not a progress list of the
     * ones still pending.
     */
    List<ReviewUnlock> findByTrip_TripIdAndStatusOrderByUnlockIdAsc(
            Long tripId,
            ReviewUnlockStatus status
    );

    List<ReviewUnlock> findByTrip_TripIdAndStatusInOrderByUnlockIdAsc(
            Long tripId,
            Collection<ReviewUnlockStatus> statuses
    );

    Optional<ReviewUnlock> findByTrip_TripIdAndTargetTypeAndTargetId(
            Long tripId,
            ReviewTargetType targetType,
            Long targetId
    );

    boolean existsByTrip_TripIdAndTargetTypeAndTargetId(
            Long tripId,
            ReviewTargetType targetType,
            Long targetId
    );

    /**
     * The card a given review belongs to, if any.
     *
     * <p>Used to keep the card's status in step with moderation.
     * A review filed outside the planner still matches, because the
     * lookup is by review id rather than by trip.
     */
    @Query("""
            SELECT unlock
            FROM ReviewUnlock unlock
            WHERE unlock.review.reviewId = :reviewId
            """)
    Optional<ReviewUnlock> findByReview_ReviewId(
            @Param("reviewId") Long reviewId
    );

    long countByTrip_TripIdAndStatusIn(
            Long tripId,
            Collection<ReviewUnlockStatus> statuses
    );
}