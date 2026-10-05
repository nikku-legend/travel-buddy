package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.PropertyCancellationTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyCancellationTermRepository
        extends JpaRepository<PropertyCancellationTerm, Long> {

    /**
     * Widest window first, because that is the tier a traveller
     * checks their own cancellation date against.
     */
    List<PropertyCancellationTerm> findByProperty_PropertyIdOrderByDaysBeforeCheckInDesc(
            Long propertyId
    );

    Optional<PropertyCancellationTerm> findByTermIdAndProperty_PropertyId(
            Long termId,
            Long propertyId
    );
}