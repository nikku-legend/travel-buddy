package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.TripSelection;
import com.Travel.Buddy.entity.TripSelectionStatus;
import com.Travel.Buddy.entity.TripSelectionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripSelectionRepository
        extends JpaRepository<TripSelection, Long> {

    List<TripSelection> findByTrip_TripIdOrderBySelectionIdAsc(
            Long tripId
    );

    List<TripSelection> findByTrip_TripIdAndStatusInOrderBySelectionIdAsc(
            Long tripId,
            List<TripSelectionStatus> statuses
    );

    List<TripSelection> findByTrip_TripIdAndSelectionTypeOrderBySelectionIdAsc(
            Long tripId,
            TripSelectionType selectionType
    );

    Optional<TripSelection> findByTrip_TripIdAndSelectionTypeAndTargetId(
            Long tripId,
            TripSelectionType selectionType,
            Long targetId
    );
    

    /**
     * The selection a booking was created from, if any.
     *
     * <p>This is the only route from a real-world guest event back
     * to the trip it belongs to. A direct hotel booking has no
     * trip at all, so callers must treat an empty result as "this
     * booking is not part of any journey" rather than as an error.
     */
    Optional<TripSelection> findByBooking_BookingId(Long bookingId);
}
