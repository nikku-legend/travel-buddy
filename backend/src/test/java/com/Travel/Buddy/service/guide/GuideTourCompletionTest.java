package com.Travel.Buddy.service.guide;

import com.Travel.Buddy.entity.Booking;
import com.Travel.Buddy.entity.BookingStatus;
import com.Travel.Buddy.entity.BookingType;
import com.Travel.Buddy.entity.Guide;
import com.Travel.Buddy.entity.GuideReservation;
import com.Travel.Buddy.entity.GuideReservationStatus;
import com.Travel.Buddy.entity.PaymentStatus;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.BookingRepository;
import com.Travel.Buddy.repository.GuideReservationRepository;
import com.Travel.Buddy.repository.GuideRepository;
import com.Travel.Buddy.repository.UserRepository;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A guide can close out a tour. (FR-16, FR-23, FR-25)
 *
 * <p>guide_reservations was the only bookable service in the system
 * with no status column, so nothing could ever move a guide booking
 * to COMPLETED. Two features were therefore permanently unreachable
 * for any trip containing a guide:
 *
 * <ul>
 *   <li>{@code TripReviewService} gates guide review eligibility on a
 *       COMPLETED booking, so a guide review card could never unlock;
 *   <li>{@code TripMilestoneService.completeTripIfFinished} requires
 *       every booked selection to be settled, so a trip with a guide
 *       could never be marked complete, and the Review Center never
 *       opened for it.
 * </ul>
 *
 * <p>Hotels already had this via host check-out and cabs via a ride
 * status PATCH. These cover the guide equivalent, including the
 * invariants the other two already enforce.
 */
@SpringBootTest
@ActiveProfiles("test")
class GuideTourCompletionTest {

    @Autowired
    private GuideService guideService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GuideRepository guideRepository;

    @Autowired
    private GuideReservationRepository reservationRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private com.Travel.Buddy.repository.CountryRepository countryRepository;

    @Autowired
    private com.Travel.Buddy.repository.StateRepository stateRepository;

    private User partner;
    private Guide guide;

    @BeforeEach
    void setUp() {
        partner = new User();
        partner.setFullName("Completing Guide");
        partner.setEmail(UUID.randomUUID() + "@tb.local");
        partner.setPasswordHash("{noop}irrelevant");
        partner = userRepository.save(partner);

        guide = new Guide();
        guide.setUser(partner);
        guide.setState(state());
        guide.setDailyRate(new BigDecimal("2500.00"));
        guide.setCurrencyCode("INR");
        guide.setActive(true);
        guide = guideRepository.save(guide);
    }

    private com.Travel.Buddy.entity.State state() {
        return stateRepository.findAll()
                .stream()
                .findFirst()
                .orElseGet(this::createState);
    }

    private com.Travel.Buddy.entity.State createState() {
        com.Travel.Buddy.entity.Country country =
                new com.Travel.Buddy.entity.Country();
        country.setName("Completion Land");
        country.setIsoCode("C" + UUID.randomUUID()
                .toString().substring(0, 4).toUpperCase());

        com.Travel.Buddy.entity.Country savedCountry =
                countryRepository.save(country);

        com.Travel.Buddy.entity.State s =
                new com.Travel.Buddy.entity.State();
        s.setCountry(savedCountry);
        s.setName("Completion State");
        s.setRegionZone(
                com.Travel.Buddy.entity.RegionZone.EAST);

        return stateRepository.save(s);
    }

    @Test
    @DisplayName("a new tour starts outstanding, not completed")
    void newTourIsNotAlreadyComplete() {
        GuideReservation reservation = reserve(BookingStatus.CONFIRMED);

        assertEquals(
                GuideReservationStatus.CONFIRMED,
                reservation.getStatus(),
                "a tour must be reported before it counts as given, "
                        + "otherwise every booked tour is reviewable "
                        + "before the traveller has taken it"
        );
    }

