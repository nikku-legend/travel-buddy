package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.TouristPlace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TouristPlaceRepository
        extends JpaRepository<TouristPlace, Long> {

    List<TouristPlace> findByState_StateIdIn(
            List<Long> stateIds
    );

    List<TouristPlace> findByActiveTrue();
}
