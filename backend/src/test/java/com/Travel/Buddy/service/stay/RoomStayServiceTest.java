package com.Travel.Buddy.service.stay;

import com.Travel.Buddy.dto.property.PropertyUpsertRequest;
import com.Travel.Buddy.dto.room.RoomTypeUpsertRequest;
import com.Travel.Buddy.dto.stay.PhysicalRoomRequest;
import com.Travel.Buddy.dto.stay.PhysicalRoomResponse;
import com.Travel.Buddy.dto.stay.RoomStayResponse;
import com.Travel.Buddy.entity.*;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.*;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Room assignment and check-in/out. (FR-22, FR-23)
 *
 * <p>The behaviour carrying real risk is double occupancy: two guests
 * being handed the same numbered room for overlapping dates. MySQL
 * cannot express that as a constraint, so it is enforced in the
 * service under a row lock, and these tests pin it down.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Room assignment and check-in/out (FR-22, FR-23)")
class RoomStayServiceTest {

    @Autowired
    private RoomStayService service;

    @Autowired
    private com.Travel.Buddy.service.property.PropertyApprovalService propertyService;

    @Autowired
    private com.Travel.Buddy.service.property.RoomTypeService roomTypeService;

    @Autowired
    private PhysicalRoomRepository physicalRoomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private HotelReservationRepository reservationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private StateRepository stateRepository;

    @Autowired
    private CountryRepository countryRepository;

    private User partner;

    private User guest;

    private Long propertyId;

    private Long deluxeRoomTypeId;

    private Long familyRoomTypeId;

    private Integer stateId;

