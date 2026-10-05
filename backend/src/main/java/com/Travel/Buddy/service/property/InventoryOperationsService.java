package com.Travel.Buddy.service.property;

import com.Travel.Buddy.dto.inventory.InventoryNightResponse;
import com.Travel.Buddy.dto.inventory.RoomBlockRequest;
import com.Travel.Buddy.dto.inventory.RoomBlockResponse;
import com.Travel.Buddy.entity.Property;
import com.Travel.Buddy.entity.RoomBlock;
import com.Travel.Buddy.entity.RoomBlockReason;
import com.Travel.Buddy.entity.RoomInventoryDaily;
import com.Travel.Buddy.entity.RoomType;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.PropertyRepository;
import com.Travel.Buddy.repository.RoomBlockRepository;
import com.Travel.Buddy.repository.RoomInventoryRepository;
import com.Travel.Buddy.repository.RoomTypeRepository;
import com.Travel.Buddy.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Partner inventory operations: the calendar and blocking. (FR-21)
 *
 * <p>Before this, a partner's only way to take rooms out of service
 * was to shrink {@code total_inventory}, which silently rewrites the
 * property's real size and fights the booking flow. Blocks are now a
 * third quantity alongside total and reserved, so the database still
 * enforces
 * {@code available + reserved + blocked = total}.
 *
 * <p>The rule that matters most: <strong>a block can never take
 * inventory away from a guest who has already paid.</strong> Blocking
 * is refused when the requested rooms exceed what is genuinely free
 * on that night.
 */
@Service
public class InventoryOperationsService {

    /** Guard against an accidental full-horizon block. */
    private static final int MAX_BLOCK_RANGE_DAYS = 365;

    private final RoomBlockRepository roomBlockRepository;

    private final RoomInventoryRepository roomInventoryRepository;

    private final RoomTypeRepository roomTypeRepository;

    private final PropertyRepository propertyRepository;

    private final UserRepository userRepository;

    public InventoryOperationsService(
            RoomBlockRepository roomBlockRepository,
            RoomInventoryRepository roomInventoryRepository,
            RoomTypeRepository roomTypeRepository,
            PropertyRepository propertyRepository,
            UserRepository userRepository
    ) {
        this.roomBlockRepository = roomBlockRepository;
        this.roomInventoryRepository = roomInventoryRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
    }

    /* ============================================================
     * CALENDAR
     * ============================================================ */

    /**
     * Nightly figures for one room type across a date window.
     *
     * <p>Returns every night in the window, including ones with no
     * inventory row, so a partner can see a gap rather than having it
     * silently omitted from the calendar.
     */
    @Transactional(readOnly = true)
    public List<InventoryNightResponse> calendar(
            Long partnerId,
            Long propertyId,
            Long roomTypeId,
            LocalDate from,
            LocalDate to
    ) {

        RoomType roomType =
                requireOwnedRoom(partnerId, propertyId, roomTypeId);

        LocalDate start =
                from == null
                        ? LocalDate.now()
                        : from;

        LocalDate end =
                to == null
                        ? start.plusDays(29)
                        : to;

        if (end.isBefore(start)) {
            throw PartnerApplicationException.badRequest(
                    "End date must not be before the start date"
            );
        }

        if (start.plusDays(MAX_BLOCK_RANGE_DAYS)
                .isBefore(end)) {

            throw PartnerApplicationException.badRequest(
                    "A calendar view is limited to "
                            + MAX_BLOCK_RANGE_DAYS + " days"
            );
        }

        Map<LocalDate, RoomInventoryDaily> inventoryByDate =
                new HashMap<>();

        for (RoomInventoryDaily inventory :
                roomInventoryRepository.findByRoomTypeAndDateRange(
                        roomType.getRoomTypeId(),
                        start,
                        end
                )) {

            inventoryByDate.put(
                    inventory.getInventoryDate(),
                    inventory
            );
        }

        Map<LocalDate, RoomBlock> blocksByDate = new HashMap<>();

        for (RoomBlock block :
                roomBlockRepository.findByRoomTypeAndDateRange(
                        roomType.getRoomTypeId(),
                        start,
                        end
                )) {

            if (block.isActive()) {
                blocksByDate.put(block.getBlockedDate(), block);
            }
        }

        List<InventoryNightResponse> nights = new ArrayList<>();

        for (LocalDate date = start;
             !date.isAfter(end);
             date = date.plusDays(1)) {

            RoomInventoryDaily inventory =
                    inventoryByDate.get(date);

            RoomBlock block = blocksByDate.get(date);

            nights.add(
                    new InventoryNightResponse(
                            date,
                            inventory == null
                                    ? 0
                                    : value(inventory.getTotalInventory()),
                            inventory == null
                                    ? 0
                                    : value(inventory.getReservedRooms()),
                            inventory == null
                                    ? 0
                                    : value(inventory.getBlockedRooms()),
                            inventory == null
                                    ? 0
                                    : value(inventory.getAvailableInventory()),
                            block == null
                                    ? null
                                    : block.getReason(),
                            block == null
                                    ? null
                                    : block.getNotes()
                    )
            );
        }

        return nights;
    }

