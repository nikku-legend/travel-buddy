package com.Travel.Buddy.service.guide;

import com.Travel.Buddy.dto.guide.GuideReservationResponse;
import com.Travel.Buddy.entity.Booking;
import com.Travel.Buddy.entity.BookingStatus;
import com.Travel.Buddy.entity.Country;
import com.Travel.Buddy.entity.Guide;
import com.Travel.Buddy.entity.GuideReservation;
import com.Travel.Buddy.entity.PaymentStatus;
import com.Travel.Buddy.entity.RegionZone;
import com.Travel.Buddy.entity.State;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.BookingRepository;
import com.Travel.Buddy.repository.CountryRepository;
import com.Travel.Buddy.repository.GuideRepository;
import com.Travel.Buddy.repository.GuideReservationRepository;
import com.Travel.Buddy.repository.StateRepository;
import com.Travel.Buddy.repository.UserRepository;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guide partner portal reads real bookings. (FR-16, FR-23)
 *
 * <p>The dashboard used to render four invented cards, including a
 * 5.0 rating on a profile it called new. It needed an endpoint to
 * read instead, which is what these cover.
 */
@SpringBootTest
@ActiveProfiles("test")
class GuidePartnerPortalTest {

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
    private CountryRepository countryRepository;

    @Autowired
    private StateRepository stateRepository;

    private User partner;
    private Guide guide;

    @BeforeEach
    void setUp() {
        partner = new User();
        partner.setFullName("Portal Guide");
        partner.setEmail(UUID.randomUUID() + "@tb.local");
        partner.setPasswordHash("{noop}irrelevant");
        partner = userRepository.save(partner);

        guide = new Guide();
        guide.setUser(partner);
        guide.setState(state());
        guide.setDailyRate(new BigDecimal("2500.00"));
        guide.setCurrencyCode("INR");
        guide.setYearsOfExperience(7);
        guide.setVerified(true);
        guide.setActive(true);
        guide = guideRepository.save(guide);
    }

    @Test
    @DisplayName("a guide with no tours is told so, not shown a fake number")
    void noToursIsEmptyNotInvented() {
        assertEquals(
                List.of(),
                guideService.myReservations(partner),
                "an empty list is the honest answer; the portal used "
                        + "to render a hardcoded 0 and a 5.0 rating"
        );
    }

    @Test
    @DisplayName("a booked tour appears with its real amount")
    void realReservationIsListed() {
        reserve(LocalDate.now().plusDays(10),
                new BigDecimal("5000.00"), PaymentStatus.PAID);

        List<GuideReservationResponse> rows =
                guideService.myReservations(partner);

        assertEquals(1, rows.size());
        assertEquals(
                new BigDecimal("5000.00"),
                rows.get(0).amount(),
                "the guide must see what the traveller actually paid"
        );
    }

    @Test
    @DisplayName("an unpaid tour is listed but is not income")
    void unpaidTourIsNotEarnings() {
        reserve(LocalDate.now().plusDays(10),
                new BigDecimal("5000.00"), PaymentStatus.UNPAID);

        GuideReservationResponse row =
                guideService.myReservations(partner).get(0);

        assertEquals(
                PaymentStatus.UNPAID,
                row.paymentStatus(),
                "the portal sums only PAID rows, so this must be "
                        + "distinguishable from income"
        );
    }

    @Test
    @DisplayName("tours are listed soonest first")
    void soonestTourComesFirst() {
        reserve(LocalDate.now().plusDays(40),
                new BigDecimal("100.00"), PaymentStatus.PAID);
        reserve(LocalDate.now().plusDays(5),
                new BigDecimal("200.00"), PaymentStatus.PAID);

        List<GuideReservationResponse> rows =
                guideService.myReservations(partner);

        assertEquals(2, rows.size());
        assertTrue(
                rows.get(0).tourDate()
                        .isBefore(rows.get(1).tourDate()),
                "a partner opening the portal wants the next job, "
                        + "not the furthest one"
        );
    }

    @Test
    @DisplayName("the portal carries no traveller contact details")
    void travellerContactIsNotEchoed() {
        reserve(LocalDate.now().plusDays(3),
                new BigDecimal("250.00"), PaymentStatus.PAID);

        GuideReservationResponse row =
                guideService.myReservations(partner).get(0);

        assertEquals(
                "Traveller",
                row.travellerName(),
                "the guide has to know who is arriving"
        );

        /*
         * The name is necessary; the contact details are not. A
         * guide works inside the platform, and re-exposing an email
         * or phone here would hand a partner a way around the
         * support, dispute and notification trail the rest of the
         * system depends on.
         */
        for (java.lang.reflect.RecordComponent component
                : GuideReservationResponse.class.getRecordComponents()) {
            String name = component.getName().toLowerCase();

            assertTrue(
                    !name.contains("email")
                            && !name.contains("phone")
                            && !name.contains("contact"),
                    "the partner portal must not expose "
                            + component.getName()
            );
        }
    }
    @Test
    @DisplayName("an account with no guide profile is told, not shown blank")
    void missingProfileIsRefused() {
        User stranger = new User();
        stranger.setFullName("Not A Guide");
        stranger.setEmail(UUID.randomUUID() + "@tb.local");
        stranger.setPasswordHash("{noop}irrelevant");

        final User saved = userRepository.save(stranger);

        assertThrows(
                IllegalArgumentException.class,
                () -> guideService.myReservations(saved),
                "an empty list would be indistinguishable from "
                        + "'no tours yet' for someone who never "
                        + "finished onboarding"
        );
    }

    private void reserve(
            LocalDate tourDate,
            BigDecimal amount,
            PaymentStatus payment
    ) {
        Booking booking = new Booking();
        booking.setUser(partner);
        booking.setBookingType(
                com.Travel.Buddy.entity.BookingType.GUIDE);
        booking.setBookingReference("TB-PORTAL" + UUID.randomUUID()
                .toString().substring(0, 8).toUpperCase());
        booking.setTotalAmount(amount);
        booking.setCurrency("INR");
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setPaymentStatus(payment);
        booking.setGuestCount(2);
        booking.setGuestName("Traveller");
        booking.setGuestEmail("traveller@tb.local");
        booking.setGuestPhone("9000000000");
        booking = bookingRepository.save(booking);

        GuideReservation reservation = new GuideReservation();
        reservation.setBooking(booking);
        reservation.setGuide(guide);
        reservation.setTourDate(tourDate);
        reservationRepository.save(reservation);
    }

    private State state() {
        return stateRepository
                .findAll()
                .stream()
                .findFirst()
                .orElseGet(this::createState);
    }

    private State createState() {
        Country country = new Country();
        country.setName("Portal Land");
        country.setIsoCode("P" + UUID.randomUUID()
                .toString().substring(0, 4).toUpperCase());

        Country savedCountry =
                countryRepository.save(country);

        State s = new State();
        s.setCountry(savedCountry);
        s.setName("Portal State");
        s.setRegionZone(
                com.Travel.Buddy.entity.RegionZone.EAST);

        return stateRepository.save(s);
    }
}