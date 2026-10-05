package com.Travel.Buddy.service.property;

import com.Travel.Buddy.dto.inventory.InventoryNightResponse;
import com.Travel.Buddy.dto.inventory.RoomBlockRequest;
import com.Travel.Buddy.dto.inventory.RoomBlockResponse;
import com.Travel.Buddy.dto.property.PropertyUpsertRequest;
import com.Travel.Buddy.dto.room.RoomTypeUpsertRequest;
import com.Travel.Buddy.entity.Country;
import com.Travel.Buddy.entity.PropertyType;
import com.Travel.Buddy.entity.RegionZone;
import com.Travel.Buddy.entity.Role;
import com.Travel.Buddy.entity.RoomBlockReason;
import com.Travel.Buddy.entity.RoomInventoryDaily;
import com.Travel.Buddy.entity.State;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.CountryRepository;
import com.Travel.Buddy.repository.RoomBlockRepository;
import com.Travel.Buddy.repository.RoomInventoryRepository;
import com.Travel.Buddy.repository.StateRepository;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.partner.RoleService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Partner inventory operations. (FR-21)
 *
 * <p>The behaviour that carries real risk is the interaction between
 * blocks and existing bookings: a partner must never be able to
 * withdraw inventory a guest has already paid for. These tests pin
 * that rule down, plus the arithmetic invariant the database
 * enforces.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Partner inventory operations (FR-21)")
class InventoryOperationsServiceTest {

    @Autowired
    private InventoryOperationsService service;

    @Autowired
    private PropertyApprovalService propertyService;

    @Autowired
    private RoomTypeService roomTypeService;

    @Autowired
    private RoomBlockRepository blockRepository;

    @Autowired
    private RoomInventoryRepository inventoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private StateRepository stateRepository;

    @Autowired
    private CountryRepository countryRepository;

    private User partner;

    private User other;

    private Integer stateId;

    private Long propertyId;

    private Long roomTypeId;

    @BeforeEach
    void setUp() {
        User admin = createUser("inv-admin@test.travelbuddy");
        roleService.grantBaselineTravelerRole(admin);
        roleService.grant(admin, Role.ROLE_SUPER_ADMIN, null);

        partner = createUser("inv-partner@test.travelbuddy");
        roleService.grantBaselineTravelerRole(partner);
        roleService.grant(partner, Role.ROLE_HOTEL_PARTNER, admin);

        other = createUser("inv-other@test.travelbuddy");
        roleService.grantBaselineTravelerRole(other);
        roleService.grant(other, Role.ROLE_HOTEL_PARTNER, admin);

        if (stateId == null) {
            Country country = new Country();
            country.setName("Testland");
            country.setIsoCode("TL");
            country = countryRepository.save(country);

            State state = new State();
            state.setName("Test State");
            state.setCountry(country);
            state.setRegionZone(RegionZone.EAST);
            state = stateRepository.save(state);

            stateId = state.getStateId();
        }

        propertyId = propertyService
                .create(
                        partner,
                        new PropertyUpsertRequest(
                                "Inventory Hotel",
                                PropertyType.HOTEL,
                                stateId,
                                "Address",
                                "Description",
                                new BigDecimal("19.8"),
                                new BigDecimal("85.7")
                        )
                )
                .propertyId();

        roomTypeId = roomTypeService.create(
                partner.getUserId(),
                propertyId,
                new RoomTypeUpsertRequest(
                        "Deluxe",
                        2,
                        new BigDecimal("2500.00"),
                        "INR",
                        4
                )
        ).roomTypeId();
    }

    private User createUser(String email) {
        User user = new User();
        user.setFullName("Test User");
        user.setEmail(email);
        user.setPasswordHash("{noop}password");

        return userRepository.save(user);
    }

    private RoomBlockRequest blockRequest(
            LocalDate from,
            LocalDate to,
            int rooms
    ) {
        return new RoomBlockRequest(
                from,
                to,
                rooms,
                RoomBlockReason.MAINTENANCE,
                "Air conditioning repair"
        );
    }

    private RoomInventoryDaily inventoryOn(LocalDate date) {
        return inventoryRepository
                .findByRoomType_RoomTypeIdAndInventoryDate(
                        roomTypeId,
                        date
                )
                .orElseThrow();
    }

    @Test
    @DisplayName("Blocking reduces availability but never touches reserved")
    void blockReducesAvailability() {
        LocalDate date = LocalDate.now().plusDays(5);

        List<RoomBlockResponse> blocked = service.block(
                partner.getUserId(),
                propertyId,
                roomTypeId,
                blockRequest(date, date, 2)
        );

        assertEquals(1, blocked.size());
        assertTrue(blocked.get(0).active());

        RoomInventoryDaily inventory = inventoryOn(date);

        assertEquals(4, inventory.getTotalInventory());
        assertEquals(2, inventory.getBlockedRooms());
        assertEquals(0, inventory.getReservedRooms());
        assertEquals(2, inventory.getAvailableInventory());
    }