    @BeforeEach
    void setUp() {
        User admin = createUser("stay-admin@test.travelbuddy");
        roleService.grantBaselineTravelerRole(admin);
        roleService.grant(admin, Role.ROLE_SUPER_ADMIN, null);

        partner = createUser("stay-partner@test.travelbuddy");
        roleService.grantBaselineTravelerRole(partner);
        roleService.grant(partner, Role.ROLE_HOTEL_PARTNER, admin);

        guest = createUser("stay-guest@test.travelbuddy");
        roleService.grantBaselineTravelerRole(guest);

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

        propertyId = propertyService.create(
                partner,
                new PropertyUpsertRequest(
                        "Stay Hotel",
                        PropertyType.HOTEL,
                        stateId,
                        "Address",
                        "Description",
                        new BigDecimal("19.8"),
                        new BigDecimal("85.7")
                )
        ).propertyId();

        deluxeRoomTypeId = roomTypeService.create(
                partner.getUserId(),
                propertyId,
                new RoomTypeUpsertRequest(
                        "Deluxe", 2, new BigDecimal("2500.00"), "INR", 3
                )
        ).roomTypeId();

        familyRoomTypeId = roomTypeService.create(
                partner.getUserId(),
                propertyId,
                new RoomTypeUpsertRequest(
                        "Family Suite", 4, new BigDecimal("5000.00"), "INR", 2
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

    private Long addRoom(Long roomTypeId, String number) {
        return service.addRoom(
                partner.getUserId(),
                propertyId,
                roomTypeId,
                new PhysicalRoomRequest(number, "Floor 1", null)
        ).physicalRoomId();
    }

    private Long confirmedBooking(
            Long roomTypeId,
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        RoomType roomType = findRoomType(roomTypeId);

        Booking booking = new Booking();

        booking.setUser(guest);
        booking.setBookingReference(
                "TB-" + UUID.randomUUID()
                        .toString()
                        .substring(0, 10)
                        .toUpperCase()
        );
        booking.setTotalAmount(new BigDecimal("5000.00"));
        booking.setCurrency("INR");
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setGuestCount(2);
        booking.setGuestName("Test Guest");
        booking.setGuestEmail("guest@test.travelbuddy");
        booking.setGuestPhone("9000000000");

        booking = bookingRepository.save(booking);

        HotelReservation reservation = new HotelReservation();

        reservation.setBooking(booking);
        reservation.setRoomType(roomType);
        reservation.setCheckIn(checkIn);
        reservation.setCheckOut(checkOut);
        reservation.setRoomsBooked(1);

        reservationRepository.save(reservation);

        return booking.getBookingId();
    }

    private RoomType findRoomType(Long roomTypeId) {
        return roomTypeRepository.findById(roomTypeId)
                .orElseThrow();
    }

    @Test
    @DisplayName("Assigning a room creates an ASSIGNED stay")
    void assignCreatesStay() {
        Long room = addRoom(deluxeRoomTypeId, "101");
        LocalDate checkIn = LocalDate.now().plusDays(3);
        Long booking = confirmedBooking(
                deluxeRoomTypeId, checkIn, checkIn.plusDays(2)
        );

        RoomStayResponse stay = service.assign(
                partner.getUserId(), booking, room
        );

        assertEquals(RoomStayStatus.ASSIGNED, stay.status());
        assertEquals("101", stay.roomNumber());
        assertEquals(checkIn, stay.checkInDate());
        assertTrue(stay.canCheckIn());
    }

    @Test
    @DisplayName("Two bookings cannot be given the same room for overlapping dates")
    void overlappingAssignmentRefused() {
        Long room = addRoom(deluxeRoomTypeId, "101");
        LocalDate checkIn = LocalDate.now().plusDays(3);

        Long first = confirmedBooking(
                deluxeRoomTypeId, checkIn, checkIn.plusDays(3)
        );
        Long second = confirmedBooking(
                deluxeRoomTypeId,
                checkIn.plusDays(1),
                checkIn.plusDays(4)
        );

        service.assign(partner.getUserId(), first, room);

        assertThrows(
                PartnerApplicationException.class,
                () -> service.assign(
                        partner.getUserId(), second, room
                ),
                "The same physical room must never host two overlapping stays"
        );
    }

    @Test
    @DisplayName("Back-to-back stays may reuse the same room")
    void backToBackStaysAllowed() {
        Long room = addRoom(deluxeRoomTypeId, "101");
        LocalDate checkIn = LocalDate.now().plusDays(3);

        Long first = confirmedBooking(
                deluxeRoomTypeId, checkIn, checkIn.plusDays(2)
        );
        Long second = confirmedBooking(
                deluxeRoomTypeId,
                checkIn.plusDays(2),
                checkIn.plusDays(4)
        );

        service.assign(partner.getUserId(), first, room);

        /*
         * One guest checks out on the day the next arrives. That is
         * normal turnover, not a conflict.
         */
        RoomStayResponse next = service.assign(
                partner.getUserId(), second, room
        );

        assertEquals(RoomStayStatus.ASSIGNED, next.status());
    }

    @Test
    @DisplayName("A booking cannot be given a room from another category")
    void roomTypeMustMatchBooking() {
        Long suiteRoom = addRoom(familyRoomTypeId, "201");
        Long booking = confirmedBooking(
                deluxeRoomTypeId,
                LocalDate.now().plusDays(3),
                LocalDate.now().plusDays(5)
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> service.assign(
                        partner.getUserId(), booking, suiteRoom
                ),
                "A Deluxe booking must not be given a Suite"
        );
    }

    @Test
    @DisplayName("An out-of-service room cannot be assigned")
    void outOfServiceRoomRefused() {
        Long room = addRoom(deluxeRoomTypeId, "101");

        service.setRoomStatus(
                partner.getUserId(),
                propertyId,
                room,
                PhysicalRoomStatus.OUT_OF_SERVICE
        );

        Long booking = confirmedBooking(
                deluxeRoomTypeId,
                LocalDate.now().plusDays(3),
                LocalDate.now().plusDays(5)
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> service.assign(
                        partner.getUserId(), booking, room
                )
        );
    }

    @Test
    @DisplayName("A room holding a live stay cannot be taken out of service")
    void occupiedRoomCannotGoOutOfService() {
        Long room = addRoom(deluxeRoomTypeId, "101");
        Long booking = confirmedBooking(
                deluxeRoomTypeId,
                LocalDate.now(),
                LocalDate.now().plusDays(2)
        );

        RoomStayResponse stay = service.assign(
                partner.getUserId(), booking, room
        );

        service.checkIn(partner.getUserId(), stay.stayId());

        assertThrows(
                PartnerApplicationException.class,
                () -> service.setRoomStatus(
                        partner.getUserId(),
                        propertyId,
                        room,
                        PhysicalRoomStatus.OUT_OF_SERVICE
                ),
                "An occupied room must not be marked unavailable"
        );
    }

    @Test
    @DisplayName("Check-in then check-out completes the booking")
    void checkInOutCompletesBooking() {
        Long room = addRoom(deluxeRoomTypeId, "101");
        Long booking = confirmedBooking(
                deluxeRoomTypeId,
                LocalDate.now(),
                LocalDate.now().plusDays(2)
        );

        RoomStayResponse stay = service.assign(
                partner.getUserId(), booking, room
        );

        RoomStayResponse inHouse = service.checkIn(
                partner.getUserId(), stay.stayId()
        );

        assertEquals(RoomStayStatus.CHECKED_IN, inHouse.status());
        assertNotNull(inHouse.checkedInAt());

        assertEquals(
                BookingStatus.CHECKED_IN,
                bookingRepository.findById(booking)
                        .orElseThrow()
                        .getBookingStatus(),
                "Check-in must be reflected on the booking"
        );

        RoomStayResponse out = service.checkOut(
                partner.getUserId(), stay.stayId()
        );

        assertEquals(RoomStayStatus.CHECKED_OUT, out.status());
        assertNotNull(out.checkedOutAt());

        assertEquals(
                BookingStatus.COMPLETED,
                bookingRepository.findById(booking)
                        .orElseThrow()
                        .getBookingStatus()
        );
    }

    @Test
    @DisplayName("An unpaid booking cannot be checked in")
    void unpaidBookingCannotCheckIn() {
        Long room = addRoom(deluxeRoomTypeId, "101");
        Long booking = confirmedBooking(
                deluxeRoomTypeId,
                LocalDate.now(),
                LocalDate.now().plusDays(2)
        );

        RoomStayResponse stay = service.assign(
                partner.getUserId(), booking, room
        );

        /* Put the booking back to the unpaid, awaiting-payment state. */
        Booking entity = bookingRepository.findById(booking)
                .orElseThrow();

        entity.setBookingStatus(BookingStatus.PENDING);
        entity.setPaymentStatus(PaymentStatus.UNPAID);
        bookingRepository.save(entity);

        assertThrows(
                PartnerApplicationException.class,
                () -> service.checkIn(
                        partner.getUserId(), stay.stayId()
                ),
                "Payment must gate arrival, or a room is occupied for free"
        );
    }

    @Test
    @DisplayName("A checked-in stay cannot be unassigned")
    void checkedInStayCannotBeUnassigned() {
        Long room = addRoom(deluxeRoomTypeId, "101");
        Long booking = confirmedBooking(
                deluxeRoomTypeId,
                LocalDate.now(),
                LocalDate.now().plusDays(2)
        );

        RoomStayResponse stay = service.assign(
                partner.getUserId(), booking, room
        );

        service.checkIn(partner.getUserId(), stay.stayId());

        assertThrows(
                PartnerApplicationException.class,
                () -> service.unassign(
                        partner.getUserId(), stay.stayId()
                )
        );
    }

    @Test
    @DisplayName("A cancelled assignment frees the room again")
    void unassignFreesRoom() {
        Long room = addRoom(deluxeRoomTypeId, "101");
        LocalDate checkIn = LocalDate.now().plusDays(3);

        Long first = confirmedBooking(
                deluxeRoomTypeId, checkIn, checkIn.plusDays(3)
        );
        Long second = confirmedBooking(
                deluxeRoomTypeId,
                checkIn.plusDays(1),
                checkIn.plusDays(4)
        );

        RoomStayResponse stay = service.assign(
                partner.getUserId(), first, room
        );

        service.unassign(partner.getUserId(), stay.stayId());

        RoomStayResponse reassigned = service.assign(
                partner.getUserId(), second, room
        );

        assertEquals(RoomStayStatus.ASSIGNED, reassigned.status());
    }


    @Test
    @DisplayName("A no-show closes the stay without a check-in")
    void noShowClosesStay() {
        Long room = addRoom(deluxeRoomTypeId, "101");
        Long booking = confirmedBooking(
                deluxeRoomTypeId,
                LocalDate.now(),
                LocalDate.now().plusDays(2)
        );

        RoomStayResponse stay = service.assign(
                partner.getUserId(), booking, room
        );

        RoomStayResponse result = service.markNoShow(
                partner.getUserId(), stay.stayId()
        );

        assertEquals(RoomStayStatus.NO_SHOW, result.status());

        assertEquals(
                BookingStatus.NO_SHOW,
                bookingRepository.findById(booking)
                        .orElseThrow()
                        .getBookingStatus()
        );
    }

    @Test
    @DisplayName("The room list flags which rooms are occupied")
    void roomListShowsOccupancy() {
        Long room = addRoom(deluxeRoomTypeId, "101");
        addRoom(deluxeRoomTypeId, "102");

        Long booking = confirmedBooking(
                deluxeRoomTypeId,
                LocalDate.now(),
                LocalDate.now().plusDays(2)
        );

        service.assign(partner.getUserId(), booking, room);

        List<PhysicalRoomResponse> rooms = service.listRooms(
                partner.getUserId(),
                propertyId,
                deluxeRoomTypeId,
                LocalDate.now(),
                LocalDate.now().plusDays(1)
        );

        assertEquals(2, rooms.size());

        PhysicalRoomResponse occupied = rooms.stream()
                .filter(r -> r.roomNumber().equals("101"))
                .findFirst()
                .orElseThrow();

        assertTrue(occupied.occupiedInWindow());
        assertFalse(
                occupied.assignable(),
                "An occupied room must not be offered to the next guest"
        );
    }

    @Test
    @DisplayName("A duplicate room number is refused")
    void duplicateRoomNumberRefused() {
        addRoom(deluxeRoomTypeId, "101");

        assertThrows(
                PartnerApplicationException.class,
                () -> addRoom(deluxeRoomTypeId, "101")
        );
    }

    @Test
    @DisplayName("A partner cannot touch another property's rooms or stays")
    void ownershipIsEnforced() {
        Long room = addRoom(deluxeRoomTypeId, "101");
        Long booking = confirmedBooking(
                deluxeRoomTypeId,
                LocalDate.now().plusDays(3),
                LocalDate.now().plusDays(5)
        );

        User stranger = createUser("stay-stranger@test.travelbuddy");
        roleService.grantBaselineTravelerRole(stranger);
        roleService.grant(stranger, Role.ROLE_HOTEL_PARTNER, null);

        assertThrows(
                PartnerApplicationException.class,
                () -> service.listRooms(
                        stranger.getUserId(),
                        propertyId,
                        null,
                        null,
                        null
                )
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> service.assign(
                        stranger.getUserId(), booking, room
                )
        );
    }
}

