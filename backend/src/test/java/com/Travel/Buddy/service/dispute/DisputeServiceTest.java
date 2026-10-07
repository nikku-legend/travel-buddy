package com.Travel.Buddy.service.dispute;

import com.Travel.Buddy.dto.dispute.ChangeDisputeStatusRequest;
import com.Travel.Buddy.dto.dispute.DisputePageResponse;
import com.Travel.Buddy.dto.dispute.DisputeQueueResponse;
import com.Travel.Buddy.dto.dispute.DisputeResponse;
import com.Travel.Buddy.dto.dispute.RaiseDisputeRequest;
import com.Travel.Buddy.dto.dispute.ResolveDisputeRequest;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dispute lifecycle. (FR-28)
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Disputes (FR-28)")
class DisputeServiceTest {

    @Autowired
    private DisputeService disputeService;

    @Autowired
    private DisputeRepository disputeRepository;

    @Autowired
    private DisputeTimelineRepository timelineRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private HotelReservationRepository reservationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleService roleService;

    private User traveller;
    private User other;
    private User admin;
    private User partner;
    private Property property;
    private RoomType roomType;

    /*
     * Set once for the shared Spring context. Re-seeding geo rows
     * per test collides on the unique country iso_code.
     */
    private static Integer stateId;

    @BeforeEach
    void setUp() {
        traveller = user("Traveller");
        other = user("Nosy Neighbour");
        admin = user("Admin");
        partner = user("Hotel Owner");

        roleService.grant(
                admin, Role.ROLE_SUPER_ADMIN, admin
        );
        roleService.grant(
                partner, Role.ROLE_HOTEL_PARTNER, admin
        );

        /*
         * Geo rows are shared across the whole context. countries
         * has a unique iso_code, so re-inserting "IN" per test
         * would fail after the first one; a unique state name per
         * test still keeps the properties independent.
         */
        if (stateId == null) {
            Country country = new Country();
            country.setName("Testland");
            country.setIsoCode("TB");
            country = countryRepository.save(country);

            State state = new State();
            state.setName("Test State");
            state.setCountry(country);
            state.setRegionZone(RegionZone.EAST);
            state = stateRepository.save(state);

            stateId = state.getStateId();
        }

        property = new Property();
        property.setName("Dispute Inn");
        property.setPropertyType(PropertyType.HOTEL);
        property.setState(stateRepository.findById(stateId)
                .orElseThrow());
        property.setAddress("Somewhere");
        property.setDescription("A place");
        property.setLatitude(new BigDecimal("19.8"));
        property.setLongitude(new BigDecimal("85.7"));
        property.setPartner(partner);
        property = propertyRepository.save(property);

        /*
         * Built directly rather than through RoomTypeService so the
         * fixture does not drag in a second service and its own
         * authorisation rules. Nothing under test here cares how the
         * room type was created, only that the booking points at one.
         */
        RoomType room = new RoomType();
        room.setProperty(property);
        room.setCategoryName("Deluxe");
        room.setMaxOccupancy(2);
        room.setBasePrice(new BigDecimal("2500.00"));
        room.setCurrency("INR");
        room.setTotalInventory(5);
        roomType = roomTypeRepository.save(room);
    }

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private StateRepository stateRepository;