    @Test
    @DisplayName("Blocking cannot take inventory away from a confirmed booking")
    void blockCannotStealBookedRooms() {
        LocalDate date = LocalDate.now().plusDays(6);

        /* A guest books 3 of the 4 rooms. */
        RoomInventoryDaily inventory = inventoryOn(date);
        inventory.reserveRooms(3);
        inventoryRepository.save(inventory);

        assertEquals(1, inventoryOn(date).getAvailableInventory());

        /*
         * The partner may only block the single remaining room.
         * Blocking 2 would silently cancel a paid booking.
         */
        assertThrows(
                PartnerApplicationException.class,
                () -> service.block(
                        partner.getUserId(),
                        propertyId,
                        roomTypeId,
                        blockRequest(date, date, 2)
                ),
                "Blocking must never take a booked room away"
        );

        service.block(
                partner.getUserId(),
                propertyId,
                roomTypeId,
                blockRequest(date, date, 1)
        );

        RoomInventoryDaily after = inventoryOn(date);

        assertEquals(3, after.getReservedRooms());
        assertEquals(1, after.getBlockedRooms());
        assertEquals(0, after.getAvailableInventory());
    }

    @Test
    @DisplayName("Releasing a block puts the rooms back on sale")
    void releaseRestoresAvailability() {
        LocalDate date = LocalDate.now().plusDays(7);

        List<RoomBlockResponse> blocked = service.block(
                partner.getUserId(),
                propertyId,
                roomTypeId,
                blockRequest(date, date, 3)
        );

        assertEquals(1, inventoryOn(date).getAvailableInventory());

        RoomBlockResponse released = service.release(
                partner.getUserId(),
                blocked.get(0).blockId()
        );

        assertFalse(released.active());
        assertNotNull(released.releasedAt());

        RoomInventoryDaily after = inventoryOn(date);

        assertEquals(0, after.getBlockedRooms());
        assertEquals(4, after.getAvailableInventory());

        assertTrue(
                blockRepository.existsById(blocked.get(0).blockId()),
                "A released block must be kept for history, not deleted"
        );
    }

    @Test
    @DisplayName("A range block applies to every night in the range")
    void rangeBlockCoversEveryNight() {
        LocalDate from = LocalDate.now().plusDays(10);
        LocalDate to = from.plusDays(2);

        List<RoomBlockResponse> blocked = service.block(
                partner.getUserId(),
                propertyId,
                roomTypeId,
                blockRequest(from, to, 4)
        );

        assertEquals(3, blocked.size());

        for (LocalDate date :
                List.of(from, from.plusDays(1), to)) {

            assertEquals(
                    4,
                    inventoryOn(date).getBlockedRooms(),
                    "Every night in the range must be blocked"
            );

            assertEquals(
                    0,
                    inventoryOn(date).getAvailableInventory()
            );
        }
    }

    @Test
    @DisplayName("The calendar reports total, reserved, blocked and available")
    void calendarExplainsEveryNight() {
        LocalDate from = LocalDate.now().plusDays(20);

        service.block(
                partner.getUserId(),
                propertyId,
                roomTypeId,
                blockRequest(from, from, 1)
        );

        RoomInventoryDaily reserved = inventoryOn(from);
        reserved.reserveRooms(2);
        inventoryRepository.save(reserved);

        List<InventoryNightResponse> nights = service.calendar(
                partner.getUserId(),
                propertyId,
                roomTypeId,
                from,
                from.plusDays(1)
        );

        assertEquals(2, nights.size());

        InventoryNightResponse first = nights.get(0);

        assertEquals(4, first.total());
        assertEquals(2, first.reserved());
        assertEquals(1, first.blocked());
        assertEquals(1, first.available());
        assertEquals(
                RoomBlockReason.MAINTENANCE,
                first.blockReason()
        );
        assertEquals("Air conditioning repair", first.blockNotes());
        assertTrue(first.isSellable());

        InventoryNightResponse second = nights.get(1);

        assertEquals(4, second.available());
        assertTrue(second.isSellable());
        assertNull(second.blockReason());
    }

    @Test
    @DisplayName("A partner cannot block or release another partner's rooms")
    void ownershipIsEnforced() {
        LocalDate date = LocalDate.now().plusDays(30);

        assertThrows(
                PartnerApplicationException.class,
                () -> service.block(
                        other.getUserId(),
                        propertyId,
                        roomTypeId,
                        blockRequest(date, date, 1)
                )
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> service.calendar(
                        other.getUserId(),
                        propertyId,
                        roomTypeId,
                        date,
                        date
                )
        );
    }

    @Test
    @DisplayName("A date range ending before it starts is refused")
    void invertedRangeRefused() {
        LocalDate date = LocalDate.now().plusDays(40);

        assertThrows(
                PartnerApplicationException.class,
                () -> service.block(
                        partner.getUserId(),
                        propertyId,
                        roomTypeId,
                        blockRequest(date, date.minusDays(3), 1)
                )
        );
    }

    @Test
    @DisplayName("Releasing an already released block is refused")
    void doubleReleaseRefused() {
        LocalDate date = LocalDate.now().plusDays(50);

        List<RoomBlockResponse> blocked = service.block(
                partner.getUserId(),
                propertyId,
                roomTypeId,
                blockRequest(date, date, 1)
        );

        service.release(
                partner.getUserId(),
                blocked.get(0).blockId()
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> service.release(
                        partner.getUserId(),
                        blocked.get(0).blockId()
                )
        );
    }
}
