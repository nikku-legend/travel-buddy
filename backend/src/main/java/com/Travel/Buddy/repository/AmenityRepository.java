package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.Amenity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AmenityRepository extends JpaRepository<Amenity, Integer> {

    Optional<Amenity> findByCode(String code);

    List<Amenity> findByCategoryOrderByLabelAsc(String category);
}