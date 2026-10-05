package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.Country;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CountryRepository
        extends JpaRepository<Country, Integer> {

    List<Country> findAllByOrderByNameAsc();

    Optional<Country> findByIsoCode(String isoCode);
}