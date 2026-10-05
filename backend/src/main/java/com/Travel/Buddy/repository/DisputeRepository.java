package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.Dispute;
import com.Travel.Buddy.entity.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DisputeRepository
        extends JpaRepository<Dispute, Long> {

    /**
     * The check that stops one traveller opening five claims on
     * the same trip. Only open statuses are considered: a closed
     * dispute stays on the record and must not block a genuinely
     * new problem with the same booking.
     */
    /**
     * Whether this booking already has a live dispute. Returns the
     * match rather than a boolean so the caller can name the
     * existing dispute in its error message.
     */
    @Query("""
            select d from Dispute d
            where d.booking.id = :bookingId
              and d.status in :statuses
            """)
    Optional<Dispute> findLiveByBookingId(
            @Param("bookingId") Long bookingId,
            @Param("statuses") List<DisputeStatus> statuses
    );

    @Query("""
            select d from Dispute d
            where d.booking.id = :bookingId
            """)
    List<Dispute> findByBookingId(
            @Param("bookingId") Long bookingId
    );

    Page<Dispute> findByRaisedBy_UserIdOrderByCreatedAtDesc(
            Long userId,
            Pageable pageable
    );

    Page<Dispute> findByPartner_UserIdOrderByCreatedAtDesc(
            Long partnerId,
            Pageable pageable
    );

    /**
     * The admin queue. Ordered oldest first because a claim that
     * has waited longest is the one most likely to breach the
     * dispute window and be lost.
     */
    Page<Dispute> findByStatusInOrderByCreatedAtAsc(
            List<DisputeStatus> statuses,
            Pageable pageable
    );

    @Query("""
            select count(d) from Dispute d
            where d.status in :statuses
            """)
    long countByStatusIn(
            @Param("statuses") List<DisputeStatus> statuses
    );

    /**
     * Total money the platform has agreed to return through
     * disputes, for the admin dashboard.
     */
    @Query("""
            select coalesce(sum(d.resolvedAmount), 0)
            from Dispute d
            where d.resolution is not null
              and d.status = com.Travel.Buddy.entity.DisputeStatus.RESOLVED
            """)
    java.math.BigDecimal sumResolvedAmounts();

    /**
     * Locked read for the admin action, so two moderators cannot
     * both rule on the same dispute. The @Version column on the
     * entity is the second line of defence.
     */
    @Query("""
            select d from Dispute d
            where d.disputeId = :disputeId
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Dispute> findByIdForUpdate(
            @Param("disputeId") Long disputeId
    );
}