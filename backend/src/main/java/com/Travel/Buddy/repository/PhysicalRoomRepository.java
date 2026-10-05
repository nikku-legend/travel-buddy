package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.PhysicalRoom;
import com.Travel.Buddy.entity.PhysicalRoomStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PhysicalRoomRepository
        extends JpaRepository<PhysicalRoom, Long> {

    List<PhysicalRoom> findByProperty_PropertyIdOrderByRoomNumberAsc(
            Long propertyId
    );

    List<PhysicalRoom> findByProperty_PropertyIdAndRoomType_RoomTypeIdOrderByRoomNumberAsc(
            Long propertyId,
            Long roomTypeId
    );

    long countByProperty_PropertyId(Long propertyId);

    long countByProperty_PropertyIdAndStatus(
            Long propertyId,
            PhysicalRoomStatus status
    );

    Optional<PhysicalRoom> findByProperty_PropertyIdAndRoomNumberIgnoreCase(
            Long propertyId,
            String roomNumber
    );

    /**
     * Locks the physical room row.
     *
     * <p>MySQL cannot express "no two stays overlap" as a constraint,
     * so competing assignments to the SAME room are serialised here
     * instead. Without this lock two front-desk terminals could each
     * read "room 101 is free" and both assign it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT room
            FROM PhysicalRoom room
            WHERE room.physicalRoomId = :physicalRoomId
            """)
    Optional<PhysicalRoom> findByIdForUpdate(
            @Param("physicalRoomId") Long physicalRoomId
    );

    Optional<PhysicalRoom> findByPhysicalRoomIdAndProperty_PropertyId(
            Long physicalRoomId,
            Long propertyId
    );
}