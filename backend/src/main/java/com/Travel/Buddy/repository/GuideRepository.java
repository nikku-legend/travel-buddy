package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.Guide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GuideRepository extends JpaRepository<Guide, Long> {

    List<Guide> findByIsActiveTrue();

    List<Guide> findByState_StateIdAndIsActiveTrue(Integer stateId);

    Optional<Guide> findByUser_UserId(Long userId);

    /*
     * Locks the guide row for the rest of the transaction.
     *
     * <p>Used before checking whether a guide is free on a date.
     * Without the lock, two trip checkouts reaching the same guide
     * for the same day both read "free" and both book it. The unique
     * index on (guide, date) is the backstop; this is what stops
     * it being reached routinely, by turning a lost update into
     * a clean refusal.
     */
    @Query("SELECT g FROM Guide g WHERE g.guideId = :guideId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Guide> findByIdForUpdate(
            @Param("guideId") Long guideId
    );

    @Query("SELECT g FROM Guide g LEFT JOIN FETCH g.languages WHERE g.guideId = :guideId")
    Optional<Guide> findByIdWithLanguages(@Param("guideId") Long guideId);

    @Query("SELECT DISTINCT g FROM Guide g LEFT JOIN FETCH g.languages WHERE g.isActive = true")
    List<Guide> findAllActiveWithLanguages();
}
