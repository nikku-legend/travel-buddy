package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.PropertyPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyPolicyRepository
        extends JpaRepository<PropertyPolicy, Long> {

    List<PropertyPolicy> findByProperty_PropertyIdOrderBySortOrderAsc(
            Long propertyId
    );

    Optional<PropertyPolicy> findByPolicyIdAndProperty_PropertyId(
            Long policyId,
            Long propertyId
    );

    boolean existsByPolicyIdAndProperty_PropertyId(
            Long policyId,
            Long propertyId
    );
}