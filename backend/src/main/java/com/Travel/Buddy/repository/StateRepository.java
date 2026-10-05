package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.RegionZone;
import com.Travel.Buddy.entity.State;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StateRepository
        extends JpaRepository<State, Integer> {

    List<State> findByCountry_CountryIdOrderByNameAsc(
            Integer countryId
    );

    List<State> findByCountry_CountryIdAndRegionZoneOrderByNameAsc(
            Integer countryId,
            RegionZone regionZone
    );
}