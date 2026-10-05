package com.Travel.Buddy.service.property;

import com.Travel.Buddy.dto.property.PropertyDecisionRequest;
import com.Travel.Buddy.dto.property.PropertyUpsertRequest;
import com.Travel.Buddy.dto.room.PartnerRoomTypeResponse;
import com.Travel.Buddy.dto.room.RoomTypeUpsertRequest;
import com.Travel.Buddy.entity.Country;
import com.Travel.Buddy.entity.Property;
import com.Travel.Buddy.entity.PropertyStatus;
import com.Travel.Buddy.entity.PropertyType;
import com.Travel.Buddy.entity.RegionZone;
import com.Travel.Buddy.entity.Role;
import com.Travel.Buddy.entity.RoomInventoryDaily;
import com.Travel.Buddy.entity.State;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.CountryRepository;
import com.Travel.Buddy.repository.PropertyRepository;
import com.Travel.Buddy.repository.RoomInventoryRepository;
import com.Travel.Buddy.repository.RoomTypeRepository;
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
 * Room type management. (FR-05)
 *
 * <p>FR-05 had no endpoints at all, which made FR-20 unusable: a partner
 * could create a property but could never add the room that submission
 * requires. These tests cover the flow that unblocks that, plus the
 * inventory generation that stops a new room being silently
 * unbookable.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Room type management (FR-05)")
class RoomTypeServiceTest {

    @Autowired
    private RoomTypeService service;

    @Autowired
    private PropertyApprovalService propertyService;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomInventoryRepository roomInventoryRepository;

    @Autowired
    private PropertyRepository propertyRepository;

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

    @BeforeEach
    void setUp() {
        User admin = createUser("rooms-admin@test.travelbuddy");
        roleService.grantBaselineTravelerRole(admin);
        roleService.grant(admin, Role.ROLE_SUPER_ADMIN, null);

        partner = createUser("rooms-partner@test.travelbuddy");
        roleService.grantBaselineTravelerRole(partner);
        roleService.grant(partner, Role.ROLE_HOTEL_PARTNER, admin);

        other = createUser("rooms-other@test.travelbuddy");
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
    }

    private User createUser(String email) {
        User user = new User();
        user.setFullName("Test User");
        user.setEmail(email);
        user.setPasswordHash("{noop}password");

        return userRepository.save(user);
    }

    private Long newDraftProperty() {
        return propertyService
                .create(
                        partner,
                        new PropertyUpsertRequest(
                                "Room Test Hotel",
                                PropertyType.HOTEL,
                                stateId,
                                "Address",
                                "Description",
                                new BigDecimal("19.8"),
                                new BigDecimal("85.7")
                        )
                )
                .propertyId();
    }

    private RoomTypeUpsertRequest room(String category, int total) {
        return new RoomTypeUpsertRequest(
                category,
                2,
                new BigDecimal("2500.00"),
                "INR",
                total
        );
    }

    @Test
    @DisplayName("Creating a room auto-generates daily inventory so it is bookable")
    void creatingRoomGeneratesInventory() {
        Long propertyId = newDraftProperty();

        PartnerRoomTypeResponse created =
                service.create(
                        partner.getUserId(),
                        propertyId,
                        room("Deluxe", 4)
                );

        assertTrue(created.hasAvailability(),
                "A new room must have inventory or it is silently unbookable");

        assertEquals(366, created.inventoryDays());

        RoomInventoryDaily firstDay =
                roomInventoryRepository
                        .findByRoomType_RoomTypeIdOrderByInventoryDateAsc(
                                created.roomTypeId()
                        ).get(0);

        assertEquals(LocalDate.now(), firstDay.getInventoryDate());
        assertEquals(4, firstDay.getTotalInventory());
        assertEquals(4, firstDay.getAvailableInventory());
        assertEquals(0, firstDay.getReservedRooms());
    }

    @Test
    @DisplayName("A property with a room can now be submitted for approval")
    void roomUnblocksPropertySubmission() {
        Long propertyId = newDraftProperty();

        /*
         * Before FR-05 existed this was unreachable: submission needs at
         * least one room and there was no way to add one.
         */
        assertThrows(
                PartnerApplicationException.class,
                () -> propertyService.submit(
                        partner.getUserId(),
                        propertyId
                )
        );

        service.create(
                partner.getUserId(),
                propertyId,
                room("Deluxe", 4)
        );

        var submitted = propertyService.submit(
                partner.getUserId(),
                propertyId
        );

        assertEquals(
                PropertyStatus.PENDING_APPROVAL,
                submitted.status()
        );
    }

