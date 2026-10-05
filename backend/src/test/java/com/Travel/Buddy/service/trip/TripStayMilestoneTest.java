package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.dto.trip.*;
import com.Travel.Buddy.entity.*;
import com.Travel.Buddy.repository.*;
import com.Travel.Buddy.service.stay.RoomStayService;

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
 * The treasure map follows what actually happens. (FR-23, TP-11)
 *
 * <p>A check-in is performed by the hotel, against a room the
 * guest really has, after payment has cleared. That makes it the
 * one guest event in the system strong enough to justify ticking a
 * pin, and the map is wired to exactly that and nothing looser.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Stay checkpoints follow real arrivals (FR-23)")
class TripStayMilestoneTest {

    @Autowired
    private TripService tripService;
    @Autowired
    private TripCartService cartService;
    @Autowired
    private TripMilestoneService milestoneService;
    @Autowired
    private RoomStayService roomStayService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private StateRepository stateRepository;
    @Autowired
    private CityRepository cityRepository;
    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private RoomTypeRepository roomTypeRepository;
    @Autowired
    private RoomStayRepository roomStayRepository;
    @Autowired
    private PhysicalRoomRepository physicalRoomRepository;
    @Autowired
    private BookingRepository bookingRepository;

    private static State sharedState;

    private User traveller;
    private User hotelPartner;
    private City city;
    private Property property;
    private RoomType roomType;

    @BeforeEach
    void setUp() {
        traveller = user("Stay Traveller");
        hotelPartner = user("Stay Hotel");

        if (sharedState == null) {
            Country country = countryRepository
                    .findByIsoCode("STM")
                    .orElseGet(() -> {
                        Country created = new Country();
                        created.setName("Stay Land");
                        created.setIsoCode("STM");
                        return created;
                    });
            country = countryRepository.save(country);

            sharedState = new State();
            sharedState.setName("Stay State");
            sharedState.setCountry(country);
            sharedState.setRegionZone(RegionZone.EAST);
            sharedState = stateRepository.save(sharedState);
        }

        city = new City(
                sharedState,
                "ST-" + UUID.randomUUID()
                        .toString().substring(0, 6),
                "st-" + UUID.randomUUID()
                        .toString().substring(0, 6)
        );
        city = cityRepository.save(city);

        property = new Property();
        property.setName("Stay Inn-" + UUID.randomUUID()
                .toString().substring(0, 6));
        property.setPropertyType(PropertyType.HOTEL);
        property.setState(sharedState);
        property.setCity(city);
        property.setPartner(hotelPartner);
        property.setAddress("Somewhere by the sea");
        property.setDescription("A small inn for the test");
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
    }

