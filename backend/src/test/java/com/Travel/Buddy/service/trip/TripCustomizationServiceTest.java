package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.dto.trip.AddTripSelectionRequest;
import com.Travel.Buddy.dto.trip.CreateTripRequest;
import com.Travel.Buddy.dto.trip.CustomiseTripSelectionRequest;
import com.Travel.Buddy.dto.trip.SetTripRouteRequest;
import com.Travel.Buddy.dto.trip.SwapTripSelectionRequest;
import com.Travel.Buddy.dto.trip.TripDetailResponse;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hotel customisation. (SRS 2.3 section 6.2)
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Hotel customisation (SRS 2.3 section 6.2)")
class TripCustomizationServiceTest {

    @Autowired
    private TripCartService cartService;
    @Autowired
    private TripService tripService;
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
    private CountryRepository countryRepository;
    @Autowired
    private StateRepository stateRepository;

    private static State sharedState;

    private User traveller;
    private User stranger;
    private User owner;
    private City city;
    private Property first;
    private Property second;
    private RoomType deluxe;
    private RoomType suite;
    private RoomType small;
    private Long tripId;
    private Long stopId;

    private static final int NIGHTS = 3;

    @BeforeEach
    void setUp() {
        traveller = user("Custom Traveller");
        stranger = user("Custom Stranger");
        owner = user("Custom Owner");

        if (sharedState == null) {
            Country country = new Country();
            country.setName("Custom Land");
            country.setIsoCode("CL");
            country = countryRepository.save(country);

            sharedState = new State();
            sharedState.setName("Custom State");
            sharedState.setCountry(country);
            sharedState.setRegionZone(RegionZone.EAST);
            sharedState = stateRepository.save(sharedState);
        }

        /*
         * A private city per test. Candidates and selections are
         * city-scoped, and sharing one would let earlier tests
         * change what this one sees.
         */
        city = new City(
                sharedState,
                "C-" + UUID.randomUUID()
                        .toString().substring(0, 6),
                "c-" + UUID.randomUUID()
                        .toString().substring(0, 6)
        );
        city.setLatitude(new BigDecimal("19.8135"));
        city.setLongitude(new BigDecimal("85.8312"));
        city = cityRepository.save(city);

        first = property("First");
        second = property("Second");

        deluxe = room(first, "Deluxe", "2000.00", 4);
        suite = room(first, "Suite", "4500.00", 6);
        small = room(first, "Single", "1000.00", 1);

        LocalDate checkIn = LocalDate.now().plusDays(70);

        tripId = tripService.create(
                traveller.getUserId(),
                new CreateTripRequest(
                        "Custom Trip", checkIn,
                        checkIn.plusDays(NIGHTS), 1,
                        null, "INR"
                )
        ).tripId();

        stopId = tripService.setRoute(
                traveller.getUserId(),
                tripId,
                new SetTripRouteRequest(List.of(
                        new SetTripRouteRequest.CityStopRequest(
                                city.getCityId(),
                                checkIn,
                                checkIn.plusDays(NIGHTS)
                        )
                ))
        ).cities().get(0).tripCityId();

        stock(deluxe, NIGHTS, 4);
        stock(suite, NIGHTS, 4);
        stock(secondRoom(), NIGHTS, 4);
    }

