package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.Booking;
import com.Travel.Buddy.entity.Review;
import com.Travel.Buddy.entity.ReviewStatus;
import com.Travel.Buddy.entity.ReviewTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository
        extends JpaRepository<Review, Long> {

    Optional<Review> findByUser_UserIdAndTargetTypeAndTargetId(
            Long userId,
            ReviewTargetType targetType,
            Long targetId
    );

    /**
     * Public listing: only PUBLISHED reviews are ever returned, so a
     * pending or rejected one cannot leak through a different query.
     */
    List<Review> findByTargetTypeAndTargetIdAndStatusOrderByCreatedAtDesc(
            ReviewTargetType targetType,
            Long targetId,
            ReviewStatus status
    );

    Page<Review> findByTargetTypeAndTargetIdAndStatusOrderByCreatedAtDesc(
            ReviewTargetType targetType,
            Long targetId,
            ReviewStatus status,
            Pageable pageable
    );

    List<Review> findByUser_UserIdOrderByCreatedAtDesc(Long userId);

    long countByUser_UserId(Long userId);

    /**
     * The reviews that make up a subject's rating.
     *
     * <p>Used to rebuild the summary whenever a review is published,
     * rejected or edited.
     */
    @Query("""
            SELECT review
            FROM Review review
            WHERE review.targetType = :targetType
              AND review.targetId = :targetId
              AND review.status = :status
            """)
    List<Review> findForSummary(
            @Param("targetType") ReviewTargetType targetType,
            @Param("targetId") Long targetId,
            @Param("status") ReviewStatus status
    );

    /**
     * Completed bookings a user holds that cover the given target.
     *
     * <p>This is the entitlement check: a review may only be filed
     * against a booking that is COMPLETED and genuinely relates to
     * the subject being reviewed.
     */
    @Query("""
            SELECT booking
            FROM Booking booking
            WHERE booking.user.userId = :userId
              AND booking.bookingStatus = com.Travel.Buddy.entity.BookingStatus.COMPLETED
              AND EXISTS (
                SELECT 1
                FROM HotelReservation reservation
                WHERE reservation.booking = booking
                  AND reservation.roomType.property.propertyId = :targetId
              )
            """)
    List<Booking> findCompletedHotelBookings(
            @Param("userId") Long userId,
            @Param("targetId") Long targetId
    );

    @Query("""
            SELECT booking
            FROM Booking booking
            WHERE booking.user.userId = :userId
              AND booking.bookingStatus = com.Travel.Buddy.entity.BookingStatus.COMPLETED
              AND EXISTS (
                SELECT 1
                FROM GuideReservation guideReservation
                WHERE guideReservation.booking = booking
                  AND guideReservation.guide.guideId = :targetId
              )
            """)
    List<Booking> findCompletedGuideBookings(
            @Param("userId") Long userId,
            @Param("targetId") Long targetId
    );

    /**
     * Any completed booking a user holds, used to let a genuine
     * traveller review a destination without needing to stay in a
     * specific property.
     */
    @Query("""
            SELECT booking
            FROM Booking booking
            WHERE booking.user.userId = :userId
              AND booking.bookingStatus = com.Travel.Buddy.entity.BookingStatus.COMPLETED
            """)
    List<Booking> findAnyCompletedBookings(
            @Param("userId") Long userId
    );

    Page<Review> findByStatusOrderByCreatedAtAsc(
            ReviewStatus status,
            Pageable pageable
    );

    long countByStatus(ReviewStatus status);

    List<Review> findByStatusIn(Collection<ReviewStatus> statuses);
}