package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomTypeRepository
        extends JpaRepository<RoomType, Long> {

    /**
     * How many room types a property has.
     *
     * <p>Used by the property approval workflow to refuse submitting a
     * listing that has nothing bookable yet.
     */
    long countByProperty_PropertyId(Long propertyId);

    List<RoomType> findByProperty_PropertyIdAndActiveTrueOrderByBasePriceAsc(
            Long propertyId
    );

    /**
     * All room types for a property, active or not, so the partner
     * portal can show deactivated rooms instead of silently hiding
     * them.
     */
    List<RoomType> findByProperty_PropertyIdOrderByCategoryNameAsc(
            Long propertyId
    );

    /**
     * A room type scoped to a property.
     *
     * <p>Scoping by property is what stops a partner editing another
     * partner's room by guessing its ID.
     */
    Optional<RoomType> findByRoomTypeIdAndProperty_PropertyId(
            Long roomTypeId,
            Long propertyId
    );

    /**
     * Duplicate category guard, so a property cannot list two rooms
     * with the same name.
     */
    boolean existsByProperty_PropertyIdAndCategoryNameIgnoreCase(
            Long propertyId,
            String categoryName
    );

    /**
     * Rooms sharing a category on the same property, excluding one.
     *
     * <p>Used to refuse a rename that would collide with another room.
     */
    @Query("""
            SELECT room
            FROM RoomType room
            WHERE room.property.propertyId = :propertyId
              AND room.roomTypeId <> :roomTypeId
              AND LOWER(room.categoryName) = LOWER(:categoryName)
            """)
    List<RoomType> findOthersWithSameCategory(
            @Param("propertyId") Long propertyId,
            @Param("roomTypeId") Long roomTypeId,
            @Param("categoryName") String categoryName
    );
}