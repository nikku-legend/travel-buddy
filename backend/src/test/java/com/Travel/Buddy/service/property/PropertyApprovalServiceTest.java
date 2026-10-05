package com.Travel.Buddy.service.property;

import com.Travel.Buddy.dto.property.PropertyDecisionRequest;
import com.Travel.Buddy.dto.property.PropertyUpsertRequest;
import com.Travel.Buddy.entity.Country;
import com.Travel.Buddy.entity.Property;
import com.Travel.Buddy.entity.PropertyStatus;
import com.Travel.Buddy.entity.PropertyType;
import com.Travel.Buddy.entity.RegionZone;
import com.Travel.Buddy.entity.Role;
import com.Travel.Buddy.entity.RoomType;
import com.Travel.Buddy.entity.State;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.CountryRepository;
import com.Travel.Buddy.repository.PropertyRepository;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property approval workflow. (FR-20)
 *
 * <p>These tests cover the marketplace-trust rule the blueprint calls
 * out explicitly: <em>"Partner submits property -&gt; PENDING_APPROVAL
 * -&gt; ADMIN REVIEW -&gt; APPROVED -&gt; PROPERTY LIVE"</em>. A hotel
 * must not be able to publish itself.
 *
 * <p>Runs on H2, so it validates business rules and state transitions,
 * not MySQL row-locking. Double-booking concurrency is a separate
 * concern and must be verified against real MySQL.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Property approval workflow (FR-20)")
class PropertyApprovalServiceTest {

    @Autowired
    private PropertyApprovalService service;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private StateRepository stateRepository;

    @Autowired
    private CountryRepository countryRepository;

    private User partner;

    private User admin;

    private Integer stateId;

