package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.DisputeEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DisputeEvidenceRepository
        extends JpaRepository<DisputeEvidence, Long> {

    List<DisputeEvidence> findByDispute_DisputeIdOrderByCreatedAtAsc(
            Long disputeId
    );

    long countByDispute_DisputeId(Long disputeId);
}