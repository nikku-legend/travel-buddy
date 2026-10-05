package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.RoomStay;
import com.Travel.Buddy.entity.RoomStayStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomStayRepository
        extends JpaRepository<RoomStay, Long> {

    List<RoomStay> findByBooking_BookingIdOrderByStayIdAsc(
            Long bookingId
    );

    /**
     * Stays occupying a room across a date range.
     *
     * <p>Overlap test: {@code existing.check_in < requested.check_out
     * AND existing.check_out > requested.check_in}. Back-to-back
     * bookings, where one check-out equals the next check-in, are
     * correctly NOT an overlap.
     */
    @Query("""
            SELECT stay
            FROM RoomStay stay
            WHERE stay.physicalRoom.physicalRoomId = :physicalRoomId
              AND stay.status IN :statuses
              AND stay.checkInDate < :checkOut
              AND stay.checkOutDate > :checkIn
            """)
    List<RoomStay> findOverlapping(
            @Param("physicalRoomId") Long physicalRoomId,
            @Param("statuses") Collection<RoomStayStatus> statuses,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut
    );

    /**
     * Excludes a stay so re-assigning the same booking to the same
     * room is not treated as a self-conflict.
     */
    @Query("""
            SELECT stay
            FROM RoomStay stay
            WHERE stay.physicalRoom.physicalRoomId = :physicalRoomId
              AND stay.status IN :statuses
              AND stay.checkInDate < :checkOut
              AND stay.checkOutDate > :checkIn
              AND stay.stayId <> :excludeStayId
            """)
    List<RoomStay> findOverlappingExcluding(
            @Param("physicalRoomId") Long physicalRoomId,
            @Param("statuses") Collection<RoomStayStatus> statuses,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("excludeStayId") Long excludeStayId
    );

    Optional<RoomStay> findByBooking_BookingIdAndPhysicalRoom_PhysicalRoomId(
            Long bookingId,
            Long physicalRoomId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT stay
            FROM RoomStay stay
            WHERE stay.stayId = :stayId
            """)
    Optional<RoomStay> findByIdForUpdate(
            @Param("stayId") Long stayId
    );

    /**
     * Stays the front desk must act on today, scoped to a partner.
     */
    @Query("""
            SELECT stay
            FROM RoomStay stay
            WHERE stay.physicalRoom.property.partner.userId = :partnerId
              AND stay.status IN :statuses
            ORDER BY stay.checkInDate ASC
            """)
    List<RoomStay> findByPartnerAndStatuses(
            @Param("partnerId") Long partnerId,
            @Param("statuses") Collection<RoomStayStatus> statuses
    );

    List<RoomStay> findByBooking_BookingIdAndStatusIn(
            Long bookingId,
            Collection<RoomStayStatus> statuses
    );

    /**
     * Every stay for a set of bookings in one round trip, used to
     * assemble the partner's reservation list without an N+1.
     */
    List<RoomStay> findByBooking_BookingIdIn(
            Collection<Long> bookingIds
    );
}