    private User user(String name) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(UUID.randomUUID() + "@tb.local");
        user.setPasswordHash("{noop}password");
        return userRepository.save(user);
    }

    /**
     * A paid booking with a checkout date in the past but inside
     * the claim window, which is the normal case for a complaint.
     */
    private Booking paidBooking(
            User owner,
            BookingStatus status,
            PaymentStatus payment,
            LocalDate checkout
    ) {
        Booking booking = new Booking();
        booking.setUser(owner);
        booking.setBookingReference("TB-" + UUID.randomUUID()
                .toString().substring(0, 8).toUpperCase());
        booking.setTotalAmount(new BigDecimal("5000.00"));
        booking.setCurrency("INR");
        booking.setBookingStatus(status);
        booking.setPaymentStatus(payment);
        booking.setGuestCount(2);
        booking.setGuestName(owner.getFullName());
        booking.setGuestEmail(owner.getEmail());
        booking = bookingRepository.save(booking);

        HotelReservation reservation = new HotelReservation();
        reservation.setBooking(booking);
        reservation.setRoomType(roomType);
        reservation.setCheckIn(checkout.minusDays(2));
        reservation.setCheckOut(checkout);
        reservation.setRoomsBooked(1);
        reservationRepository.save(reservation);

        return booking;
    }

    private Booking claimableBooking() {
        return claimableBookingFor(traveller);
    }

    /**
     * A completed, paid stay owned by {@code owner}. Takes the
     * owner explicitly so a test can give two different travellers
     * a claimable booking each.
     */
    private Booking claimableBookingFor(User owner) {
        return paidBooking(
                owner,
                BookingStatus.COMPLETED,
                PaymentStatus.PAID,
                LocalDate.now().minusDays(2)
        );
    }

    private RaiseDisputeRequest claim(
            DisputeCategory category,
            BigDecimal amount
    ) {
        return new RaiseDisputeRequest(
                category,
                "The room was not as advertised",
                "The air conditioning was broken for the whole "
                        + "stay and the room was damp.",
                amount
        );
    }

    private RaiseDisputeRequest standardClaim() {
        return claim(
                DisputeCategory.PROPERTY_NOT_AS_DESCRIBED,
                new BigDecimal("2500.00")
        );
    }

    /* ============================================================
     * RAISING
     * ============================================================ */

    @Test
    @DisplayName("a traveller can dispute a paid stay they completed")
    void raisesDispute() {
        Booking booking = claimableBooking();

        DisputeResponse response = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );

        assertNotNull(response.disputeId());
        assertEquals(DisputeStatus.OPEN, response.status());
        assertEquals(
                new BigDecimal("2500.00"),
                response.requestedAmount()
        );
        assertTrue(response.canWithdraw());
    }

    @Test
    @DisplayName("the partner is captured at the moment of the claim")
    void capturesThePartner() {
        Booking booking = claimableBooking();

        DisputeResponse response = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );

        assertEquals(
                partner.getUserId(), property.getPartner().getUserId()
        );
        assertNotNull(response.partnerName());
    }

    @Test
    @DisplayName("raising a dispute writes the first audit entry")
    void writesRaisedTimelineEntry() {
        Booking booking = claimableBooking();

        DisputeResponse response = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );

        var entries = timelineRepository
                .findByDispute_DisputeIdOrderByCreatedAtAsc(
                        response.disputeId()
                );

        assertEquals(1, entries.size());
        assertEquals(
                DisputeEventType.RAISED,
                entries.get(0).getEventType()
        );
        assertNull(entries.get(0).getFromStatus());
        assertEquals("OPEN", entries.get(0).getToStatus());
        assertEquals(
                traveller.getUserId(),
                entries.get(0).getActor().getUserId()
        );
    }

    /* ============================================================
     * WHO MAY RAISE
     * ============================================================ */

    @Test
    @DisplayName("you cannot dispute someone else's booking")
    void cannotDisputeAnotherTravellersBooking() {
        Booking booking = claimableBooking();

        PartnerApplicationException error = assertThrows(
                PartnerApplicationException.class,
                () -> disputeService.raise(
                        other.getUserId(),
                        booking.getBookingId(),
                        standardClaim()
                )
        );

        assertTrue(error.getMessage().contains("not yours"));
    }

    @Test
    @DisplayName("an unpaid booking cannot be disputed")
    void unpaidBookingCannotBeDisputed() {
        Booking booking = paidBooking(
                traveller,
                BookingStatus.PENDING,
                PaymentStatus.UNPAID,
                LocalDate.now().minusDays(2)
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> disputeService.raise(
                        traveller.getUserId(),
                        booking.getBookingId(),
                        standardClaim()
                )
        );
    }

    @Test
    @DisplayName("you cannot claim more than you paid")
    void cannotClaimMoreThanPaid() {
        Booking booking = claimableBooking();

        assertThrows(
                PartnerApplicationException.class,
                () -> disputeService.raise(
                        traveller.getUserId(),
                        booking.getBookingId(),
                        claim(
                                DisputeCategory.OVERCHARGING,
                                new BigDecimal("50000.00")
                        )
                )
        );
    }

    @Test
    @DisplayName("a negative claim is rejected")
    void negativeClaimRejected() {
        Booking booking = claimableBooking();

        assertThrows(
                PartnerApplicationException.class,
                () -> disputeService.raise(
                        traveller.getUserId(),
                        booking.getBookingId(),
                        claim(
                                DisputeCategory.OTHER,
                                new BigDecimal("-100.00")
                        )
                )
        );
    }

    /**
     * The anti-spam rule. Without it a partner can be buried under
     * duplicate claims about the same trip.
     */
    @Test
    @DisplayName("a second dispute on the same booking is refused")
    void oneOpenDisputePerBooking() {
        Booking booking = claimableBooking();

        disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> disputeService.raise(
                        traveller.getUserId(),
                        booking.getBookingId(),
                        claim(
                                DisputeCategory.CLEANLINESS,
                                new BigDecimal("100.00")
                        )
                )
        );
    }

    @Test
    @DisplayName("a closed dispute does not block a later new claim")
    void closedDisputeDoesNotBlockANewOne() {
        Booking booking = claimableBooking();

        DisputeResponse first = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );
        disputeService.withdraw(
                traveller.getUserId(), first.disputeId()
        );

        DisputeResponse second = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );

        assertNotNull(second.disputeId());
        assertFalse(first.disputeId().equals(second.disputeId()));
    }

    @Test
    @DisplayName("the claim window closes 30 days after checkout")
    void claimWindowIsEnforced() {
        Booking old = paidBooking(
                traveller,
                BookingStatus.COMPLETED,
                PaymentStatus.PAID,
                LocalDate.now().minusDays(45)
        );

        PartnerApplicationException error = assertThrows(
                PartnerApplicationException.class,
                () -> disputeService.raise(
                        traveller.getUserId(),
                        old.getBookingId(),
                        standardClaim()
                )
        );

        assertTrue(error.getMessage().contains("window"));
    }

    @Test
    @DisplayName("a stay still in progress cannot be disputed yet")
    void cannotDisputeBeforeCheckout() {
        /*
         * The window runs from checkout, so a stay that has not
         * ended yet is outside it entirely.
         */
        Booking upcoming = paidBooking(
                traveller,
                BookingStatus.CONFIRMED,
                PaymentStatus.PAID,
                LocalDate.now().plusDays(3)
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> disputeService.raise(
                        traveller.getUserId(),
                        upcoming.getBookingId(),
                        standardClaim()
                )
        );
    }

    /* ============================================================
     * WITHDRAWAL
     * ============================================================ */

    @Test
    @DisplayName("a claimant can withdraw their own open dispute")
    void claimantCanWithdraw() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );

        DisputeResponse withdrawn = disputeService.withdraw(
                traveller.getUserId(), dispute.disputeId()
        );

        assertEquals(DisputeStatus.WITHDRAWN, withdrawn.status());
    }

    @Test
    @DisplayName("you cannot withdraw someone else's dispute")
    void cannotWithdrawAnotherDispute() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> disputeService.withdraw(
                        other.getUserId(), dispute.disputeId()
                )
        );
    }

    @Test
    @DisplayName("withdrawal is refused once a moderator has taken it on")
    void cannotWithdrawAfterReviewStarts() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );
        disputeService.claim(
                admin.getUserId(), dispute.disputeId(), null
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> disputeService.withdraw(
                        traveller.getUserId(), dispute.disputeId()
                )
        );
    }

    /* ============================================================
     * ADMIN WORKFLOW
     * ============================================================ */

    @Test
    @DisplayName("claiming a dispute moves it under review and assigns it")
    void claimingAssignsAndStartsReview() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );

        DisputeResponse claimed = disputeService.claim(
                admin.getUserId(),
                dispute.disputeId(),
                "Looking into it"
        );

        assertEquals(DisputeStatus.UNDER_REVIEW, claimed.status());
        assertNotNull(claimed.assignedToName());
        assertNotNull(claimed.assignedAt());
    }

    @Test
    @DisplayName("claiming records an ASSIGNED entry on the trail")
    void claimingIsRecorded() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );
        disputeService.claim(
                admin.getUserId(), dispute.disputeId(), null
        );

        var entries = timelineRepository
                .findByDispute_DisputeIdOrderByCreatedAtAsc(
                        dispute.disputeId()
                );

        assertEquals(2, entries.size());
        assertEquals(
                DisputeEventType.ASSIGNED,
                entries.get(1).getEventType()
        );
        assertEquals("OPEN", entries.get(1).getFromStatus());
        assertEquals("UNDER_REVIEW", entries.get(1).getToStatus());
    }

    @Test
    @DisplayName("a second moderator cannot steal an assigned dispute")
    void cannotClaimAnAssignedDispute() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );
        disputeService.claim(
                admin.getUserId(), dispute.disputeId(), null
        );
        User secondAdmin = user("Second Admin");
        roleService.grant(
                secondAdmin, Role.ROLE_SUPER_ADMIN, secondAdmin
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> disputeService.claim(
                        secondAdmin.getUserId(),
                        dispute.disputeId(),
                        null
                )
        );
    }

    @Test
    @DisplayName("a dispute can be sent back to the partner for a response")
    void canAwaitPartnerResponse() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );
        disputeService.claim(
                admin.getUserId(), dispute.disputeId(), null
        );

        DisputeResponse moved = disputeService.changeStatus(
                admin.getUserId(),
                dispute.disputeId(),
                new ChangeDisputeStatusRequest(
                        DisputeStatus.AWAITING_PARTNER_RESPONSE,
                        "Asking the hotel for their account"
                )
        );

        assertEquals(
                DisputeStatus.AWAITING_PARTNER_RESPONSE,
                moved.status()
        );
    }

    @Test
    @DisplayName("a nonsense transition is refused")
    void refusesNonsenseTransitions() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );

        /* OPEN cannot jump straight to AWAITING_PARTNER_RESPONSE. */
        assertThrows(
                PartnerApplicationException.class,
                () -> disputeService.changeStatus(
                        admin.getUserId(),
                        dispute.disputeId(),
                        new ChangeDisputeStatusRequest(
                                DisputeStatus
                                        .AWAITING_PARTNER_RESPONSE,
                                null
                        )
                )
        );
    }

    @Test
    @DisplayName("a dispute cannot be closed through the status endpoint")
    void cannotCloseThroughStatusEndpoint() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );

        /*
         * Closing must go through resolve, so that a ruling and an
         * amount are always recorded together.
         */
        assertThrows(
                PartnerApplicationException.class,
                () -> disputeService.changeStatus(
                        admin.getUserId(),
                        dispute.disputeId(),
                        new ChangeDisputeStatusRequest(
                                DisputeStatus.RESOLVED, null
                        )
                )
        );
    }

    /* ============================================================
     * RULING
     * ============================================================ */

    @Test
    @DisplayName("a full refund ruling closes dispute without claiming disbursement")
    void resolvesWithFullRefundRuling() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );

        DisputeResponse resolved = disputeService.resolve(
                admin.getUserId(),
                dispute.disputeId(),
                new ResolveDisputeRequest(
                        DisputeResolution.FULL_REFUND,
                        new BigDecimal("2500.00"),
                        "Photos confirm the room was unusable",
                        null
                )
        );

        assertEquals(DisputeStatus.RESOLVED, resolved.status());
        assertEquals(
                DisputeResolution.FULL_REFUND,
                resolved.resolution()
        );
        assertEquals(new BigDecimal("2500.00"),
                resolved.resolvedAmount());
        assertNotNull(resolved.resolvedAt());

        assertEquals(
                PaymentStatus.PAID,
                bookingRepository.findById(booking.getBookingId())
                        .orElseThrow()
                        .getPaymentStatus()
        );

        String notice = notificationRepository
                .findAll()
                .stream()
                .filter(notification ->
                        notification.getUser().getUserId()
                                .equals(traveller.getUserId())
                                && notification.getType()
                                == NotificationType.DISPUTE_RESOLVED
                )
                .map(com.Travel.Buddy.entity.Notification::getBody)
                .filter(body -> body != null
                        && body.contains("approved"))
                .findFirst()
                .orElse("");
        assertTrue(notice.contains("approved"));
        assertTrue(notice.contains("not yet been confirmed as disbursed"));
        assertFalse(notice.contains("You were refunded"));
    }

    @Test
    @DisplayName("a ruling may not exceed the amount claimed")
    void cannotAwardMoreThanClaimed() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                claim(
                        DisputeCategory.CLEANLINESS,
                        new BigDecimal("500.00")
                )
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> disputeService.resolve(
                        admin.getUserId(),
                        dispute.disputeId(),
                        new ResolveDisputeRequest(
                                DisputeResolution.PARTIAL_REFUND,
                                new BigDecimal("4000.00"),
                                "Trying to overpay myself",
                                null
                        )
                )
        );
    }

    @Test
    @DisplayName("a refund without a written reason is refused")
    void refundRequiresAReason() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> disputeService.resolve(
                        admin.getUserId(),
                        dispute.disputeId(),
                        new ResolveDisputeRequest(
                                DisputeResolution.FULL_REFUND,
                                new BigDecimal("2500.00"),
                                "  ",
                                null
                        )
                )
        );
    }

    @Test
    @DisplayName("a refund must state an amount")
    void refundRequiresAnAmount() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> disputeService.resolve(
                        admin.getUserId(),
                        dispute.disputeId(),
                        new ResolveDisputeRequest(
                                DisputeResolution.PARTIAL_REFUND,
                                null,
                                "Because",
                                null
                        )
                )
        );
    }

    @Test
    @DisplayName("a NO_REFUND ruling moves no money")
    void noRefundMovesNoMoney() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );

        DisputeResponse resolved = disputeService.resolve(
                admin.getUserId(),
                dispute.disputeId(),
                new ResolveDisputeRequest(
                        DisputeResolution.NO_REFUND,
                        new BigDecimal("0.00"),
                        "The photos predate the stay",
                        null
                )
        );

        assertEquals(DisputeStatus.RESOLVED, resolved.status());
        assertEquals(
                BigDecimal.ZERO, resolved.resolvedAmount()
        );
        assertEquals(
                PaymentStatus.PAID,
                bookingRepository.findById(booking.getBookingId())
                        .orElseThrow()
                        .getPaymentStatus(),
                "the traveller must still have paid"
        );
    }

    /**
     * A fair complaint that needs no money back is a real outcome.
     * Recording it as REJECTED would misreport the ruling.
     */
    @Test
    @DisplayName("a dispute can be resolved with no refund, distinct from a rejection")
    void resolvedWithoutRefundIsNotARejection() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );

        DisputeResponse resolved = disputeService.resolve(
                admin.getUserId(),
                dispute.disputeId(),
                new ResolveDisputeRequest(
                        DisputeResolution.CREDIT_NOTE,
                        BigDecimal.ZERO,
                        "A credit has been applied to your next stay",
                        null
                )
        );

        assertEquals(DisputeStatus.RESOLVED, resolved.status());
        assertEquals(
                DisputeResolution.CREDIT_NOTE,
                resolved.resolution()
        );
    }

    @Test
    @DisplayName("rejecting a dispute requires a reason and records nothing paid")
    void rejectingRequiresAReason() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> disputeService.resolve(
                        admin.getUserId(),
                        dispute.disputeId(),
                        new ResolveDisputeRequest(
                                null, null, null, true
                        )
                )
        );
    }

    @Test
    @DisplayName("a rejected dispute is closed with its reason")
    void rejectingClosesTheDispute() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );

        DisputeResponse rejected = disputeService.resolve(
                admin.getUserId(),
                dispute.disputeId(),
                new ResolveDisputeRequest(
                        null, null,
                        "The claim was raised after the fact",
                        true
                )
        );

        assertEquals(DisputeStatus.REJECTED, rejected.status());
        assertNotNull(rejected.resolvedAt());
    }

    @Test
    @DisplayName("a closed dispute cannot be ruled on again")
    void cannotRuleTwice() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );
        disputeService.resolve(
                admin.getUserId(),
                dispute.disputeId(),
                new ResolveDisputeRequest(
                        DisputeResolution.FULL_REFUND,
                        new BigDecimal("2500.00"),
                        "Approved",
                        null
                )
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> disputeService.resolve(
                        admin.getUserId(),
                        dispute.disputeId(),
                        new ResolveDisputeRequest(
                                DisputeResolution.FULL_REFUND,
                                new BigDecimal("2500.00"),
                                "Approved again",
                                null
                        )
                )
        );
    }

    @Test
    @DisplayName("a withdrawn dispute cannot be ruled on")
    void cannotRuleOnWithdrawn() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );
        disputeService.withdraw(
                traveller.getUserId(), dispute.disputeId()
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> disputeService.resolve(
                        admin.getUserId(),
                        dispute.disputeId(),
                        new ResolveDisputeRequest(
                                DisputeResolution.NO_REFUND,
                                BigDecimal.ZERO,
                                "Too late",
                                null
                        )
                )
        );
    }

    @Test
    @DisplayName("the full trail is kept from raising to ruling")
    void trailCoversTheWholeCase() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );
        disputeService.claim(
                admin.getUserId(), dispute.disputeId(), "On it"
        );
        disputeService.changeStatus(
                admin.getUserId(),
                dispute.disputeId(),
                new ChangeDisputeStatusRequest(
                        DisputeStatus.AWAITING_PARTNER_RESPONSE,
                        "Awaiting the hotel"
                )
        );
        disputeService.changeStatus(
                admin.getUserId(),
                dispute.disputeId(),
                new ChangeDisputeStatusRequest(
                        DisputeStatus.UNDER_REVIEW, null
                )
        );
        disputeService.resolve(
                admin.getUserId(),
                dispute.disputeId(),
                new ResolveDisputeRequest(
                        DisputeResolution.PARTIAL_REFUND,
                        new BigDecimal("1000.00"),
                        "Half of the room rate",
                        null
                )
        );

        var entries = timelineRepository
                .findByDispute_DisputeIdOrderByCreatedAtAsc(
                        dispute.disputeId()
                );

        assertEquals(5, entries.size());
        assertEquals(
                DisputeEventType.RAISED,
                entries.get(0).getEventType()
        );
        assertEquals(
                DisputeEventType.RESOLVED,
                entries.get(4).getEventType()
        );
        assertEquals(
                "UNDER_REVIEW",
                entries.get(4).getFromStatus()
        );
        assertEquals("RESOLVED", entries.get(4).getToStatus());
    }

    /* ============================================================
     * VISIBILITY
     * ============================================================ */

    @Test
    @DisplayName("the claimant and the partner can both see the dispute")
    void partiesCanSeeTheDispute() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );

        assertNotNull(disputeService.get(
                traveller.getUserId(), false, dispute.disputeId()
        ));
        assertNotNull(disputeService.get(
                partner.getUserId(), false, dispute.disputeId()
        ));
        assertNotNull(disputeService.get(
                admin.getUserId(), true, dispute.disputeId()
        ));
    }

    @Test
    @DisplayName("an unrelated traveller cannot read the dispute")
    void unrelatedUserCannotSeeTheDispute() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> disputeService.get(
                        other.getUserId(),
                        false,
                        dispute.disputeId()
                )
        );
    }

    @Test
    @DisplayName("the detail view returns the trail and the flags")
    void detailCarriesTimelineAndFlags() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );
        disputeService.claim(
                admin.getUserId(), dispute.disputeId(), null
        );

        DisputeResponse detail = disputeService.get(
                traveller.getUserId(), false, dispute.disputeId()
        );

        assertEquals(2, detail.timeline().size());
        assertNotNull(detail.evidence());
        assertFalse(detail.canWithdraw());
        assertTrue(detail.canAddEvidence());
    }

    /* ============================================================
     * QUEUES
     * ============================================================ */

    @Test
    @DisplayName("the admin queue holds live disputes and skips closed ones")
    void queueHoldsLiveDisputes() {
        Booking live = claimableBooking();
        Booking closed = claimableBooking();
        disputeService.raise(
                traveller.getUserId(),
                live.getBookingId(),
                standardClaim()
        );
        DisputeResponse done = disputeService.raise(
                traveller.getUserId(),
                closed.getBookingId(),
                standardClaim()
        );
        disputeService.withdraw(
                traveller.getUserId(), done.disputeId()
        );

        DisputeQueueResponse queue = disputeService.queue(0, 20);

        assertTrue(
                queue.content().stream()
                        .noneMatch(
                                row -> row.disputeId()
                                        .equals(done.disputeId())
                        ),
                "a withdrawn dispute must not sit in the work queue"
        );
    }

    @Test
    @DisplayName("the queue reports the money already refunded")
    void queueReportsRefundedTotal() {
        Booking booking = claimableBooking();
        DisputeResponse dispute = disputeService.raise(
                traveller.getUserId(),
                booking.getBookingId(),
                standardClaim()
        );
        disputeService.resolve(
                admin.getUserId(),
                dispute.disputeId(),
                new ResolveDisputeRequest(
                        DisputeResolution.FULL_REFUND,
                        new BigDecimal("2500.00"),
                        "Approved",
                        null
                )
        );

        DisputeQueueResponse queue = disputeService.queue(0, 20);

        assertTrue(
                queue.totalRefunded()
                        .compareTo(new BigDecimal("2500.00")) >= 0
        );
    }

    @Test
    @DisplayName("the partner sees only disputes against their own property")
    void partnerSeesOnlyTheirOwn() {
        Booking mine = claimableBooking();
        Booking otherProperty = claimableBookingFor(other);

        disputeService.raise(
                traveller.getUserId(),
                mine.getBookingId(),
                standardClaim()
        );
        disputeService.raise(
                other.getUserId(),
                otherProperty.getBookingId(),
                standardClaim()
        );

        /*
         * Both disputes are against the same property in this
         * fixture, so the partner should see exactly the claims
         * made by travellers against their listings and nothing
         * they are not a party to.
         */
        DisputeQueueResponse queue =
                disputeService.disputesAgainst(
                        partner.getUserId(), 0, 20
                );

        assertEquals(2, queue.totalElements());
    }

    @Test
    @DisplayName("the claimant's list only contains their own disputes")
    void myDisputesIsScoped() {
        Booking mine = claimableBooking();
        Booking theirs = claimableBookingFor(other);
        disputeService.raise(
                traveller.getUserId(),
                mine.getBookingId(),
                standardClaim()
        );
        disputeService.raise(
                other.getUserId(),
                theirs.getBookingId(),
                standardClaim()
        );

        DisputePageResponse page = disputeService.myDisputes(
                traveller.getUserId(), 0, 20
        );

        assertEquals(1, page.totalElements());
    }
}