    private User user(String name) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(UUID.randomUUID() + "@tb.local");
        user.setPasswordHash("{noop}password");
        return userRepository.save(user);
    }

    /**
     * A trip with a route and one hotel selected in its only stop,
     * which is what earns that stop the check-in pins.
     */
    private TripDetailResponse tripWithStay() {
        LocalDate start = LocalDate.now().plusDays(150);
        LocalDate end = start.plusDays(3);

        TripDetailResponse created = tripService.create(
                traveller.getUserId(),
                new CreateTripRequest(
                        "Stay Trip", start, end, 1, null, "INR"
                )
        );

        TripDetailResponse routed = tripService.setRoute(
                traveller.getUserId(),
                created.tripId(),
                new SetTripRouteRequest(
                        List.of(new SetTripRouteRequest.CityStopRequest(
                                city.getCityId(), start, end
                        ))
                )
        );

        /*
         * Attached to the stop, not to the trip. A room chosen
         * for no particular stop is not a stay the traveller will
         * check in to, which is exactly what seedStayCheckpoints
         * refuses to invent a pin for.
         */
        return cartService.addSelection(
                traveller.getUserId(),
                created.tripId(),
                new AddTripSelectionRequest(
                        TripSelectionType.HOTEL,
                        routed.cities().get(0).tripCityId(),
                        property.getPropertyId(),
                        roomType.getRoomTypeId(),
                        start,
                        end,
                        1,
                        1,
                        null,
                        "INR"
                )
        );
    }

    private TripMilestoneResponse pin(
            TripDetailResponse trip,
            TripMilestoneType type
    ) {
        return trip.milestones().stream()
                .filter(m -> m.milestoneType() == type)
                .findFirst()
                .orElse(null);
    }

    private TripDetailResponse reload(Long tripId) {
        return tripService.get(traveller.getUserId(), tripId);
    }

    /* ============================================================
     * THE PINS EXIST
     * ============================================================ */

    @Test
    @DisplayName("selecting a room earns the stop a check-in and check-out")
    void selectingARoomAddsStayPins() {
        TripDetailResponse trip = tripWithStay();

        assertNotNull(
                pin(trip, TripMilestoneType.CHECKED_IN),
                "a stop with a room booked is a stop the traveller "
                        + "will check in to"
        );
        assertNotNull(pin(trip, TripMilestoneType.CHECKED_OUT));
    }

    /**
     * A stop with no room keeps only the plain arrival and
     * departure. A check-in pin for somewhere the traveller never
     * sleeps is a claim about their trip that is simply false.
     */
    @Test
    @DisplayName("a stop with no room has no check-in pin")
    void passThroughStopHasNoStayPins() {
        LocalDate start = LocalDate.now().plusDays(150);
        LocalDate end = start.plusDays(3);

        TripDetailResponse created = tripService.create(
                traveller.getUserId(),
                new CreateTripRequest(
                        "Pass Through", start, end, 1, null, "INR"
                )
        );

        TripDetailResponse routed = tripService.setRoute(
                traveller.getUserId(),
                created.tripId(),
                new SetTripRouteRequest(
                        List.of(new SetTripRouteRequest.CityStopRequest(
                                city.getCityId(), start, end
                        ))
                )
        );

        assertNull(pin(routed, TripMilestoneType.CHECKED_IN));
        assertNotNull(pin(routed, TripMilestoneType.CITY_ARRIVED));
    }

    /* ============================================================
     * THE MAP ONLY MOVES ON A REAL EVENT
     * ============================================================ */

    @Test
    @DisplayName("nothing is ticked merely because a room was selected")
    void selectingIsNotArriving() {
        assertFalse(
                pin(tripWithStay(), TripMilestoneType.CHECKED_IN)
                        .completed(),
                "booking a room is not checking in to it"
        );
    }

    @Test
    @DisplayName("a real check-in ticks the pin on the trip")
    void realCheckInTicksTheMap() {
        TripDetailResponse trip = tripWithStay();

        RoomStay stay = assignedPaidStay(
                trip, LocalDate.now().plusDays(150)
        );

        roomStayService.checkIn(
                hotelPartner.getUserId(), stay.getStayId()
        );

        assertTrue(
                pin(reload(trip.tripId()),
                        TripMilestoneType.CHECKED_IN).completed(),
                "the hotel checked a paid guest in, so the journey "
                        + "has actually started"
        );
    }

    @Test
    @DisplayName("a booking made outside any trip leaves every map alone")
    void directBookingDoesNotTickAMap() {
        LocalDate checkIn = LocalDate.now().plusDays(150);

        Booking booking = paidBooking(checkIn);
        RoomStay stay = assignedStay(booking, checkIn);

        roomStayService.checkIn(
                hotelPartner.getUserId(), stay.getStayId()
        );

        /*
         * Nothing to assert against a trip, because there is no
         * trip. The point is that the check-in succeeded without
         * inventing a journey to record it against, which is what
         * would have happened had the code walked a trip that did
         * not exist.
         */
        assertEquals(
                BookingStatus.CHECKED_IN,
                bookingRepository
                        .findById(booking.getBookingId())
                        .orElseThrow()
                        .getBookingStatus()
        );
    }

    /* ============================================================
     * THE PICK IS NAMED
     * ============================================================ */

    /**
     * This was the literal string "Item 1": the database id
     * dressed up as a hotel name, shown in the traveller's own
     * cart for every hotel, guide and cab they picked.
     */
    @Test
    @DisplayName("a picked hotel is named, not shown as Item 1")
    void pickedHotelIsNamed() {
        TripDetailResponse trip = tripWithStay();

        assertEquals(
                property.getName(),
                trip.selections().get(0).targetName(),
                "a traveller must see what they actually booked"
        );
    }

    /* ============================================================
     * FIXTURES
     * ============================================================ */

    private Booking paidBooking(LocalDate checkIn) {
        Booking booking = new Booking();
        booking.setUser(traveller);
        booking.setBookingReference(
                "BK-" + UUID.randomUUID()
                        .toString().substring(0, 8).toUpperCase()
        );
        booking.setTotalAmount(new BigDecimal("2000.00"));
        booking.setCurrency("INR");
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setGuestCount(1);
        booking.setGuestName("Stay Traveller");
        return bookingRepository.save(booking);
    }

    private RoomStay assignedStay(Booking booking, LocalDate checkIn) {
        PhysicalRoom room = new PhysicalRoom();
        room.setProperty(property);
        room.setRoomType(roomType);
        room.setRoomNumber(
                UUID.randomUUID().toString().substring(0, 4)
        );
        room.setStatus(PhysicalRoomStatus.AVAILABLE);
        room = physicalRoomRepository.save(room);

        RoomStay stay = new RoomStay();
        stay.setBooking(booking);
        stay.setPhysicalRoom(room);
        stay.setCheckInDate(checkIn);
        stay.setCheckOutDate(checkIn.plusDays(2));
        stay.setGuestName("Stay Traveller");
        stay.setStatus(RoomStayStatus.ASSIGNED);
        return roomStayRepository.save(stay);
    }

    /**
     * A stay that is both paid and linked back to the trip's own
     * selection, which is the only way a guest event can honestly
     * reach the map.
     */
    private RoomStay assignedPaidStay(
            TripDetailResponse trip,
            LocalDate checkIn
    ) {
        TripSelection selection = selectionRepository
                .findByTrip_TripIdOrderBySelectionIdAsc(trip.tripId())
                .get(0);

        Booking booking = paidBooking(checkIn);
        selection.markBooked(booking);
        selectionRepository.save(selection);

        return assignedStay(booking, checkIn);
    }

    @Autowired
    private TripSelectionRepository selectionRepository;
}