    @Test
    @DisplayName("Duplicate room categories on one property are refused")
    void duplicateCategoryRefused() {
        Long propertyId = newDraftProperty();

        service.create(
                partner.getUserId(),
                propertyId,
                room("Deluxe", 4)
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> service.create(
                        partner.getUserId(),
                        propertyId,
                        room("deluxe", 2)
                ),
                "Case-insensitive duplicates must be refused"
        );
    }

    @Test
    @DisplayName("A partner cannot add or edit rooms on someone else's property")
    void ownershipIsEnforced() {
        Long propertyId = newDraftProperty();

        assertThrows(
                PartnerApplicationException.class,
                () -> service.create(
                        other.getUserId(),
                        propertyId,
                        room("Deluxe", 4)
                )
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> service.listForProperty(
                        other.getUserId(),
                        propertyId
                )
        );
    }

    @Test
    @DisplayName("A room cannot be edited through a different property")
    void roomIsScopedToItsProperty() {
        Long propertyA = newDraftProperty();

        Long propertyB = propertyService
                .create(
                        other,
                        new PropertyUpsertRequest(
                                "Other Hotel",
                                PropertyType.HOTEL,
                                stateId,
                                "Address",
                                "Description",
                                new BigDecimal("19.8"),
                                new BigDecimal("85.7")
                        )
                )
                .propertyId();

        Long roomB = service.create(
                other.getUserId(),
                propertyB,
                room("Suite", 2)
        ).roomTypeId();

        assertThrows(
                PartnerApplicationException.class,
                () -> service.update(
                        partner.getUserId(),
                        propertyA,
                        roomB,
                        room("Stolen", 1)
                ),
                "A room must not be editable through a different property"
        );
    }

    @Test
    @DisplayName("Resizing a room resyncs future inventory totals")
    void resizingRoomResyncsInventory() {
        Long propertyId = newDraftProperty();

        Long roomId = service.create(
                partner.getUserId(),
                propertyId,
                room("Deluxe", 10)
        ).roomTypeId();

        service.update(
                partner.getUserId(),
                propertyId,
                roomId,
                room("Deluxe", 6)
        );

        RoomInventoryDaily firstDay =
                roomInventoryRepository
                        .findByRoomType_RoomTypeIdOrderByInventoryDateAsc(
                                roomId
                        ).get(0);

        assertEquals(6, firstDay.getTotalInventory());
        assertEquals(6, firstDay.getAvailableInventory());
    }

    @Test
    @DisplayName("Deactivating a room zeroes availability but keeps the room")
    void deactivateKeepsRoomButZerosAvailability() {
        Long propertyId = newDraftProperty();

        Long roomId = service.create(
                partner.getUserId(),
                propertyId,
                room("Deluxe", 5)
        ).roomTypeId();

        PartnerRoomTypeResponse result = service.deactivate(
                partner.getUserId(),
                propertyId,
                roomId
        );

        assertFalse(result.active());
        assertNotNull(result.cannotEditReason());

        assertTrue(
                roomTypeRepository.existsById(roomId),
                "Deactivation must not delete the room"
        );

        RoomInventoryDaily firstDay =
                roomInventoryRepository
                        .findByRoomType_RoomTypeIdOrderByInventoryDateAsc(
                                roomId
                        ).get(0);

        assertEquals(0, firstDay.getAvailableInventory());
    }

    @Test
    @DisplayName("Rooms are locked once the property is live")
    void roomsLockedWhenPropertyIsLive() {
        Long propertyId = newDraftProperty();

        Long roomId = service.create(
                partner.getUserId(),
                propertyId,
                room("Deluxe", 5)
        ).roomTypeId();

        User admin = userRepository
                .findByEmail("rooms-admin@test.travelbuddy")
                .orElseThrow();

        /* A property can only be approved after it is submitted. */
        propertyService.submit(
                partner.getUserId(),
                propertyId
        );

        propertyService.decide(
                admin.getUserId(),
                propertyId,
                new PropertyDecisionRequest(true, null)
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> service.create(
                        partner.getUserId(),
                        propertyId,
                        room("Extra", 1)
                ),
                "A live property must not gain new rooms"
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> service.update(
                        partner.getUserId(),
                        propertyId,
                        roomId,
                        room("Deluxe", 9)
                )
        );
    }
}
