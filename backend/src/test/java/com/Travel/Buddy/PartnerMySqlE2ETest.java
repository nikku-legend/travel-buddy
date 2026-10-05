package com.Travel.Buddy;

import com.Travel.Buddy.dto.property.PropertyUpsertRequest;
import com.Travel.Buddy.dto.room.RoomTypeUpsertRequest;
import com.Travel.Buddy.dto.stay.PartnerReservationResponse;
import com.Travel.Buddy.dto.stay.PhysicalRoomRequest;
import com.Travel.Buddy.entity.*;
import com.Travel.Buddy.repository.*;
import com.Travel.Buddy.service.partner.RoleService;
import com.Travel.Buddy.service.stay.PartnerReservationService;
import com.Travel.Buddy.service.stay.RoomStayService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The partner portals against a genuine MySQL 8 server. (FR-22, FR-23)
 *
 * <p>Deliberately NOT {@code @Transactional}. That is the whole point:
 * the double-booking guarantee rests on {@code PESSIMISTIC_WRITE} row
 * locking, and a lock cannot be demonstrated from inside one long
 * transaction that never commits. Here each write commits, so two
 * threads genuinely contend for the same {@code physical_rooms} row.
 *
 * <p>This is the check the pom says must be proven "before shipping" and
 * the one H2 structurally cannot make: H2 in MySQL-compatibility mode
 * emulates the dialect, not InnoDB's locking.
 *
 * <p>Rows are not rolled back between tests, so every test scopes its
 * assertions to data it created in that test.
 *
 * <p><b>This suite found a real bug.</b> With {@code PESSIMISTIC_WRITE}
 * in place and the default REPEATABLE READ isolation, two desks still
 * double-booked the same room, because the loser was blocked before the
 * winner committed and so read a snapshot that predated the winner's
 * stay. {@code assign} now runs at READ_COMMITTED. Both tests below fail
 * if either half of that fix is reverted.
 *
 * <p><b>Opt-in on purpose.</b> Surefire's default includes match this
 * class ({@code *Test}), so without a guard a contributor with no MySQL
 * would see their build fail on a connection error.
 *
 * <p>Run it with:
 * <pre>
 *   mvn test -Dtravelbuddy.mysql.e2e=true -Dtest=PartnerMySqlE2ETest
 * </pre>
 */
@EnabledIfSystemProperty(
        named = "travelbuddy.mysql.e2e",
        matches = "true"
)
@SpringBootTest
@ActiveProfiles("mysql-e2e")
@DisplayName("Partner portals on real MySQL (FR-22, FR-23)")
class PartnerMySqlE2ETest {

    @Autowired
    private RoomStayService roomStayService;

    @Autowired
    private PartnerReservationService reservationService;

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
    private PhysicalRoomRepository physicalRoomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private StateRepository stateRepository;

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager
            transactionManager;

    private User partner;

    private User guest;

    private Long propertyId;

    private Long roomTypeId;

    private Integer stateId;

    @BeforeEach
    void setUp() {
        User admin = createUser("admin");
        roleService.grantBaselineTravelerRole(admin);
        roleService.grant(admin, Role.ROLE_SUPER_ADMIN, null);

        partner = createUser("partner");
        roleService.grantBaselineTravelerRole(partner);
        roleService.grant(partner, Role.ROLE_HOTEL_PARTNER, admin);

        guest = createUser("guest");
        roleService.grantBaselineTravelerRole(guest);

        /*
         * Reuse the geography V15 already seeded rather than inserting
         * a fresh country per test. countries.iso_code is unique, so
         * inserting a random code every run is a 1-in-256 collision
         * that eventually fails the suite -- and it leaks rows on every
         * run besides. The property is still unique per test via
         * suffix().
         */
        Country country = countryRepository
                .findByIsoCode("IN")
                .orElseGet(() -> {
                    Country fresh = new Country();
                    fresh.setName("E2Eland");
                    fresh.setIsoCode("IN");
                    return countryRepository.save(fresh);
                });

        State state = new State();
        state.setName("E2E State " + suffix());
        state.setCountry(country);
        state.setRegionZone(RegionZone.EAST);
        stateId = stateRepository.save(state).getStateId();

        propertyId = propertyService.create(
                partner,
                new PropertyUpsertRequest(
                        "MySQL Hotel " + suffix(), PropertyType.HOTEL,
                        stateId, "Address", "Description",
                        new BigDecimal("19.8"), new BigDecimal("85.7")
                )
        ).propertyId();

        roomTypeId = roomTypeService.create(
                partner.getUserId(), propertyId,
                new RoomTypeUpsertRequest(
                        "Deluxe", 2,
                        new BigDecimal("2500.00"), "INR", 3
                )
        ).roomTypeId();
    }