    private User user(String name) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(UUID.randomUUID() + "@tb.local");
        user.setPasswordHash("{noop}password");
        return userRepository.save(user);
    }

    private Property property(String name) {
        Property property = new Property();
        property.setName(name + "-" + UUID.randomUUID()
                .toString().substring(0, 6));
        property.setPropertyType(PropertyType.HOTEL);
        property.setState(sharedState);
        property.setCity(city);
        property.setPartner(owner);
        property.setAddress("Somewhere");
        property.setDescription("A stay");
        property.setLatitude(new BigDecimal("19.8200"));
        property.setLongitude(new BigDecimal("85.8400"));
        property.setStatus(PropertyStatus.APPROVED);
        property.setVerified(true);
        property.setActive(true);
        return propertyRepository.save(property);
    }

    private RoomType room(
            Property property,
            String category,
            String price,
            int maxOccupancy
    ) {
        RoomType room = new RoomType();
        room.setProperty(property);
        room.setCategoryName(category);
        room.setMaxOccupancy(maxOccupancy);
        room.setBasePrice(new BigDecimal(price));
        room.setCurrency("INR");
        room.setTotalInventory(5);
        room.setActive(true);
        return roomTypeRepository.save(room);
    }

    private RoomType secondRoom() {
        return room(second, "Standard", "2500.00", 4);
    }

    private void stock(RoomType roomType, int nights, int available) {
        LocalDate checkIn = LocalDate.now().plusDays(70);

        for (int i = 0; i < nights; i++) {
            final LocalDate night = checkIn.plusDays(i);

            RoomInventoryDaily day = inventoryRepository
                    .findByRoomType_RoomTypeIdAndInventoryDate(
                            roomType.getRoomTypeId(), night)
                    .orElseGet(RoomInventoryDaily::new);

            day.setRoomType(roomType);
            day.setInventoryDate(night);
            day.setTotalInventory(5);
            day.setReservedRooms(Math.max(5 - available, 0));
            day.setBlockedRooms(0);
            day.recalculateAvailableInventory();
            inventoryRepository.save(day);
        }
    }

    private TripDetailResponse select(RoomType roomType) {
        LocalDate checkIn = LocalDate.now().plusDays(70);

        return cartService.addSelection(
                traveller.getUserId(),
                tripId,
                new AddTripSelectionRequest(
                        TripSelectionType.HOTEL,
                        stopId,
                        roomType.getProperty().getPropertyId(),
                        roomType.getRoomTypeId(),
                        checkIn,
                        checkIn.plusDays(NIGHTS),
                        2, null, null, "INR"
                )
        );
    }

    private Long selectedId() {
        return select(deluxe).selections()
                .stream()
                .filter(s -> s.status() == TripSelectionStatus.SELECTED)
                .map(s -> s.selectionId())
                .findFirst()
                .orElseThrow();
    }

    /* ============================================================
     * ROOM TYPE
     * ============================================================ */

    @Test
    @DisplayName("a traveller can change the room type")
    void changeRoomType() {
        Long id = selectedId();

        TripDetailResponse trip = cartService.customiseSelection(
                traveller.getUserId(), tripId, id,
                new CustomiseTripSelectionRequest(
                        suite.getRoomTypeId(), null, null
                )
        );

        assertEquals(
                new BigDecimal("13500.00"),
                trip.selections().get(0).quotedAmount(),
                "3 nights at 4500 must be re-quoted"
        );
    }

    @Test
    @DisplayName("a room type from another property is refused")
    void roomTypeMustBelongToTheProperty() {
        Long id = selectedId();
        RoomType elsewhere = secondRoom();

        assertThrows(
                PartnerApplicationException.class,
                () -> cartService.customiseSelection(
                        traveller.getUserId(), tripId, id,
                        new CustomiseTripSelectionRequest(
                                elsewhere.getRoomTypeId(),
                                null, null
                        )
                )
        );
    }

    /**
     * A traveller upgrading for more guests must not be able to
     * pick a room that cannot sleep them.
     */
    @Test
    @DisplayName("a room too small for the guests is refused")
    void roomMustFitTheParty() {
        Long id = selectedId();

        assertThrows(
                PartnerApplicationException.class,
                () -> cartService.customiseSelection(
                        traveller.getUserId(), tripId, id,
                        new CustomiseTripSelectionRequest(
                                small.getRoomTypeId(),
                                4, null
                        )
                )
        );
    }

    /* ============================================================
     * ROOM COUNT  (the money bug)
     * ============================================================ */

    @Test
    @DisplayName("the number of rooms can be changed and repriced")
    void changeRoomCount() {
        Long id = selectedId();

        TripDetailResponse trip = cartService.customiseSelection(
                traveller.getUserId(), tripId, id,
                new CustomiseTripSelectionRequest(
                        null, 2, null
                )
        );

        assertEquals(
                new BigDecimal("12000.00"),
                trip.selections().get(0).quotedAmount(),
                "3 nights at 2000 across 2 rooms"
        );
    }

    /**
     * The defect found while building this: the quote always
     * evaluated to a single room regardless of party size, so a
     * family of four was quoted for one room.
     */
    @Test
    @DisplayName("a party needing two rooms is priced for two")
    void partySizeDrivesRoomCount() {
        TripDetailResponse trip = select(deluxe);

        assertEquals(
                new BigDecimal("6000.00"),
                trip.selections().get(0).quotedAmount(),
                "one room for two guests"
        );

        trip = cartService.customiseSelection(
                traveller.getUserId(), tripId,
                trip.selections().get(0).selectionId(),
                new CustomiseTripSelectionRequest(
                        null, null, 4
                )
        );

        assertEquals(
                new BigDecimal("12000.00"),
                trip.selections().get(0).quotedAmount(),
                "four guests need two rooms, and must be billed for "
                        + "both"
        );
    }

    @Test
    @DisplayName("a room count below one is refused")
    void roomCountMustBePositive() {
        Long id = selectedId();

        assertThrows(
                IllegalArgumentException.class,
                () -> cartService.customiseSelection(
                        traveller.getUserId(), tripId, id,
                        new CustomiseTripSelectionRequest(
                                null, 0, null
                        )
                )
        );
    }

    /* ============================================================
     * SWAP
     * ============================================================ */

    @Test
    @DisplayName("a recommended hotel can be replaced outright")
    void swapReplacesTheHotel() {
        Long id = selectedId();

        TripDetailResponse trip = cartService.swapSelection(
                traveller.getUserId(), tripId, id,
                new SwapTripSelectionRequest(
                        TripSelectionType.HOTEL,
                        second.getPropertyId(),
                        secondRoom().getRoomTypeId(),
                        null, null, null, null, "INR"
                )
        );

        List<com.Travel.Buddy.dto.trip.TripSelectionResponse> live =
                trip.selections().stream()
                        .filter(s -> s.status()
                                == TripSelectionStatus.SELECTED)
                        .toList();

        assertEquals(1, live.size());
        assertEquals(
                second.getPropertyId(), live.get(0).targetId()
        );
    }

    @Test
    @DisplayName("a swap keeps the dates of the selection it replaces")
    void swapKeepsDates() {
        Long id = selectedId();

        TripDetailResponse trip = cartService.swapSelection(
                traveller.getUserId(), tripId, id,
                new SwapTripSelectionRequest(
                        TripSelectionType.HOTEL,
                        second.getPropertyId(),
                        secondRoom().getRoomTypeId(),
                        null, null, null, null, "INR"
                )
        );

        var live = trip.selections().stream()
                .filter(s -> s.status()
                        == TripSelectionStatus.SELECTED)
                .findFirst()
                .orElseThrow();

        assertEquals(
                LocalDate.now().plusDays(70),
                live.checkIn()
        );
        assertEquals(NIGHTS, live.nights());
    }

    /**
     * The old selection is retained rather than deleted, so the
     * record shows a hotel was chosen and then swapped.
     */
    @Test
    @DisplayName("a swap leaves the replaced selection visible as removed")
    void swapRetainsHistory() {
        Long id = selectedId();

        TripDetailResponse trip = cartService.swapSelection(
                traveller.getUserId(), tripId, id,
                new SwapTripSelectionRequest(
                        TripSelectionType.HOTEL,
                        second.getPropertyId(),
                        secondRoom().getRoomTypeId(),
                        null, null, null, null, "INR"
                )
        );

        assertTrue(
                trip.selections().stream().anyMatch(
                        s -> s.status()
                                == TripSelectionStatus.REMOVED),
                "the replaced selection must not vanish from the "
                        + "record"
        );
    }

    @Test
    @DisplayName("a stranger cannot customise another traveller's stay")
    void customisationIsScopedToOwner() {
        Long id = selectedId();

        assertThrows(
                PartnerApplicationException.class,
                () -> cartService.customiseSelection(
                        stranger.getUserId(), tripId, id,
                        new CustomiseTripSelectionRequest(
                                suite.getRoomTypeId(), null, null
                        )
                )
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> cartService.swapSelection(
                        stranger.getUserId(), tripId, id,
                        new SwapTripSelectionRequest(
                                TripSelectionType.HOTEL,
                                second.getPropertyId(),
                                null, null, null, null, null,
                                "INR"
                        )
                )
        );
    }

    @Test
    @DisplayName("a selection from another trip cannot be customised")
    void selectionMustBelongToTheTrip() {
        Long id = selectedId();
        Long otherTrip = tripService.create(
                traveller.getUserId(),
                new CreateTripRequest(
                        "Other", LocalDate.now().plusDays(70),
                        LocalDate.now().plusDays(73), 1,
                        null, "INR"
                )
        ).tripId();

        assertThrows(
                PartnerApplicationException.class,
                () -> cartService.customiseSelection(
                        traveller.getUserId(), otherTrip, id,
                        new CustomiseTripSelectionRequest(
                                suite.getRoomTypeId(), null, null
                        )
                )
        );
    }
}