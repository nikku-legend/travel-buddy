package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.CabRide;
import com.Travel.Buddy.entity.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CabRideRepository extends JpaRepository<CabRide, Long> {

    List<CabRide> findByUser_UserIdOrderByCreatedAtDesc(Long userId);

    List<CabRide> findByCab_Partner_UserIdOrderByCreatedAtDesc(Long partnerId);

    List<CabRide> findByCab_CabIdAndStatus(Long cabId, RideStatus status);

    /**
     * Entitlement for a cab review: the rider must have completed a
     * ride in this exact vehicle. (FR-26)
     */
    boolean existsByCab_CabIdAndUser_UserIdAndStatus(
            Long cabId,
            Long userId,
            com.Travel.Buddy.entity.RideStatus status
    );
/**
     * The ride reserved for a booking.
     *
     * <p>TripReviewService needs the ride behind a cab selection to
     * decide whether that service has completed. Cab entitlement in
     * ReviewService is proved by a completed ride rather than a
     * completed booking, so a cab card follows the ride's status, not
     * the booking's.
     */
    java.util.Optional<CabRide> findByBooking_BookingId(
            Long bookingId
    );
}