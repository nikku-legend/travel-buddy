package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.TripRecommendation;
import com.Travel.Buddy.entity.TripRecommendationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripRecommendationRepository
        extends JpaRepository<TripRecommendation, Long> {

    List<TripRecommendation> findByTrip_TripIdOrderByRankPositionAsc(
            Long tripId
    );

    List<TripRecommendation> findByTrip_TripIdAndRecommendationTypeOrderByRankPositionAsc(
            Long tripId,
            TripRecommendationType type
    );

    /**
     * Acceptance rate for the engine's own evaluation. Empty when
     * the traveller was never shown a recommendation, which is a
     * different measurement from "shown and ignored".
     */
    List<TripRecommendation> findByTrip_TripIdAndShown(
            Long tripId,
            boolean shown
    );

    void deleteByTrip_TripId(Long tripId);
}