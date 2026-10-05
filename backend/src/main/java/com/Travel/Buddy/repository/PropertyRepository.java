package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.Property;
import com.Travel.Buddy.entity.PropertyStatus;
import com.Travel.Buddy.entity.PropertyType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PropertyRepository
        extends JpaRepository<Property, Long> {

    /*
     * ============================================================
     * PUBLIC READS
     *
     * These filter on is_verified, which the approval service keeps in
     * sync with status = APPROVED. Only live properties are visible.
     * ============================================================
     */

    List<Property> findByState_StateIdAndActiveTrueAndVerifiedTrueOrderByNameAsc(
            Integer stateId
    );

    List<Property> findByState_StateIdAndPropertyTypeAndActiveTrueAndVerifiedTrueOrderByNameAsc(
            Integer stateId,
            PropertyType propertyType
    );

    List<Property> findByActiveTrueAndVerifiedTrueOrderByNameAsc();

    /**
     * Candidate hotels for one city stop of a trip. (TP-08)
     *
     * <p>City-scoped rather than state-scoped on purpose. The SRS
     * asks for recommendations "near the selected city or popular
     * places", and a Bhubaneswar hotel offered to someone
     * planning a Puri stop is 60km from where they are going. A
     * state-wide query would quietly return the wrong answer.
     */
    List<Property> findByCity_CityIdAndActiveTrueAndVerifiedTrueOrderByNameAsc(
            Long cityId
    );

    /*
     * ============================================================
     * PARTNER READS
     * ============================================================ */

    List<Property> findByPartner_UserIdOrderByNameAsc(
            Long userId
    );

    long countByPartner_UserId(Long userId);

    /*
     * ============================================================
     * ADMIN READS
     * ============================================================ */

    @Query("""
            SELECT property
            FROM Property property
            WHERE property.status = :status
            ORDER BY property.submittedAt ASC
            """)
    List<Property> findByStatusOrderBySubmittedAtAsc(
            @Param("status") PropertyStatus status
    );

    @Query("""
            SELECT property
            FROM Property property
            WHERE property.status = :status
              AND property.partner.userId = :partnerId
            ORDER BY property.submittedAt ASC
            """)
    List<Property> findByStatusAndPartnerUserIdOrderBySubmittedAtAsc(
            @Param("status") PropertyStatus status,
            @Param("partnerId") Long partnerId
    );

    long countByStatus(PropertyStatus status);

    /**
     * Locks the property row so two admins reviewing the same listing
     * cannot both act on it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT property
            FROM Property property
            WHERE property.propertyId = :propertyId
            """)
    Optional<Property> findByIdForUpdate(
            @Param("propertyId") Long propertyId
    );
}