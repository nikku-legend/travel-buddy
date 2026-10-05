package com.Travel.Buddy.service.stay;

import com.Travel.Buddy.dto.property.PropertyUpsertRequest;
import com.Travel.Buddy.dto.room.RoomTypeUpsertRequest;
import com.Travel.Buddy.dto.stay.PartnerReservationResponse;
import com.Travel.Buddy.dto.stay.PhysicalRoomRequest;
import com.Travel.Buddy.entity.*;
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
 * The partner's arrival list. (FR-22)
 *
 * <p>Exists because {@code GET /front-desk} only returns stays that
 * already have a physical room, which made a confirmed guest who had
 * not been assigned yet invisible — precisely the booking a front desk
 * most needs to see.
 *
 * <p>The behaviour worth pinning is the counting. "Assigned" must mean
 * one physical room per promised room, not one per booking, and a
 * released room must stop counting, or the front desk closes a
 * half-arranged booking and believes it is finished.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Partner arrival list (FR-22)")
class PartnerReservationServiceTest {

    @Autowired
    private PartnerReservationService service;

    @Autowired
    private RoomStayService roomStayService;

    @Autowired
    private com.Travel.Buddy.service.property.PropertyApprovalService
            propertyService;

    @Autowired
    private com.Travel.Buddy.service.property.RoomTypeService
            roomTypeService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private HotelReservationRepository reservationRepository;

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

    private User guest;

    private Long propertyId;

    private Long deluxeRoomTypeId;

    private Integer stateId;

    @BeforeEach
    void setUp() {
        User admin = createUser("res-admin@test.travelbuddy");
        roleService.grantBaselineTravelerRole(admin);
        roleService.grant(admin, Role.ROLE_SUPER_ADMIN, null);

        partner = createUser("res-partner@test.travelbuddy");
        roleService.grantBaselineTravelerRole(partner);
        roleService.grant(partner, Role.ROLE_HOTEL_PARTNER, admin);

        guest = createUser("res-guest@test.travelbuddy");
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
            stateId = stateRepository.save(state).getStateId();
        }

        propertyId = propertyService.create(
                partner,
                new PropertyUpsertRequest(
                        "Arrival Hotel", PropertyType.HOTEL, stateId,
                        "Address", "Description",
                        new BigDecimal("19.8"), new BigDecimal("85.7")
                )
        ).propertyId();

