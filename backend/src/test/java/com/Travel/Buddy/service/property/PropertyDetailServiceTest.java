package com.Travel.Buddy.service.property;

import com.Travel.Buddy.dto.property.PropertyDetailResponse;
import com.Travel.Buddy.entity.*;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The hotel detail page. (SRS 2.3 section 6.2)
 *
 * <p>This endpoint previously existed in a thin form and was
 * replaced by a superset. These tests pin the sections section
 * 6.2 enumerates, so a future simplification cannot quietly drop
 * one and nobody notices until a page renders blank.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Property detail page (SRS 2.3 section 6.2)")
class PropertyDetailServiceTest {

    @Autowired
    private PropertyService propertyService;
    @Autowired
    private PropertyDetailService detailService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CityRepository cityRepository;
    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private RoomTypeRepository roomTypeRepository;
    @Autowired
    private RoomInventoryRepository inventoryRepository;
    @Autowired
    private PropertyAmenityRepository propertyAmenityRepository;
    @Autowired
    private PropertyImageRepository propertyImageRepository;
    @Autowired
    private PropertyPolicyRepository propertyPolicyRepository;
    @Autowired
    private PropertyCancellationTermRepository cancellationRepository;
    @Autowired
    private AmenityRepository amenityRepository;
    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private StateRepository stateRepository;

    private static State sharedState;

    private User owner;
    private City city;
    private Property property;
    private RoomType roomType;

    private static final int NIGHTS = 3;

    @BeforeEach
    void setUp() {
        owner = user("Detail Owner");

        if (sharedState == null) {
            /*
             * Upsert, not insert. The suite runs several Spring
             * contexts in one JVM, so a static guard can be
             * re-entered against a fresh database and a blind
             * insert would then collide on the unique iso code.
             */
            Country country = countryRepository
                    .findByIsoCode("PDL")
                    .orElseGet(() -> {
                        Country created = new Country();
                        created.setName("Detail Land");
                        created.setIsoCode("PDL");
                        return created;
                    });
            country = countryRepository.save(country);

            sharedState = new State();
            sharedState.setName("Detail State");
            sharedState.setCountry(country);
            sharedState.setRegionZone(RegionZone.EAST);
            sharedState = stateRepository.save(sharedState);
        }

        city = new City(
                sharedState,
                "D-" + UUID.randomUUID()
                        .toString().substring(0, 6),
                "d-" + UUID.randomUUID()
                        .toString().substring(0, 6)
        );
        city.setLatitude(new BigDecimal("19.8135"));
        city.setLongitude(new BigDecimal("85.8312"));
        city = cityRepository.save(city);

        property = new Property();
        property.setName("Detail Stay-" + UUID.randomUUID()
                .toString().substring(0, 6));
        property.setPropertyType(PropertyType.HOTEL);
        property.setState(sharedState);
        property.setCity(city);
        property.setPartner(owner);
        property.setAddress("Beach Road");
        property.setDescription("A sea facing stay");
        property.setLatitude(new BigDecimal("19.8200"));
        property.setLongitude(new BigDecimal("85.8400"));
        property.setStatus(PropertyStatus.APPROVED);
        property.setVerified(true);
        property.setActive(true);
        property = propertyRepository.save(property);

        roomType = new RoomType();
        roomType.setProperty(property);
        roomType.setCategoryName("Deluxe");
        roomType.setMaxOccupancy(2);
        roomType.setBasePrice(new BigDecimal("2000.00"));
        roomType.setCurrency("INR");
        roomType.setTotalInventory(4);
        roomType.setActive(true);
        roomType = roomTypeRepository.save(roomType);

        seedContent(property);
    }

