package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.RoomBlock;
import com.Travel.Buddy.entity.RoomType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomBlockRepository
        extends JpaRepository<RoomBlock, Long> {

    /**
     * The live block covering a specific night, if any.
     *
     * <p>{@code releasedAt IS NULL} distinguishes an active block from
     * released history that must not be double counted.
     */
    @Query("""
            SELECT block
            FROM RoomBlock block
            WHERE block.roomType.roomTypeId = :roomTypeId
              AND block.blockedDate = :date
              AND block.releasedAt IS NULL
            """)
    Optional<RoomBlock> findActiveBlock(
            @Param("roomTypeId") Long roomTypeId,
            @Param("date") LocalDate date
    );

    /**
     * Locks the block row so two concurrent requests cannot both
     * create or release the same night's block.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT block
            FROM RoomBlock block
            WHERE block.blockId = :blockId
            """)
    Optional<RoomBlock> findByIdForUpdate(
            @Param("blockId") Long blockId
    );

    /**
     * Blocks a partner owns, newest first.
     */
    @Query("""
            SELECT block
            FROM RoomBlock block
            WHERE block.roomType.property.partner.userId = :partnerId
            ORDER BY block.blockedDate DESC
            """)
    List<RoomBlock> findByPartner(
            @Param("partnerId") Long partnerId
    );

    /**
     * Blocks within a date window for one room type, used to render
     * the partner's calendar.
     */
    @Query("""
            SELECT block
            FROM RoomBlock block
            WHERE block.roomType.roomTypeId = :roomTypeId
              AND block.blockedDate BETWEEN :from AND :to
            ORDER BY block.blockedDate ASC
            """)
    List<RoomBlock> findByRoomTypeAndDateRange(
            @Param("roomTypeId") Long roomTypeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
