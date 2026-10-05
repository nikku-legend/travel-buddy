package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.PropertyAmenity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PropertyAmenityRepository
        extends JpaRepository<PropertyAmenity, PropertyAmenity.Id> {

    /**
     * Grouped by category on read, so the detail page can show
     * "Facilities" and "Access" as separate sections.
     */
    List<PropertyAmenity> findByIdPropertyIdOrderByAmenityCategoryAscAmenityLabelAsc(
            Long propertyId
    );
}