package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.TripMilestone;
import com.Travel.Buddy.entity.TripMilestoneType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripMilestoneRepository
        extends JpaRepository<TripMilestone, Long> {

    List<TripMilestone> findByTrip_TripIdOrderByProgressPercentAsc(
            Long tripId
    );

    /**
     * Whole-trip checkpoints, such as the start and the finish,
     * carry no city.
     */
    Optional<TripMilestone> findByTrip_TripIdAndMilestoneTypeAndTripCityIsNull(
            Long tripId,
            TripMilestoneType milestoneType
    );

    /**
     * Per-city checkpoints. Scoped by city so a stop's arrival is
     * never completed because a different stop was reached.
     */
    Optional<TripMilestone> findByTrip_TripIdAndMilestoneTypeAndTripCity_TripCityId(
            Long tripId,
            TripMilestoneType milestoneType,
            Long tripCityId
    );

    boolean existsByTrip_TripIdAndMilestoneTypeAndTripCity_TripCityId(
            Long tripId,
            TripMilestoneType milestoneType,
            Long tripCityId
    );

    /**
     * Drops only the per-city checkpoints.
     *
     * <p>Used when a route is replaced wholesale. The start and
     * finish checkpoints are not tied to a stop and must survive,
     * along with any completion already recorded against them.
     */
    void deleteByTrip_TripIdAndTripCityIsNotNull(Long tripId);
}