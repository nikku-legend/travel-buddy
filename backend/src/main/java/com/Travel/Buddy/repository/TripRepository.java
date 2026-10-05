package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.Trip;
import com.Travel.Buddy.entity.TripStatus;
import com.Travel.Buddy.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TripRepository
        extends JpaRepository<Trip, Long> {

    Page<Trip> findByUser_UserIdOrderByCreatedAtDesc(
            Long userId,
            Pageable pageable
    );

    List<Trip> findByUser_UserIdAndStatusIn(
            Long userId,
            List<TripStatus> statuses
    );

    /**
     * Locked read for the checkout flow.
     *
     * <p>This lock is what enforces "only one open checkout per
     * trip". MySQL has no partial index, so a generated-column
     * trick was tried and rejected: a STORED generated column
     * over a foreign-key column silently prevents that foreign
     * key from being created. A row lock is the portable
     * guarantee.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.tripId = :tripId")
    Optional<Trip> findByIdForUpdate(
            @Param("tripId") Long tripId
    );

    /**
     * Trips whose end date has passed, used by the completion
     * sweep that opens post-trip reviews (TP-12).
     */
    @Query("""
            select t from Trip t
            where t.status in :statuses
              and t.endDate < :today
            order by t.endDate asc
            """)
    List<Trip> findDueForCompletion(
            @Param("statuses") List<TripStatus> statuses,
            @Param("today") java.time.LocalDate today
    );
}