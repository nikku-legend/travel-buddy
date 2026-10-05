package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.ReviewSummary;
import com.Travel.Buddy.entity.ReviewSummaryId;
import com.Travel.Buddy.entity.ReviewTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewSummaryRepository
        extends JpaRepository<ReviewSummary, ReviewSummaryId> {

    Optional<ReviewSummary> findByTargetTypeAndTargetId(
            ReviewTargetType targetType,
            Long targetId
    );
}