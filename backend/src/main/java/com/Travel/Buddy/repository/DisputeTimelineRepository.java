package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.DisputeTimelineEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DisputeTimelineRepository
        extends JpaRepository<DisputeTimelineEntry, Long> {

    List<DisputeTimelineEntry>
            findByDispute_DisputeIdOrderByCreatedAtAsc(
                    Long disputeId
            );
}