    private String suffix() {
        return UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase();
    }

    /**
     * Each test method gets its own users.
     *
     * <p>These tests commit, so without a per-invocation suffix the
     * second test would collide with the first on the unique index on
     * {@code users.email} and fail during setup -- hiding whatever it
     * was actually trying to prove.
     */
    private User createUser(String label) {
        User user = new User();
        user.setFullName("E2E User");
        user.setEmail("mysql-e2e-" + label + "-" + suffix()
                + "@test.travelbuddy");
        user.setPasswordHash("{noop}password");
        return userRepository.save(user);
    }

    private Long addRoom(String number) {
        return roomStayService.addRoom(
                partner.getUserId(), propertyId, roomTypeId,
                new PhysicalRoomRequest(number, "Floor 1", null)
        ).physicalRoomId();
    }

    private Long booking(LocalDate checkIn, LocalDate checkOut, int rooms) {
        Booking booking = new Booking();
        booking.setUser(guest);
        booking.setBookingReference("TB-" + suffix());
        booking.setTotalAmount(new BigDecimal("7500.00"));
        booking.setCurrency("INR");
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setGuestCount(2);
        booking.setGuestName("MySQL Guest");
        booking.setGuestEmail("guest@test.travelbuddy");
        booking.setGuestPhone("9000000000");

        booking = bookingRepository.save(booking);

        HotelReservation reservation = new HotelReservation();
        reservation.setBooking(booking);
        reservation.setRoomType(roomTypeRepository.getReferenceById(roomTypeId));
        reservation.setCheckIn(checkIn);
        reservation.setCheckOut(checkOut);
        reservation.setRoomsBooked(rooms);

        reservationRepository.save(reservation);

        return booking.getBookingId();
    }

    private PartnerReservationResponse reservationFor(Long bookingId) {
        return reservationService
                .listOpenReservations(partner.getUserId())
                .stream()
                .filter(r -> r.bookingId().equals(bookingId))
                .findFirst()
                .orElse(null);
    }

