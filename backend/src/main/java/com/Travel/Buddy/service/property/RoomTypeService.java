package com.Travel.Buddy.service.property;

import com.Travel.Buddy.dto.room.PartnerRoomTypeResponse;
import com.Travel.Buddy.dto.room.RoomTypeUpsertRequest;
import com.Travel.Buddy.entity.Property;
import com.Travel.Buddy.entity.RoomInventoryDaily;
import com.Travel.Buddy.entity.RoomType;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.PropertyRepository;
import com.Travel.Buddy.repository.RoomInventoryRepository;
import com.Travel.Buddy.repository.RoomTypeRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Room type management for hotel partners. (FR-05)
 *
 * <p>This is the missing link that made property approval unusable: a
 * partner could create a property but had no way to add a room, while
 * submission requires at least one.
 *
 * <p>Creating or resizing a room also (re)generates daily inventory,
 * because availability checks read {@code room_inventory_daily} for
 * every requested night. A room with no inventory rows is silently
 * unbookable, which looks identical to "no availability" in the UI.
 */
@Service
public class RoomTypeService {

    /**
     * How far ahead daily inventory is generated.
     *
     * <p>Matches the seed migration so a freshly created room behaves
     * exactly like a seeded one.
     */
    private final int inventoryHorizonDays;

    private final RoomTypeRepository roomTypeRepository;

    private final RoomInventoryRepository roomInventoryRepository;

    private final PropertyRepository propertyRepository;

    public RoomTypeService(
            @Value("${app.inventory.horizon-days:365}")
            int inventoryHorizonDays,
            RoomTypeRepository roomTypeRepository,
            RoomInventoryRepository roomInventoryRepository,
            PropertyRepository propertyRepository
    ) {
        this.inventoryHorizonDays = inventoryHorizonDays;
        this.roomTypeRepository = roomTypeRepository;
        this.roomInventoryRepository = roomInventoryRepository;
        this.propertyRepository = propertyRepository;
    }

    /* ============================================================
     * READS
     * ============================================================ */

