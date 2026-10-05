package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.GuideAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GuideAvailabilityRepository extends JpaRepository<GuideAvailability, Long> {

    List<GuideAvailability> findByGuide_GuideIdAndAvailabilityDateBetween(
            Long guideId,
            LocalDate startDate,
            LocalDate endDate
    );

    Optional<GuideAvailability> findByGuide_GuideIdAndAvailabilityDate(
            Long guideId,
            LocalDate date
    );
}
