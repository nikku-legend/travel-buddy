package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.PartnerApplication;
import com.Travel.Buddy.entity.PartnerApplicationStatus;
import com.Travel.Buddy.entity.PartnerType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PartnerApplicationRepository
        extends JpaRepository<PartnerApplication, Long> {

    Optional<PartnerApplication>
    findByUser_UserIdAndPartnerType(
            Long userId,
            PartnerType partnerType
    );

    /**
     * All applications for one user, newest first. Used to build the
     * "my partner applications" screen.
     */
    @Query("""
            SELECT application
            FROM PartnerApplication application
            WHERE application.user.userId = :userId
            ORDER BY application.partnerType ASC
            """)
    List<PartnerApplication> findByUserId(
            @Param("userId") Long userId
    );

    /**
     * Admin review queue.
     *
     * <p>Only applications awaiting a decision are returned; approved,
     * rejected and withdrawn rows are history, not work.
     */
    @Query("""
            SELECT application
            FROM PartnerApplication application
            WHERE application.status IN :statuses
            ORDER BY application.submittedAt ASC
            """)
    List<PartnerApplication> findByStatuses(
            @Param("statuses")
            List<PartnerApplicationStatus> statuses
    );

    @Query("""
            SELECT application
            FROM PartnerApplication application
            WHERE application.status IN :statuses
            ORDER BY application.submittedAt ASC
            """)
    Page<PartnerApplication> findByStatuses(
            @Param("statuses")
            List<PartnerApplicationStatus> statuses,
            Pageable pageable
    );

    /**
     * Locks the application row while an admin decides on it, so two
     * admins cannot both approve or both reject the same application.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT application
            FROM PartnerApplication application
            WHERE application.applicationId = :applicationId
            """)
    Optional<PartnerApplication> findByIdForUpdate(
            @Param("applicationId")
            Long applicationId
    );

    boolean existsByUser_UserIdAndPartnerTypeAndStatusIn(
            Long userId,
            PartnerType partnerType,
            List<PartnerApplicationStatus> statuses
    );

    long countByStatus(PartnerApplicationStatus status);
}