    @Test
    @DisplayName("completing a tour settles the booking")
    void completingTourCompletesBooking() {
        GuideReservation reservation = reserve(BookingStatus.CONFIRMED);

        guideService.updateReservationStatusForUser(
                partner,
                reservation.getGuideReservationId(),
                GuideReservationStatus.IN_PROGRESS
        );

        guideService.updateReservationStatusForUser(
                partner,
                reservation.getGuideReservationId(),
                GuideReservationStatus.COMPLETED
        );

        assertEquals(
                GuideReservationStatus.COMPLETED,
                reservationRepository
                        .findById(reservation.getGuideReservationId())
                        .orElseThrow()
                        .getStatus()
        );

        assertEquals(
                BookingStatus.COMPLETED,
                bookingOf(reservation).getBookingStatus(),
                "this is the write that unblocks the guide review card"
        );
    }

    @Test
    @DisplayName("a tour cannot skip straight to completed")
    void cannotSkipToCompleted() {
        GuideReservation reservation = reserve(BookingStatus.CONFIRMED);

        assertThrows(
                IllegalArgumentException.class,
                () -> guideService.updateReservationStatusForUser(
                        partner,
                        reservation.getGuideReservationId(),
                        GuideReservationStatus.COMPLETED
                ),
                "CONFIRMED -> COMPLETED skips the tour actually being "
                        + "given, the same way a ride cannot jump "
                        + "straight from booked to finished"
        );
    }
    @Test
    @DisplayName("a completed tour is terminal and cannot be reopened")
    void completedTourIsTerminal() {
        GuideReservation reservation = reserve(BookingStatus.CONFIRMED);
        Long id = reservation.getGuideReservationId();

        guideService.updateReservationStatusForUser(
                partner, id, GuideReservationStatus.IN_PROGRESS);
        guideService.updateReservationStatusForUser(
                partner, id, GuideReservationStatus.COMPLETED);

        for (GuideReservationStatus backwards : new GuideReservationStatus[]{
                GuideReservationStatus.IN_PROGRESS,
                GuideReservationStatus.CONFIRMED,
                GuideReservationStatus.CANCELLED
        }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> guideService.updateReservationStatusForUser(
                            partner, id, backwards),
                    "a completed tour is a historical record; "
                            + "editing it puts the booking ledger and "
                            + "the review window out of agreement"
            );
        }
    }

    @Test
    @DisplayName("a tour cannot be marked the status it already has")
    void sameStatusIsRejected() {
        GuideReservation reservation = reserve(BookingStatus.CONFIRMED);

        assertThrows(
                IllegalArgumentException.class,
                () -> guideService.updateReservationStatusForUser(
                        partner,
                        reservation.getGuideReservationId(),
                        GuideReservationStatus.CONFIRMED
                ),
                "a no-op is almost always a double-clicked button, "
                        + "not an intent to record anything"
        );
    }

    @Test
    @DisplayName("a guide cannot close out another guide's tour")
    void wrongGuideIsRefused() {
        GuideReservation reservation = reserve(BookingStatus.CONFIRMED);

        User stranger = new User();
        stranger.setFullName("Other Guide");
        stranger.setEmail(UUID.randomUUID() + "@tb.local");
        stranger.setPasswordHash("{noop}irrelevant");
        User savedStranger = userRepository.save(stranger);

        Guide otherGuide = new Guide();
        otherGuide.setUser(savedStranger);
        otherGuide.setState(state());
        otherGuide.setDailyRate(new BigDecimal("1000.00"));
        otherGuide.setCurrencyCode("INR");
        otherGuide.setActive(true);
        Guide savedOther = guideRepository.save(otherGuide);

        assertThrows(
                IllegalArgumentException.class,
                () -> guideService.updateReservationStatus(
                        savedOther.getGuideId(),
                        reservation.getGuideReservationId(),
                        GuideReservationStatus.CANCELLED
                ),
                "otherwise any partner could void a competitor's "
                        + "confirmed work by cancelling it"
        );

        assertEquals(
                BookingStatus.CONFIRMED,
                bookingOf(reservation).getBookingStatus(),
                "the refused attempt must not have settled anything"
        );
    }
    @Test
    @DisplayName("completing a tour whose booking was cancelled leaves it cancelled")
    void cancelledBookingIsNotResurrected() {
        GuideReservation reservation = reserve(BookingStatus.CANCELLED);

        guideService.updateReservationStatusForUser(
                partner,
                reservation.getGuideReservationId(),
                GuideReservationStatus.IN_PROGRESS
        );

        guideService.updateReservationStatusForUser(
                partner,
                reservation.getGuideReservationId(),
                GuideReservationStatus.COMPLETED
        );

        assertEquals(
                BookingStatus.CANCELLED,
                bookingOf(reservation).getBookingStatus(),
                "a guide reporting late must not turn a cancelled "
                        + "booking into a paid-for journey nobody took"
        );
    }

    @Test
    @DisplayName("an account with no guide profile cannot report a tour status")
    void accountWithoutProfileIsRefused() {
        GuideReservation reservation = reserve(BookingStatus.CONFIRMED);

        User stranger = new User();
        stranger.setFullName("Not A Guide");
        stranger.setEmail(UUID.randomUUID() + "@tb.local");
        stranger.setPasswordHash("{noop}irrelevant");
        User savedStranger = userRepository.save(stranger);

        assertThrows(
                IllegalArgumentException.class,
                () -> guideService.updateReservationStatusForUser(
                        savedStranger,
                        reservation.getGuideReservationId(),
                        GuideReservationStatus.CANCELLED
                ),
                "there is no guide to attribute the report to"
        );
    }

    @Test
    @DisplayName("the portal shows a tour's reported status")
    void portalExposesTourStatus() {
        GuideReservation reservation = reserve(BookingStatus.CONFIRMED);

        guideService.updateReservationStatusForUser(
                partner,
                reservation.getGuideReservationId(),
                GuideReservationStatus.IN_PROGRESS
        );

        assertEquals(
                GuideReservationStatus.IN_PROGRESS,
                guideService.myReservations(partner).get(0).status(),
                "a partner needs to see which tours are still "
                        + "outstanding, or the button is the only "
                        + "record that anything happened"
        );
    }

    @Test
    @DisplayName("cancelling a tour does not settle the booking as completed")
    void cancellingTourLeavesBookingUncompleted() {
        GuideReservation reservation = reserve(BookingStatus.CONFIRMED);

        guideService.updateReservationStatusForUser(
                partner,
                reservation.getGuideReservationId(),
                GuideReservationStatus.CANCELLED
        );

        assertEquals(
                BookingStatus.CONFIRMED,
                bookingOf(reservation).getBookingStatus(),
                "a cancelled tour is not a completed one; the booking "
                        + "is settled by whatever handles cancellation"
        );

        assertTrue(
                reservationRepository
                        .findById(reservation.getGuideReservationId())
                        .orElseThrow()
                        .getStatus()
                        == GuideReservationStatus.CANCELLED
        );
    }

    /**
     * Re-reads the booking from the database rather than trusting the
     * in-memory copy, so a test cannot pass because the entity the
     * service mutated is the same object the assertion is looking at.
     */
    private Booking bookingOf(GuideReservation reservation) {
        return bookingRepository.findById(
                        reservation.getBooking().getBookingId())
                .orElseThrow();
    }

    private GuideReservation reserve(BookingStatus bookingStatus) {
        Booking booking = new Booking();
        booking.setUser(partner);
        booking.setBookingType(BookingType.GUIDE);
        booking.setBookingReference("TB-COMPLETE"
                + UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase());
        booking.setTotalAmount(new BigDecimal("5000.00"));
        booking.setCurrency("INR");
        booking.setBookingStatus(bookingStatus);
        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setGuestCount(2);
        booking.setGuestName("Traveller");
        booking.setGuestEmail("traveller@tb.local");
        booking.setGuestPhone("9000000000");
        booking = bookingRepository.save(booking);

        GuideReservation reservation = new GuideReservation();
        reservation.setBooking(booking);
        reservation.setGuide(guide);
        reservation.setTourDate(LocalDate.now().plusDays(3));
        return reservationRepository.save(reservation);
    }
}
