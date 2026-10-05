package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.RoomInventoryDaily;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomInventoryRepository
        extends JpaRepository<RoomInventoryDaily, Long> {

    /*
     * ============================================================
     * BOOKING INVENTORY LOCK
     * ============================================================
     *
     * Used ONLY during actual booking creation.
     *
     * PESSIMISTIC_WRITE ensures that concurrent booking requests
     * cannot both reserve the same remaining inventory.
     *
     * Example:
     *
     * Available rooms = 1
     *
     * User A -> locks inventory
     * User B -> waits
     *
     * User A -> reserves room
     * User B -> sees available = 0
     *
     * Result:
     *
     * A = SUCCESS
     * B = REJECTED
     */

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT inventory
            FROM RoomInventoryDaily inventory
            WHERE inventory.roomType.roomTypeId = :roomTypeId
              AND inventory.inventoryDate >= :checkIn
              AND inventory.inventoryDate < :checkOut
            ORDER BY inventory.inventoryDate ASC
            """)
    List<RoomInventoryDaily> findForAvailability(
            @Param("roomTypeId")
            Long roomTypeId,

            @Param("checkIn")
            LocalDate checkIn,

            @Param("checkOut")
            LocalDate checkOut
    );


    /*
     * ============================================================
     * READ-ONLY AVAILABILITY
     * ============================================================
     *
     * Used when the traveler is simply checking availability.
     *
     * No write lock is acquired.
     */

    @Query("""
            SELECT inventory
            FROM RoomInventoryDaily inventory
            WHERE inventory.roomType.roomTypeId = :roomTypeId
              AND inventory.inventoryDate >= :checkIn
              AND inventory.inventoryDate < :checkOut
            ORDER BY inventory.inventoryDate ASC
            """)
    List<RoomInventoryDaily> findForAvailabilityReadOnly(
            @Param("roomTypeId")
            Long roomTypeId,

            @Param("checkIn")
            LocalDate checkIn,

            @Param("checkOut")
            LocalDate checkOut
    );


    /*
     * ============================================================
     * EXACT INVENTORY ROW
     * ============================================================
     */

    Optional<RoomInventoryDaily>
    findByRoomType_RoomTypeIdAndInventoryDate(
            Long roomTypeId,
            LocalDate inventoryDate
    );


    /*
     * ============================================================
     * INVENTORY EXISTENCE
     * ============================================================
     */

    boolean existsByRoomType_RoomTypeIdAndInventoryDate(
            Long roomTypeId,
            LocalDate inventoryDate
    );

    /**
     * How many nightly rows exist for a room.
     *
     * <p>Distinguishes a bookable room from one that was listed but
     * never given daily inventory, which otherwise both look like
     * "no availability" to a traveler.
     */
    long countByRoomType_RoomTypeId(Long roomTypeId);


    /*
     * ============================================================
     * ALL INVENTORY FOR ROOM TYPE
     * ============================================================
     */

    List<RoomInventoryDaily>
    findByRoomType_RoomTypeIdOrderByInventoryDateAsc(
            Long roomTypeId
    );


    /*
     * ============================================================
     * INVENTORY DATE RANGE
     * ============================================================
     */

    @Query("""
            SELECT inventory
            FROM RoomInventoryDaily inventory
            WHERE inventory.roomType.roomTypeId = :roomTypeId
              AND inventory.inventoryDate >= :startDate
              AND inventory.inventoryDate <= :endDate
            ORDER BY inventory.inventoryDate ASC
            """)
    List<RoomInventoryDaily> findByRoomTypeAndDateRange(
            @Param("roomTypeId")
            Long roomTypeId,

            @Param("startDate")
            LocalDate startDate,

            @Param("endDate")
            LocalDate endDate
    );
}