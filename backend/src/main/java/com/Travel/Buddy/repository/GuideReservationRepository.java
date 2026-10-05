package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.GuideReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GuideReservationRepository extends JpaRepository<GuideReservation, Long> {

    List<GuideReservation> findByGuide_GuideId(Long guideId);

    List<GuideReservation> findByBooking_BookingId(Long bookingId);

    boolean existsByGuide_GuideIdAndTourDate(Long guideId, LocalDate tourDate);

    /**
     * Locks the reservation for the rest of the transaction.
     *
     * <p>Used before a guide reports how far a tour got, and before
     * the booking behind it is settled. Without the lock, two requests
     * can both read CONFIRMED and both advance it, so the transition
     * rules this guards are not actually being enforced by the
     * database -- only by whichever request happened to arrive first
     * and still be holding the entity.
     */
    @Query("SELECT r FROM GuideReservation r WHERE r.guideReservationId = :reservationId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GuideReservation> findByIdForUpdate(
            @Param("reservationId") Long reservationId
    );
}