        deluxeRoomTypeId = roomTypeService.create(
                partner.getUserId(), propertyId,
                new RoomTypeUpsertRequest(
                        "Deluxe", 2,
                        new BigDecimal("2500.00"), "INR", 3
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

    private Long addRoom(String number) {
        return roomStayService.addRoom(
                partner.getUserId(), propertyId, deluxeRoomTypeId,
                new PhysicalRoomRequest(number, "Floor 1", null)
        ).physicalRoomId();
    }

    private String reference() {
        return "TB-" + UUID.randomUUID().toString()
                .substring(0, 10).toUpperCase();
    }

    private Long booking(
            BookingStatus status, PaymentStatus payment,
            LocalDate checkIn, int roomsBooked
    ) {
        return booking(status, payment, checkIn,
                checkIn.plusDays(2), roomsBooked);
    }

    private Long booking(
            BookingStatus status, PaymentStatus payment,
            LocalDate checkIn, LocalDate checkOut, int roomsBooked
    ) {
        Booking booking = new Booking();

        booking.setUser(guest);
        booking.setBookingReference(reference());
        booking.setTotalAmount(new BigDecimal("7500.00"));
        booking.setCurrency("INR");
        booking.setBookingStatus(status);
        booking.setPaymentStatus(payment);
        booking.setGuestCount(2);
        booking.setGuestName("Arriving Guest");
        booking.setGuestEmail("arriving@test.travelbuddy");
        booking.setGuestPhone("9000000000");

        booking = bookingRepository.save(booking);

        HotelReservation reservation = new HotelReservation();
        reservation.setBooking(booking);
        reservation.setRoomType(
                roomTypeRepository.getReferenceById(deluxeRoomTypeId)
        );
        reservation.setCheckIn(checkIn);
        reservation.setCheckOut(checkOut);
        reservation.setRoomsBooked(roomsBooked);

        reservationRepository.save(reservation);

        return booking.getBookingId();
    }

    private List<PartnerReservationResponse> forPartner() {
        return service.listOpenReservations(partner.getUserId());
    }

    private PartnerReservationResponse one() {
        List<PartnerReservationResponse> all = forPartner();

        assertEquals(1, all.size(), "expected exactly one reservation");

        return all.get(0);
    }

    private Long assign(Long bookingId, Long roomId) {
        return roomStayService.assign(
                partner.getUserId(), bookingId, roomId
        ).stayId();
    }

    @Test
    @DisplayName("A confirmed booking with no room yet is visible")
    void unassignedBookingIsVisible() {
        booking(BookingStatus.CONFIRMED, PaymentStatus.PAID,
                LocalDate.now().plusDays(2), 1);

        PartnerReservationResponse r = one();

        assertTrue(r.canAssign(),
                "a paid confirmed booking awaiting a room must be "
                        + "assignable");
        assertEquals(1, r.roomsStillToAssign());
        assertFalse(r.fullyAssigned(),
                "a reservation with no room number is not assigned");
        assertNull(r.assignedRoomNumbers());
    }

    @Test
    @DisplayName("Assignment counts rooms, not bookings")
    void assignmentCountsEveryRoom() {
        Long bookingId = booking(BookingStatus.CONFIRMED,
                PaymentStatus.PAID, LocalDate.now().plusDays(2), 2);

        assign(bookingId, addRoom("101"));

        PartnerReservationResponse partial = one();

        assertEquals(1, partial.roomsAssigned(),
                "one of the two promised rooms has a number");
        assertEquals(1, partial.roomsStillToAssign(),
                "one room is still owed to this guest");
        assertFalse(partial.fullyAssigned(),
                "must not read as arranged until every promised room "
                        + "has a number");

        assign(bookingId, addRoom("102"));

        PartnerReservationResponse full = one();

        assertEquals(2, full.roomsAssigned());
        assertEquals(0, full.roomsStillToAssign());
        assertTrue(full.fullyAssigned());
        assertNotNull(full.assignedRoomNumbers());
    }

    @Test
    @DisplayName("A cancelled stay releases its room")
    void cancelledStayStopsCounting() {
        Long bookingId = booking(BookingStatus.CONFIRMED,
                PaymentStatus.PAID, LocalDate.now().plusDays(2), 1);

        Long stayId = assign(bookingId, addRoom("101"));

        assertTrue(one().fullyAssigned());

        roomStayService.unassign(partner.getUserId(), stayId);

        assertFalse(one().fullyAssigned(),
                "an unassigned stay must not keep reporting a room");
    }

    @Test
    @DisplayName("Settled bookings leave the queue")
    void settledBookingsAreNotListed() {
        booking(BookingStatus.CANCELLED, PaymentStatus.PAID,
                LocalDate.now().plusDays(2), 1);
        booking(BookingStatus.COMPLETED, PaymentStatus.PAID,
                LocalDate.now().minusDays(4), 1);

        assertTrue(forPartner().isEmpty(),
                "a booking needing no action must not sit in the "
                        + "pending list forever");
    }

    @Test
    @DisplayName("Another partner cannot see these guests")
    void reservationsArePartnerScoped() {
        booking(BookingStatus.CONFIRMED, PaymentStatus.PAID,
                LocalDate.now().plusDays(2), 1);

        User other = createUser("res-other@test.travelbuddy");
        roleService.grantBaselineTravelerRole(other);

        assertTrue(
                service.listOpenReservations(other.getUserId())
                        .isEmpty(),
                "a booking must only reach the partner who owns the "
                        + "property it is for");
    }

    @Test
    @DisplayName("Soonest arrival is listed first")
    void soonestArrivalComesFirst() {
        booking(BookingStatus.CONFIRMED, PaymentStatus.PAID,
                LocalDate.now().plusDays(9), 1);
        booking(BookingStatus.CONFIRMED, PaymentStatus.PAID,
                LocalDate.now().plusDays(2), 1);

        List<PartnerReservationResponse> all = forPartner();

        assertEquals(2, all.size());
        assertEquals(
                LocalDate.now().plusDays(2),
                all.get(0).checkIn(),
                "the guest arriving tomorrow is the one the front desk "
                        + "needs, not the one arriving next week");
    }

    @Test
    @DisplayName("An unpaid booking is not offered check-in")
    void unpaidBookingCannotCheckIn() {
        booking(BookingStatus.CONFIRMED, PaymentStatus.UNPAID,
                LocalDate.now().plusDays(2), 1);

        assertFalse(one().canCheckIn(),
                "check-in is gated on payment; the portal must not "
                        + "offer a button the backend will reject");
    }

    @Test
    @DisplayName("A same-day stay is one night, not zero")
    void sameDayIsOneNight() {
        booking(BookingStatus.CONFIRMED, PaymentStatus.PAID,
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(2), 1);

        assertEquals(1L, one().nights(),
                "a same-day guest still occupies the room and the "
                        + "front desk still has to prepare it");
    }

    @Test
    @DisplayName("An empty property has nothing pending")
    void noBookingsIsEmptyNotNull() {
        assertNotNull(forPartner());
        assertTrue(forPartner().isEmpty());
    }

    /* ============================================================
     * ACTION ELIGIBILITY — the positive paths
     *
     * The earlier cases proved the portal withholds actions it should
     * not offer. Without these, an over-strict condition would still
     * pass every one of them: "canCheckIn is always false" satisfies
     * "an unpaid booking cannot check in". These pin the other half.
     * ============================================================ */

    @Test
    @DisplayName("A paid, fully assigned guest can be checked in")
    void readyGuestCanCheckIn() {
        Long bookingId = booking(BookingStatus.CONFIRMED,
                PaymentStatus.PAID, LocalDate.now().plusDays(2), 1);

        assign(bookingId, addRoom("101"));

        PartnerReservationResponse r = one();

        assertTrue(r.canCheckIn(),
                "paid, room assigned, not yet arrived — check-in is the "
                        + "one action that must be offered here");
        assertFalse(r.canCheckOut(),
                "a guest who has not arrived cannot be departing");
        assertTrue(r.canMarkNoShow(),
                "an assigned guest who still might not show up");
    }

    @Test
    @DisplayName("A guest with a room still owed cannot be checked in")
    void halfArrangedGuestCannotCheckIn() {
        Long bookingId = booking(BookingStatus.CONFIRMED,
                PaymentStatus.PAID, LocalDate.now().plusDays(2), 2);

        assign(bookingId, addRoom("101"));

        PartnerReservationResponse partial = one();

        assertEquals(1, partial.roomsStillToAssign());
        assertFalse(partial.canCheckIn(),
                "checking in would strand the un-arrived room against "
                        + "this guest's departure date");
        assertTrue(partial.canAssign(),
                "assigning the second room is still available");
    }

    @Test
    @DisplayName("A checked-in guest can be checked out")
    void inHouseGuestCanCheckOut() {
        Long bookingId = booking(BookingStatus.CONFIRMED,
                PaymentStatus.PAID, LocalDate.now().plusDays(2), 1);

        Long stayId = assign(bookingId, addRoom("101"));

        roomStayService.checkIn(partner.getUserId(), stayId);

        PartnerReservationResponse r = one();

        assertTrue(r.canCheckOut(),
                "an in-house guest must be able to depart");
        assertFalse(r.canCheckIn(),
                "checking in twice is rejected by RoomStayService, so "
                        + "the portal must stop offering it");
        assertFalse(r.canMarkNoShow(),
                "a guest who is in house did not fail to show");
    }

    @Test
    @DisplayName("A no-show is no longer offered as a no-show")
    void noShowClearsTheNoShowAction() {
        Long bookingId = booking(BookingStatus.CONFIRMED,
                PaymentStatus.PAID, LocalDate.now().plusDays(2), 1);

        Long stayId = assign(bookingId, addRoom("101"));

        assertTrue(one().canMarkNoShow());

        roomStayService.markNoShow(partner.getUserId(), stayId);

        assertTrue(forPartner().isEmpty(),
                "a no-show needs no further action and must leave the "
                        + "queue, or it is reported as work forever");
    }

    @Test
    @DisplayName("Action flags agree with what the stay service allows")
    void flagsMatchStayService() {
        Long bookingId = booking(BookingStatus.CONFIRMED,
                PaymentStatus.PAID, LocalDate.now().plusDays(2), 1);

        Long stayId = assign(bookingId, addRoom("101"));

        /*
         * The portal offers what the backend accepts. If these ever
         * disagree, the front desk learns it by clicking a button that
         * fails — so assert the two views directly rather than each
         * against its own expectation.
         */
        PartnerReservationResponse listed = one();

        roomStayService.checkIn(partner.getUserId(), stayId);

        PartnerReservationResponse afterArrival = one();

        assertTrue(listed.canCheckIn(),
                "check-in is available before the guest arrives");

        assertFalse(afterArrival.canCheckIn(),
                "and unavailable once they have");

        assertTrue(afterArrival.canCheckOut(),
                "check-out becomes available at that same moment");
    }
}

