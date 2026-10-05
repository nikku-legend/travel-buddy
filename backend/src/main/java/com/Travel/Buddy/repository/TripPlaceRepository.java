package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.TripPlace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripPlaceRepository
        extends JpaRepository<TripPlace, Long> {

    List<TripPlace> findByTrip_TripIdOrderByTripPlaceIdAsc(
            Long tripId
    );

    List<TripPlace> findByTripCity_TripCityId(
            Long tripCityId
    );

    Optional<TripPlace> findByTrip_TripIdAndPlace_PlaceId(
            Long tripId,
            Long placeId
    );
}