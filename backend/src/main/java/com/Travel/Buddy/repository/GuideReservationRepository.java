package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.GuideReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface GuideReservationRepository extends JpaRepository<GuideReservation, Long> {

    List<GuideReservation> findByGuide_GuideId(Long guideId);

    List<GuideReservation> findByBooking_BookingId(Long bookingId);

    boolean existsByGuide_GuideIdAndTourDate(Long guideId, LocalDate tourDate);
}