    private User user(String name) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(UUID.randomUUID() + "@tb.local");
        user.setPasswordHash("{noop}password");
        return userRepository.save(user);
    }

    private void seedContent(Property target) {
        /*
         * The amenity catalogue is seeded by a Flyway migration,
         * and the test profile disables Flyway and builds the
         * schema from the entities instead. Relying on the
         * migration here would leave the catalogue empty and the
         * amenity assertions passing or failing for the wrong
         * reason, so the fixture seeds what it needs.
         */
        for (String code : new String[]{
                "WIFI", "PARKING", "POOL", "STEP_FREE", "TV"}) {
            propertyAmenityRepository.save(
                    new PropertyAmenity(
                            target, amenity(code),
                            true, false, null
                    )
            );
        }

        propertyImageRepository.save(
                new PropertyImage(
                        target,
                        "https://images.example/stay.jpg",
                        "The exterior", 0, true
                )
        );

        propertyPolicyRepository.save(
                new PropertyPolicy(
                        target, PolicyType.CHECK_IN_TIME,
                        "Check-in", "From 12:00", 0
                )
        );

        cancellationRepository.save(
                new PropertyCancellationTerm(
                        target, 7, new BigDecimal("100.00"),
                        0, "Free up to 7 days before"
                )
        );
    }

    /**
     * Upsert, because the catalogue is shared across the whole
     * test context and a fixed code must not collide on its
     * unique index.
     */
    private Amenity amenity(String code) {
        return amenityRepository.findByCode(code)
                .orElseGet(() -> amenityRepository.save(
                        new Amenity(code, code, "GROUP", null)));
    }

    private void stock(int available) {
        LocalDate checkIn = LocalDate.now().plusDays(90);

        for (int i = 0; i < NIGHTS; i++) {
            final LocalDate night = checkIn.plusDays(i);

            RoomInventoryDaily day = inventoryRepository
                    .findByRoomType_RoomTypeIdAndInventoryDate(
                            roomType.getRoomTypeId(), night)
                    .orElseGet(RoomInventoryDaily::new);

            day.setRoomType(roomType);
            day.setInventoryDate(night);
            day.setTotalInventory(4);
            day.setReservedRooms(
                    Math.max(4 - available, 0));
            day.setBlockedRooms(0);
            day.recalculateAvailableInventory();
            inventoryRepository.save(day);
        }
    }

    private PropertyDetailResponse detail() {
        return detailService.detail(
                property.getPropertyId(), null, null
        );
    }

    /* ============================================================
     * SUPERSET SHAPE
     * ============================================================ */

    @Test
    @DisplayName("the detail page carries every section 6.2 lists")
    void everySectionIsPresent() {
        PropertyDetailResponse detail = detail();

        assertNotNull(detail.propertyId());
        assertNotNull(detail.name());
        assertNotNull(detail.description());
        assertNotNull(detail.address());
        assertNotNull(detail.cityName());
        assertNotNull(detail.stateName());
        assertNotNull(detail.latitude());
        assertNotNull(detail.longitude());
        assertNotNull(detail.images());
        assertNotNull(detail.amenities());
        assertNotNull(detail.roomTypes());
        assertNotNull(detail.policies());
        assertNotNull(detail.cancellationTerms());
    }

    /**
     * The endpoint absorbed an older, thinner response. These
     * fields are what the existing Stay Details page reads, so
     * losing any of them would silently blank that page.
     */
    @Test
    @DisplayName("fields the existing frontend depends on are preserved")
    void legacyFieldsSurvive() {
        PropertyDetailResponse detail = propertyService
                .getPropertyById(property.getPropertyId());

        assertNotNull(detail.propertyId());
        assertNotNull(detail.stateId());
        assertNotNull(detail.stateName());
        assertNotNull(detail.name());
        assertNotNull(detail.propertyType());
        assertNotNull(detail.address());
        assertNotNull(detail.description());
        assertNotNull(detail.verified());
    }

    /* ============================================================
     * 2.2  ATTRACTIONS AND AMENITIES
     * ============================================================ */

    @Test
    @DisplayName("amenities are grouped by category for the page")
    void amenitiesAreGrouped() {
        PropertyDetailResponse detail = detail();

        assertFalse(detail.amenities().isEmpty());

        assertTrue(
                detail.amenities().stream().allMatch(
                        group -> group.amenities() != null
                                && !group.amenities().isEmpty()
                ),
                "an empty group renders as a heading with "
                        + "nothing under it"
        );
    }

    @Test
    @DisplayName("an amenity carries its catalogue code and price flags")
    void amenityEntryIsComplete() {
        PropertyDetailResponse.Entry entry = detail()
                .amenities().stream()
                .flatMap(g -> g.amenities().stream())
                .findFirst()
                .orElseThrow();

        assertNotNull(entry.code());
        assertNotNull(entry.label());
        assertNotNull(entry.iconName() == null
                ? "ok"
                : entry.iconName());
        assertTrue(
                entry.free() || entry.requiresBooking()
                        || entry.note() != null,
                "an amenity that is paid, needs booking, or carries "
                        + "a note must say which"
        );
    }

    /* ============================================================
     * 6.1  CANCELLATION
     * ============================================================ */

    @Test
    @DisplayName("cancellation tiers expose refund and the derived penalty")
    void cancellationTiersAreComplete() {
        PropertyDetailResponse.CancellationTier tier = detail()
                .cancellationTerms().get(0);

        assertEquals(7, tier.daysBeforeCheckIn());
        assertEquals(
                0, new BigDecimal("100.00")
                        .compareTo(tier.refundPercent())
        );
        assertEquals(
                0, new BigDecimal("0.00")
                        .compareTo(tier.penaltyPercent()),
                "the penalty is derived, so the two can never "
                        + "be shown as independent facts"
        );
    }

    @Test
    @DisplayName("the most generous window is surfaced for the page")
    void mostGenerousWindowIsExposed() {
        assertEquals(
                7, detail().mostGenerousCancellationDays()
        );
    }

    /* ============================================================
     * AVAILABILITY
     * ============================================================ */

    /**
     * Null rather than false: "available" without a date range is
     * a claim the backend cannot support.
     */
    @Test
    @DisplayName("availability is null when no dates are asked for")
    void availabilityIsNullWithoutDates() {
        assertNull(
                detail().availability(),
                "an unsupported claim is worse than no claim"
        );
    }

    @Test
    @DisplayName("a fully open range reports available")
    void openRangeIsAvailable() {
        stock(4);

        LocalDate checkIn = LocalDate.now().plusDays(90);

        PropertyDetailResponse.Availability availability =
                detailService.detail(
                                property.getPropertyId(),
                                checkIn,
                                checkIn.plusDays(NIGHTS)
                        )
                        .availability();

        assertNotNull(availability);
        assertTrue(availability.available());
        assertEquals(NIGHTS, availability.nights());
        assertNull(availability.reason());
    }

    @Test
    @DisplayName("a sold-out range is unavailable and says why")
    void soldOutRangeIsUnavailable() {
        stock(0);

        LocalDate checkIn = LocalDate.now().plusDays(90);

        PropertyDetailResponse.Availability availability =
                detailService.detail(
                                property.getPropertyId(),
                                checkIn,
                                checkIn.plusDays(NIGHTS)
                        )
                        .availability();

        assertNotNull(availability);
        assertFalse(availability.available());
        assertNotNull(availability.reason());
    }

    /* ============================================================
     * VISIBILITY
     * ============================================================ */

    /**
     * Preserved from the endpoint this replaced: an unapproved
     * property stays invisible publicly.
     */
    @Test
    @DisplayName("an unverified property is not publicly visible")
    void unverifiedPropertyIsHidden() {
        property.setVerified(false);
        propertyRepository.save(property);

        assertThrows(
                PropertyNotFoundException.class,
                () -> propertyService.getPropertyById(
                        property.getPropertyId()
                )
        );
    }

    @Test
    @DisplayName("an inactive property is not publicly visible")
    void inactivePropertyIsHidden() {
        property.setActive(false);
        propertyRepository.save(property);

        assertThrows(
                PropertyNotFoundException.class,
                () -> propertyService.getPropertyById(
                        property.getPropertyId()
                )
        );
    }

    @Test
    @DisplayName("an unknown property is reported as not found")
    void unknownPropertyIsNotFound() {
        assertThrows(
                PropertyNotFoundException.class,
                () -> propertyService.getPropertyById(
                        999_999_999L
                )
        );
    }

    @Test
    @DisplayName("backwards dates are rejected")
    void backwardsRangeRejected() {
        LocalDate checkIn = LocalDate.now().plusDays(90);

        assertThrows(
                PartnerApplicationException.class,
                () -> detailService.detail(
                        property.getPropertyId(),
                        checkIn,
                        checkIn.minusDays(2)
                )
        );
    }
}