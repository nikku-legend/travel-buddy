package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.PropertyImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyImageRepository
        extends JpaRepository<PropertyImage, Long> {

    List<PropertyImage> findByProperty_PropertyIdOrderBySortOrderAsc(
            Long propertyId
    );

    Optional<PropertyImage> findByProperty_PropertyIdAndCoverTrue(
            Long propertyId
    );

    /**
     * Both lookups are property scoped so that a guessed image id
     * belonging to another partner's property resolves to nothing.
     */
    Optional<PropertyImage> findByImageIdAndProperty_PropertyId(
            Long imageId,
            Long propertyId
    );

    boolean existsByImageIdAndProperty_PropertyId(
            Long imageId,
            Long propertyId
    );
}