    @Transactional(readOnly = true)
    public List<RoomBlockResponse> listBlocks(
            Long partnerId
    ) {
        return roomBlockRepository.findByPartner(partnerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /* ============================================================
     * BLOCKING
     * ============================================================ */

    /**
     * Takes rooms out of service for a date range.
     *
     * <p>The range is applied as one all-or-nothing transaction. A
     * partial block would leave the partner with a closure they did
     * not intend and inventory they cannot explain.
     *
     * @return one entry per night actually blocked
     */
    @Transactional
    public List<RoomBlockResponse> block(
            Long partnerId,
            Long propertyId,
            Long roomTypeId,
            RoomBlockRequest request
    ) {

        RoomType roomType =
                requireOwnedRoom(partnerId, propertyId, roomTypeId);

        if (roomTypeRequestOutOfRange(request)) {

            throw PartnerApplicationException.badRequest(
                    "End date must not be before the start date"
            );
        }

        if (request.fromDate()
                .plusDays(MAX_BLOCK_RANGE_DAYS)
                .isBefore(request.endDate())) {

            throw PartnerApplicationException.badRequest(
                    "A single block is limited to "
                            + MAX_BLOCK_RANGE_DAYS + " days"
            );
        }

        int nights =
                (int) java.time.temporal.ChronoUnit.DAYS.between(
                        request.fromDate(),
                        request.endDate()
                ) + 1;

        if (nights > MAX_BLOCK_RANGE_DAYS) {

            throw PartnerApplicationException.badRequest(
                    "A single block is limited to "
                            + MAX_BLOCK_RANGE_DAYS + " days"
            );
        }

        User partner = userRepository.findById(partnerId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Partner not found"
                        )
                );

        RoomBlockReason reason =
                request.reason() == null
                        ? RoomBlockReason.MAINTENANCE
                        : request.reason();

        List<RoomBlockResponse> created = new ArrayList<>();

        for (LocalDate date = request.fromDate();
             !date.isAfter(request.endDate());
             date = date.plusDays(1)) {

            created.add(
                    blockSingleNight(
                            partner,
                            roomType,
                            date,
                            request.rooms(),
                            reason,
                            request.notes()
                    )
            );
        }

        return created;
    }

    private RoomBlockResponse blockSingleNight(
            User partner,
            RoomType roomType,
            LocalDate date,
            int rooms,
            RoomBlockReason reason,
            String notes
    ) {
        RoomInventoryDaily inventory =
                roomInventoryRepository
                        .findByRoomType_RoomTypeIdAndInventoryDate(
                                roomType.getRoomTypeId(),
                                date
                        )
                        .orElseThrow(() ->
                                PartnerApplicationException.badRequest(
                                        "No inventory exists for " + date
                                )
                        );

        RoomBlock existing =
                roomBlockRepository.findActiveBlock(
                        roomType.getRoomTypeId(),
                        date
                ).orElse(null);

        if (existing != null) {

            /*
             * One live block per room type per night, so a repeated
             * request extends the existing block rather than creating
             * a second row that would double count.
             */
            if (existing.getRoomsBlocked() >= rooms) {

                throw PartnerApplicationException.conflict(
                        "This night already has a block of "
                                + existing.getRoomsBlocked()
                                + " room(s)"
                );
            }

            int additional = rooms - existing.getRoomsBlocked();

            applyBlock(inventory, additional);

            existing.setRoomsBlocked(rooms);

            if (notes != null && !notes.isBlank()) {
                existing.setNotes(notes.trim());
            }

            return toResponse(
                    roomBlockRepository.save(existing)
            );
        }

        applyBlock(inventory, rooms);

        RoomBlock block = new RoomBlock();

        block.setRoomType(roomType);
        block.setBlockedDate(date);
        block.setRoomsBlocked(rooms);
        block.setReason(reason);
        block.setNotes(
                notes == null || notes.isBlank()
                        ? null
                        : notes.trim()
        );
        block.setCreatedBy(partner);

        return toResponse(roomBlockRepository.save(block));
    }

    private void applyBlock(
            RoomInventoryDaily inventory,
            int rooms
    ) {
        try {

            inventory.blockRooms(rooms);
            roomInventoryRepository.save(inventory);

        } catch (IllegalStateException exception) {

            throw PartnerApplicationException.conflict(
                    exception.getMessage()
            );
        }
    }

    private boolean roomTypeRequestOutOfRange(
            RoomBlockRequest request
    ) {
        return request.endDate().isBefore(request.fromDate());
    }

    /**
     * Puts a night back on sale.
     *
     * <p>Blocks are released, never deleted, so the history of every
     * closure survives for settlement and dispute purposes.
     */
    @Transactional
    public RoomBlockResponse release(
            Long partnerId,
            Long blockId
    ) {
        RoomBlock block =
                roomBlockRepository.findByIdForUpdate(blockId)
                        .orElseThrow(() ->
                                PartnerApplicationException.notFound(
                                        "Block not found"
                                )
                        );

        requireOwnedRoom(
                partnerId,
                block.getRoomType()
                        .getProperty()
                        .getPropertyId(),
                block.getRoomType().getRoomTypeId()
        );

        if (!block.isActive()) {

            throw PartnerApplicationException.conflict(
                    "This block has already been released"
            );
        }

        RoomInventoryDaily inventory =
                roomInventoryRepository
                        .findByRoomType_RoomTypeIdAndInventoryDate(
                                block.getRoomType().getRoomTypeId(),
                                block.getBlockedDate()
                        )
                        .orElseThrow(() ->
                                PartnerApplicationException.notFound(
                                        "Inventory for this night no longer exists"
                                )
                        );

        inventory.unblockRooms(block.getRoomsBlocked());
        roomInventoryRepository.save(inventory);

        block.setReleasedAt(LocalDateTime.now());
        block.setReleasedBy(
                userRepository.findById(partnerId)
                        .orElse(null)
        );

        return toResponse(roomBlockRepository.save(block));
    }

    /* ============================================================
     * GUARDS
     * ============================================================ */

    private RoomType requireOwnedRoom(
            Long partnerId,
            Long propertyId,
            Long roomTypeId
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

    /* ============================================================
     * MAPPER
     * ============================================================ */

    private RoomBlockResponse toResponse(RoomBlock block) {
        return new RoomBlockResponse(
                block.getBlockId(),
                block.getRoomType()
                        .getProperty()
                        .getPropertyId(),
                block.getRoomType().getRoomTypeId(),
                block.getRoomType().getCategoryName(),
                block.getBlockedDate(),
                block.getRoomsBlocked(),
                block.getReason(),
                block.getNotes(),
                block.getCreatedBy() == null
                        ? null
                        : block.getCreatedBy().getFullName(),
                block.getCreatedAt(),
                block.isActive(),
                block.getReleasedAt()
        );
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }
}