    /** Live stays actually occupying a room, read from the database. */
    private int liveStaysIn(Long roomId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM room_stays "
                        + "WHERE physical_room_id = ? AND status IN "
                        + "('ASSIGNED','CHECKED_IN')",
                Integer.class, roomId
        );
    }

    /**
     * One front-desk terminal trying to take a room.
     *
     * <p>Records which way it went so the caller can tell "was refused
     * because the room was taken" (correct) from "neither thread ran"
     * (a broken test).
     */
    private Runnable assignAttempt(
            Long bookingId,
            Long roomId,
            AtomicInteger granted,
            AtomicInteger refused
    ) {
        return () -> {
            try {
                roomStayService.assign(
                        partner.getUserId(), bookingId, roomId
                );
                granted.incrementAndGet();
            } catch (Exception refused_) {
                refused.incrementAndGet();
            }
        };
    }

    /**
     * Waits for the starting gun, then races for the room.
     *
     * <p>A refusal is swallowed deliberately: two guests wanting one
     * room is the situation under test, and exactly one losing is the
     * correct outcome. The database is the judge.
     */
    private Runnable racingAttempt(
            Long bookingId,
            Long roomId,
            CountDownLatch startGun,
            CountDownLatch done
    ) {
        return () -> {
            try {
                startGun.await();

                roomStayService.assign(
                        partner.getUserId(), bookingId, roomId
                );
            } catch (Exception expected) {
                // Refused because the room was taken. Correct.
            } finally {
                done.countDown();
            }
        };
    }

    @Test
    @DisplayName("A booking arrives, is assigned, checked in and out")
    void fullArrivalLifecycle() {
        LocalDate checkIn = LocalDate.now().plusDays(2);
        Long bookingId = booking(checkIn, checkIn.plusDays(2), 1);
        Long roomId = addRoom("101");

        /* Arrives with nothing assigned. */
        PartnerReservationResponse arrived =
                reservationFor(bookingId);

        assertNotNull(arrived,
                "a paid confirmed booking must reach the portal");
        assertEquals(1, arrived.roomsStillToAssign());
        assertTrue(arrived.canAssign());
        assertFalse(arrived.canCheckIn(),
                "there is no room yet, so there is nothing to check into");

        /* Front desk gives it room 101. */
        Long stayId = roomStayService.assign(
                partner.getUserId(), bookingId, roomId
        ).stayId();

        PartnerReservationResponse assigned =
                reservationFor(bookingId);

        assertEquals(1, assigned.roomsAssigned());
        assertTrue(assigned.fullyAssigned());
        assertTrue(assigned.assignedRoomNumbers().contains("101"));
        assertTrue(assigned.canCheckIn(),
                "paid, room assigned, not yet arrived -- check-in must be "
                        + "offered");

        /* Guest arrives. */
        roomStayService.checkIn(partner.getUserId(), stayId);

        PartnerReservationResponse inHouse = reservationFor(bookingId);

        assertTrue(inHouse.canCheckOut());
        assertFalse(inHouse.canCheckIn(),
                "checking in twice is rejected, so it must stop being "
                        + "offered");

        /* Guest departs. */
        roomStayService.checkOut(partner.getUserId(), stayId);

        PartnerReservationResponse departed = reservationFor(bookingId);

        assertNull(departed,
                "a departed guest has no outstanding work and must leave "
                        + "the arrival list");

        Integer stays = jdbc.queryForObject(
                "SELECT COUNT(*) FROM room_stays WHERE booking_id = ?",
                Integer.class, bookingId
        );

        assertEquals(1, stays,
                "the stay record must survive check-out as history");
    }

    @Test
    @DisplayName("Two front desks cannot hand the same room to two guests")
    void concurrentAssignmentsSerialise() throws Exception {
        /*
         * Deterministically prove the lock serialises the two writers.
         *
         * <p>A plain "fire two threads and hope they overlap" test is
         * worthless here: one thread usually finishes before the other
         * is even scheduled, so it passes whether or not the lock
         * exists. Confirmed by mutation -- removing PESSIMISTIC_WRITE
         * still passed such a test.
         *
         * <p>So a third transaction takes the room-row lock and holds
         * it. Both front-desk threads are then released together and
         * MUST queue on that row, because that lock is the only thing
         * between them and a double booking. When the holder lets go,
         * exactly one may proceed and the other must observe the
         * committed stay and be refused.
         */
        LocalDate checkIn = LocalDate.now().plusDays(3);

        Long roomId = addRoom("201");
        Long bookingA = booking(checkIn, checkIn.plusDays(2), 1);
        Long bookingB = booking(checkIn, checkIn.plusDays(2), 1);

        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        /*
         * On its own thread: the holder blocks on a latch, so it cannot
         * sit on the test thread.
         */
        Thread holder = new Thread(() ->
                new org.springframework.transaction.support
                        .TransactionTemplate(transactionManager)
                        .execute(status -> {
                            physicalRoomRepository
                                    .findByIdForUpdate(roomId);

                            lockHeld.countDown();

                            try {
                                releaseLock.await(30, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }

                            return null;
                        })
        );

        holder.start();

        assertTrue(lockHeld.await(30, TimeUnit.SECONDS),
                "the blocking transaction must take the room lock");

        AtomicInteger granted = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();

        Runnable first = assignAttempt(
                bookingA, roomId, granted, refused
        );

        Runnable second = assignAttempt(
                bookingB, roomId, granted, refused
        );

        ExecutorService pool = Executors.newFixedThreadPool(2);

        pool.submit(first);
        pool.submit(second);

        /*
         * Let both reach the lock the holder owns, so it is released
         * while they are genuinely queued rather than before they ran.
         */
        Thread.sleep(750);

        releaseLock.countDown();

        pool.shutdown();

        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS),
                "both assignments must finish");

        holder.join(30_000);

        assertEquals(1, granted.get(),
                "exactly one guest may be given room 201");
        assertEquals(1, refused.get(),
                "the loser must be told, not silently double-booked");

        assertEquals(1, liveStaysIn(roomId),
                "the database itself must hold exactly one live stay for "
                        + "this room");
    }

    @Test
    @DisplayName("Repeated simultaneous races never double-book")
    void repeatedRacesNeverDoubleBook() throws Exception {
        /*
         * Catches the unlocked build that the single-race test misses.
         *
         * <p>Without the lock, a double booking is a timing race rather
         * than a certainty: one round can pass by luck. Enough rounds
         * against fresh rooms and the unlocked build cannot keep
         * winning, which is what makes this a real regression guard
         * rather than a formality.
         */
        LocalDate checkIn = LocalDate.now().plusDays(8);
        int rounds = 12;

        for (int round = 0; round < rounds; round++) {
            String number = "S" + round;
            Long roomId = addRoom(number);
            Long bookingA = booking(checkIn, checkIn.plusDays(2), 1);
            Long bookingB = booking(checkIn, checkIn.plusDays(2), 1);

            CountDownLatch startGun = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);

            Runnable first = racingAttempt(bookingA, roomId, startGun, done);

            Runnable second = racingAttempt(bookingB, roomId, startGun, done);

            ExecutorService pool = Executors.newFixedThreadPool(2);

            pool.submit(first);
            pool.submit(second);

            startGun.countDown();

            assertTrue(done.await(60, TimeUnit.SECONDS));

            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);

            assertEquals(1, liveStaysIn(roomId),
                    "round " + round + " put " + liveStaysIn(roomId)
                            + " guests in room " + number);
        }
    }

    @Test
    @DisplayName("A multi-room booking needs every room before check-in")
    void multiRoomBookingCountsRooms() {
        LocalDate checkIn = LocalDate.now().plusDays(4);

        Long bookingId = booking(checkIn, checkIn.plusDays(2), 2);
        Long first = addRoom("301");
        Long second = addRoom("302");

        roomStayService.assign(
                partner.getUserId(), bookingId, first
        );

        PartnerReservationResponse partial =
                reservationFor(bookingId);

        assertEquals(1, partial.roomsAssigned());
        assertEquals(1, partial.roomsStillToAssign(),
                "one of the two promised rooms still has no number");
        assertFalse(partial.canCheckIn(),
                "checking in half a booking strands the other room "
                        + "against this guest's departure");

        roomStayService.assign(
                partner.getUserId(), bookingId, second
        );

        PartnerReservationResponse full = reservationFor(bookingId);

        assertEquals(2, full.roomsAssigned());
        assertEquals(0, full.roomsStillToAssign());
        assertTrue(full.canCheckIn());
    }

    @Test
    @DisplayName("Releasing a room makes it bookable again")
    void unassignReturnsRoomToPool() {
        LocalDate checkIn = LocalDate.now().plusDays(5);
        Long bookingId = booking(checkIn, checkIn.plusDays(2), 1);
        Long roomId = addRoom("401");

        Long stayId = roomStayService.assign(
                partner.getUserId(), bookingId, roomId
        ).stayId();

        assertTrue(reservationFor(bookingId).fullyAssigned());

        roomStayService.unassign(partner.getUserId(), stayId);

        PartnerReservationResponse released =
                reservationFor(bookingId);

        assertFalse(released.fullyAssigned(),
                "an unassigned stay must stop reporting a room");
        assertEquals(1, released.roomsStillToAssign());
        assertTrue(released.canAssign());
    }

    @Test
    @DisplayName("Another partner cannot see or touch these bookings")
    void partnerScopingHolds() {
        LocalDate checkIn = LocalDate.now().plusDays(6);
        booking(checkIn, checkIn.plusDays(2), 1);

        User intruder = createUser("intruder");
        roleService.grantBaselineTravelerRole(intruder);
        roleService.grant(intruder, Role.ROLE_HOTEL_PARTNER, null);

        assertTrue(
                reservationService
                        .listOpenReservations(intruder.getUserId())
                        .isEmpty(),
                "a booking must only reach the partner who owns the "
                        + "property it is for");

        List<com.Travel.Buddy.dto.stay.RoomStayResponse> theirDesk =
                roomStayService.frontDeskQueue(intruder.getUserId());

        assertTrue(theirDesk.isEmpty(),
                "and must not leak through the front-desk queue either");
    }
}