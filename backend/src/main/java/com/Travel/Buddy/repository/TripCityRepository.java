package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.TripCity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripCityRepository
        extends JpaRepository<TripCity, Long> {

    List<TripCity> findByTrip_TripIdOrderBySequenceAsc(
            Long tripId
    );

    Optional<TripCity> findByTrip_TripIdAndCity_CityId(
            Long tripId,
            Long cityId
    );

    Optional<TripCity> findByTrip_TripIdAndSequence(
            Long tripId,
            int sequence
    );

    long countByTrip_TripId(Long tripId);
}