    @BeforeEach
    void setUp() {
        admin = createUser("admin@test.travelbuddy");
        roleService.grantBaselineTravelerRole(admin);
        roleService.grant(admin, Role.ROLE_SUPER_ADMIN, null);

        partner = createUser("partner@test.travelbuddy");
        roleService.grantBaselineTravelerRole(partner);
        roleService.grant(partner, Role.ROLE_HOTEL_PARTNER, admin);

        if (stateId == null) {
            Country country = new Country();
            country.setName("Testland");
            country.setIsoCode("TL");
            country = countryRepository.save(country);

            /*
             * RegionZone is an enum, not a table, so a state just picks
             * a zone value.
             */
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

    private PropertyUpsertRequest request() {
        return new PropertyUpsertRequest(
                "Sea View Hotel",
                PropertyType.HOTEL,
                stateId,
                "Beach Road, Puri",
                "A lovely sea facing property.",
                new BigDecimal("19.8015"),
                new BigDecimal("85.7000")
        );
    }

    private void addRoom(Property property) {
        RoomType roomType = new RoomType();
        roomType.setProperty(property);
        roomType.setCategoryName("Deluxe");
        roomType.setMaxOccupancy(2);
        roomType.setBasePrice(new BigDecimal("2500.00"));
        roomType.setCurrency("INR");
        roomType.setTotalInventory(5);

        roomTypeRepository.save(roomType);
    }

    private boolean isPubliclyVisible(Long propertyId) {
        return propertyRepository
                .findByActiveTrueAndVerifiedTrueOrderByNameAsc()
                .stream()
                .anyMatch(p -> p.getPropertyId().equals(propertyId));
    }

    @Test
    @DisplayName("A newly created property is a private DRAFT, not live")
    void newPropertyIsDraftAndNotVisible() {
        var response = service.create(partner, request());

        assertEquals(PropertyStatus.DRAFT, response.status());
        assertFalse(response.isLive());

        Property stored = propertyRepository
                .findById(response.propertyId())
                .orElseThrow();

        /*
         * is_verified is the flag every public read query filters on, so
         * it must be false or the hotel would appear in search before
         * anyone reviewed it.
         */
        assertFalse(stored.getVerified());

        assertFalse(
                isPubliclyVisible(response.propertyId()),
                "A draft property must not appear in public listings"
        );
    }

    @Test
    @DisplayName("A property cannot be submitted for approval with no rooms")
    void cannotSubmitWithoutRooms() {
        var created = service.create(partner, request());

        assertThrows(
                PartnerApplicationException.class,
                () -> service.submit(
                        partner.getUserId(),
                        created.propertyId()
                )
        );
    }

    @Test
    @DisplayName("Approving a property makes it live and publicly visible")
    void approvalMakesPropertyLive() {
        var created = service.create(partner, request());

        addRoom(propertyRepository
                .findById(created.propertyId())
                .orElseThrow());

        service.submit(partner.getUserId(), created.propertyId());

        var decided = service.decide(
                admin.getUserId(),
                created.propertyId(),
                new PropertyDecisionRequest(true, null)
        );

        assertEquals(PropertyStatus.APPROVED, decided.status());
        assertTrue(decided.isLive());

        assertTrue(
                propertyRepository
                        .findById(created.propertyId())
                        .orElseThrow()
                        .getVerified()
        );

        assertTrue(
                isPubliclyVisible(created.propertyId()),
                "An approved property must appear in public listings"
        );
    }

    @Test
    @DisplayName("Rejecting requires a reason and keeps the property offline")
    void rejectionRequiresReasonAndStaysOffline() {
        var created = service.create(partner, request());

        addRoom(propertyRepository
                .findById(created.propertyId())
                .orElseThrow());

        service.submit(partner.getUserId(), created.propertyId());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.decide(
                        admin.getUserId(),
                        created.propertyId(),
                        new PropertyDecisionRequest(false, "  ")
                ),
                "A rejection without a reason must be refused"
        );

        var rejected = service.decide(
                admin.getUserId(),
                created.propertyId(),
                new PropertyDecisionRequest(
                        false,
                        "Property ownership document is unclear."
                )
        );

        assertEquals(PropertyStatus.REJECTED, rejected.status());
        assertFalse(rejected.isLive());
        assertEquals(
                "Property ownership document is unclear.",
                rejected.rejectionReason()
        );

        assertFalse(isPubliclyVisible(created.propertyId()));
    }

    @Test
    @DisplayName("A rejected property returns to DRAFT when corrected, then can resubmit")
    void rejectedPropertyCanBeCorrectedAndResubmitted() {
        var created = service.create(partner, request());

        addRoom(propertyRepository
                .findById(created.propertyId())
                .orElseThrow());

        service.submit(partner.getUserId(), created.propertyId());

        service.decide(
                admin.getUserId(),
                created.propertyId(),
                new PropertyDecisionRequest(false, "Address is wrong")
        );

        var corrected = service.update(
                partner.getUserId(),
                created.propertyId(),
                request()
        );

        assertEquals(PropertyStatus.DRAFT, corrected.status());
        assertTrue(corrected.canSubmit());

        var resubmitted = service.submit(
                partner.getUserId(),
                created.propertyId()
        );

        assertEquals(
                PropertyStatus.PENDING_APPROVAL,
                resubmitted.status()
        );
    }

    @Test
    @DisplayName("A property is locked while under review")
    void propertyIsLockedWhileUnderReview() {
        var created = service.create(partner, request());

        addRoom(propertyRepository
                .findById(created.propertyId())
                .orElseThrow());

        service.submit(partner.getUserId(), created.propertyId());

        assertThrows(
                PartnerApplicationException.class,
                () -> service.update(
                        partner.getUserId(),
                        created.propertyId(),
                        request()
                ),
                "A property under review must not be editable"
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> service.submit(
                        partner.getUserId(),
                        created.propertyId()
                ),
                "A property under review must not be resubmittable"
        );
    }

    @Test
    @DisplayName("A partner cannot edit another partner's property")
    void ownershipIsEnforced() {
        var created = service.create(partner, request());

        User other = createUser("other@test.travelbuddy");

        assertThrows(
                PartnerApplicationException.class,
                () -> service.update(
                        other.getUserId(),
                        created.propertyId(),
                        request()
                ),
                "Ownership must be enforced, not just assumed"
        );
    }

    @Test
    @DisplayName("An approved property can be suspended and stops being public")
    void suspensionRemovesPropertyFromMarketplace() {
        var created = service.create(partner, request());

        addRoom(propertyRepository
                .findById(created.propertyId())
                .orElseThrow());

        service.submit(partner.getUserId(), created.propertyId());

        service.decide(
                admin.getUserId(),
                created.propertyId(),
                new PropertyDecisionRequest(true, null)
        );

        var suspended = service.suspend(
                admin.getUserId(),
                created.propertyId(),
                "Guest safety investigation"
        );

        assertEquals(PropertyStatus.SUSPENDED, suspended.status());
        assertFalse(suspended.isLive());

        assertFalse(
                isPubliclyVisible(created.propertyId()),
                "A suspended property must leave public listings"
        );
    }

    @Test
    @DisplayName("Only a live property can be suspended")
    void onlyLivePropertyCanBeSuspended() {
        var created = service.create(partner, request());

        assertThrows(
                PartnerApplicationException.class,
                () -> service.suspend(
                        admin.getUserId(),
                        created.propertyId(),
                        "Too early"
                )
        );
    }
}