    @Transactional(readOnly = true)
    public List<PartnerRoomTypeResponse> listForProperty(
            Long partnerId,
            Long propertyId
    ) {
        requireOwnedProperty(partnerId, propertyId);

        return roomTypeRepository
                .findByProperty_PropertyIdOrderByCategoryNameAsc(
                        propertyId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /* ============================================================
     * WRITES
     * ============================================================ */

    @Transactional
    public PartnerRoomTypeResponse create(
            Long partnerId,
            Long propertyId,
            RoomTypeUpsertRequest request
    ) {
        Property property =
                requireOwnedProperty(partnerId, propertyId);

        requireEditableProperty(property);

        String category =
                request.categoryName().trim();

        if (roomTypeRepository
                .existsByProperty_PropertyIdAndCategoryNameIgnoreCase(
                        propertyId,
                        category
                )) {

            throw PartnerApplicationException.conflict(
                    "A room type named '" + category
                            + "' already exists on this property"
            );
        }

        RoomType roomType = new RoomType();

        roomType.setProperty(property);
        roomType.setCategoryName(category);
        roomType.setMaxOccupancy(request.maxOccupancy());
        roomType.setBasePrice(request.basePrice());
        roomType.setCurrency(
                request.currency() == null
                        ? "INR"
                        : request.currency().trim().toUpperCase()
        );
        roomType.setTotalInventory(request.totalInventory());
        roomType.setActive(true);

        RoomType saved = roomTypeRepository.save(roomType);

        generateInventory(saved, request.totalInventory());

        return toResponse(saved);
    }

    @Transactional
    public PartnerRoomTypeResponse update(
            Long partnerId,
            Long propertyId,
            Long roomTypeId,
            RoomTypeUpsertRequest request
    ) {
        Property property =
                requireOwnedProperty(partnerId, propertyId);

        requireEditableProperty(property);

        RoomType roomType =
                requireOwnedRoom(propertyId, roomTypeId);

        String category =
                request.categoryName().trim();

        if (!roomTypeRepository
                .findOthersWithSameCategory(
                        propertyId,
                        roomTypeId,
                        category
                ).isEmpty()) {

            throw PartnerApplicationException.conflict(
                    "Another room on this property is already named '"
                            + category + "'"
            );
        }

        int previousInventory =
                roomType.getTotalInventory() == null
                        ? 0
                        : roomType.getTotalInventory();

        roomType.setCategoryName(category);
        roomType.setMaxOccupancy(request.maxOccupancy());
        roomType.setBasePrice(request.basePrice());

        if (request.currency() != null) {
            roomType.setCurrency(
                    request.currency().trim().toUpperCase()
            );
        }

        roomType.setTotalInventory(request.totalInventory());

        RoomType saved = roomTypeRepository.save(roomType);

        /*
         * Only resync when the physical count actually changed. Doing it
         * on every price edit would be wasteful, and doing it carelessly
         * could resurrect rooms a partner had deliberately blocked.
         */
        if (previousInventory != request.totalInventory()) {
            syncInventoryTotals(saved, request.totalInventory());
        }

        return toResponse(saved);
    }

    /**
     * Deactivates a room rather than deleting it.
     *
     * <p>Hard deletion would cascade away daily inventory that existing
     * bookings reference, and a room with bookings must never disappear
     * from history.
     */
    @Transactional
    public PartnerRoomTypeResponse deactivate(
            Long partnerId,
            Long propertyId,
            Long roomTypeId
    ) {
        Property property =
                requireOwnedProperty(partnerId, propertyId);

        requireEditableProperty(property);

        RoomType roomType =
                requireOwnedRoom(propertyId, roomTypeId);

        roomType.setActive(false);

        /*
         * A deactivated room must not be offered, so its availability
         * drops to zero. Already confirmed bookings keep their own
         * reservation and are unaffected.
         */
        for (RoomInventoryDaily inventory :
                roomInventoryRepository
                        .findByRoomType_RoomTypeIdOrderByInventoryDateAsc(
                                roomTypeId
                        )) {

            inventory.setReservedRooms(
                    inventory.getTotalInventory()
            );
            inventory.recalculateAvailableInventory();

            roomInventoryRepository.save(inventory);
        }

        return toResponse(roomTypeRepository.save(roomType));
    }

    /* ============================================================
     * INVENTORY
     * ============================================================ */

    /**
     * Creates daily inventory for the next {@code horizonDays} nights.
     *
     * <p>Existing days are left untouched, so re-running this after a
     * failure cannot wipe out reservations that already exist.
     */
    private void generateInventory(
            RoomType roomType,
            int totalInventory
    ) {
        LocalDate today = LocalDate.now();

        for (int offset = 0;
             offset <= inventoryHorizonDays;
             offset++) {

            LocalDate date =
                    today.plusDays(offset);

            if (roomInventoryRepository
                    .existsByRoomType_RoomTypeIdAndInventoryDate(
                            roomType.getRoomTypeId(),
                            date
                    )) {

                continue;
            }

            RoomInventoryDaily inventory =
                    new RoomInventoryDaily();

            inventory.setRoomType(roomType);
            inventory.setInventoryDate(date);
            inventory.setTotalInventory(totalInventory);
            inventory.setReservedRooms(0);
            inventory.setAvailableInventory(totalInventory);

            roomInventoryRepository.save(inventory);
        }
    }

    /**
     * Applies a changed physical room count to future days only.
     *
     * <p>Past and fully reserved days are skipped: shrinking a room from
     * 10 to 4 must not push an existing 3-room booking below the
     * inventory it already holds.
     */
    private void syncInventoryTotals(
            RoomType roomType,
            int totalInventory
    ) {
        LocalDate today = LocalDate.now();

        for (RoomInventoryDaily inventory :
                roomInventoryRepository
                        .findByRoomType_RoomTypeIdOrderByInventoryDateAsc(
                                roomType.getRoomTypeId()
                        )) {

            if (inventory.getInventoryDate()
                    .isBefore(today)) {

                continue;
            }

            int reserved =
                    inventory.getReservedRooms() == null
                            ? 0
                            : inventory.getReservedRooms();

            if (reserved > totalInventory) {

                /*
                 * Cannot shrink below what is already booked. The day
                 * is over-committed, so keep it at the reserved level
                 * rather than corrupting the consistency constraint.
                 */
                inventory.setTotalInventory(reserved);
            } else {

                inventory.setTotalInventory(totalInventory);
            }

            inventory.recalculateAvailableInventory();

            roomInventoryRepository.save(inventory);
        }
    }

    /* ============================================================
     * GUARDS
     * ============================================================ */

    /**
     * Loads a property and proves the caller owns it.
     *
     * <p>Ownership is checked on every call rather than trusted from the
     * request, so guessing another partner's property ID grants nothing.
     */
    private Property requireOwnedProperty(
            Long partnerId,
            Long propertyId
    ) {
        Property property =
                propertyRepository.findById(propertyId)
                        .orElseThrow(() ->
                                PartnerApplicationException.notFound(
                                        "Property not found"
                                )
                        );

        if (!property.getPartner()
                .getUserId()
                .equals(partnerId)) {

            throw PartnerApplicationException.forbidden(
                    "You do not own this property"
            );
        }

        return property;
    }

    private RoomType requireOwnedRoom(
            Long propertyId,
            Long roomTypeId
    ) {
        return roomTypeRepository
                .findByRoomTypeIdAndProperty_PropertyId(
                        roomTypeId,
                        propertyId
                )
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Room type not found on this property"
                        )
                );
    }

    /**
     * Rooms may only be changed while the property is still editable.
     *
     * <p>Freezing a live property's room definitions would silently
     * change what guests already booked against.
     */
    private void requireEditableProperty(Property property) {

        if (!property.getStatus().isEditableByPartner()) {

            throw PartnerApplicationException.conflict(
                    "Rooms cannot be changed while the property is "
                            + property.getStatus()
            );
        }
    }

    /* ============================================================
     * MAPPER
     * ============================================================ */

    private PartnerRoomTypeResponse toResponse(
            RoomType roomType
    ) {
        long inventoryDays =
                roomInventoryRepository
                        .countByRoomType_RoomTypeId(
                                roomType.getRoomTypeId()
                        );

        boolean propertyEditable =
                roomType.getProperty()
                        .getStatus()
                        .isEditableByPartner();

        return new PartnerRoomTypeResponse(
                roomType.getRoomTypeId(),
                roomType.getProperty().getPropertyId(),
                roomType.getProperty().getName(),
                roomType.getCategoryName(),
                roomType.getMaxOccupancy(),
                roomType.getBasePrice(),
                roomType.getCurrency(),
                roomType.getTotalInventory(),
                Boolean.TRUE.equals(roomType.getActive()),
                inventoryDays,
                inventoryDays > 0,
                propertyEditable
                        && Boolean.TRUE.equals(roomType.getActive()),
                roomTypeStatusReason(roomType, propertyEditable)
        );
    }

    private String roomTypeStatusReason(
            RoomType roomType,
            boolean propertyEditable
    ) {

        if (!propertyEditable) {
            return "The property is live, so its rooms are locked";
        }

        if (!Boolean.TRUE.equals(roomType.getActive())) {
            return "This room type is deactivated";
        }

        return null;
    }
}
