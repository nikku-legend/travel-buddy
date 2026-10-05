package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.dto.trip.AddTripSelectionRequest;
import com.Travel.Buddy.dto.trip.CreateTripRequest;
import com.Travel.Buddy.dto.trip.SetTripRouteRequest;
import com.Travel.Buddy.dto.trip.TripDetailResponse;
import com.Travel.Buddy.dto.trip.TripMilestoneResponse;
import com.Travel.Buddy.entity.*;
import com.Travel.Buddy.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The closing pin is earned, like every other pin. (SRS 2.2 TP-11)
 *
 * <p>TRIP_COMPLETED was seeded but nothing could ever complete it, so
 * every trip ended with a permanently grey final pin.
 *
 * <p>The rule: the end date has passed AND every booked service has
 * reached a terminal state. The date alone is not evidence. A partner
 * who checks a guest out a week early would otherwise close the whole
 * trip, and the map would record a finished journey containing a
 * stay that never happened.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Trip completion needs the date and the services")
class TripCompletionTest {

    @Autowired
    private TripService tripService;
    @Autowired
    private TripCartService cartService;
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
    private BookingRepository bookingRepository;
    @Autowired
    private TripSelectionRepository selectionRepository;

    private static State sharedState;

    private User traveller;
    private City city;
    private Property property;
    private RoomType roomType;

    @BeforeEach
    void setUp() {
        traveller = user("Complete Traveller");

        if (sharedState == null) {
            Country country = countryRepository
                    .findByIsoCode("CMP")
                    .orElseGet(() -> {
                        Country created = new Country();
                        created.setName("Complete Land");
                        created.setIsoCode("CMP");
                        return created;
                    });
            country = countryRepository.save(country);

            sharedState = new State();
            sharedState.setName("Complete State");
            sharedState.setCountry(country);
            sharedState.setRegionZone(RegionZone.EAST);
            sharedState = stateRepository.save(sharedState);
        }

        city = new City(sharedState, "CP-" + rand(), "cp-" + rand());
        city = cityRepository.save(city);

        property = new Property();
        property.setName("Complete Inn-" + rand());
        property.setPropertyType(PropertyType.HOTEL);
        property.setState(sharedState);
        property.setCity(city);
        property.setPartner(user("Complete Hotel"));
        property.setAddress("Complete Road");
        property.setDescription("A property for the completion test");
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

    /* THE TRIP IS OVER */

    @Test
    @DisplayName("a finished trip ticks the closing pin")
    void finishedTripCompletes() {
        Long tripId = pastTrip();
        book(tripId, BookingStatus.COMPLETED);

        assertTrue(
                pin(tripId).completed(),
                "the date has passed and every service has finished"
        );
    }

    /**
     * Cancelled counts as settled on purpose: a traveller who drops
     * one leg and takes the rest has still finished the trip.
     */
    @Test
    @DisplayName("a cancelled service still lets the trip finish")
    void cancelledServiceStillCompletes() {
        Long tripId = pastTrip();
        book(tripId, BookingStatus.CANCELLED);

        assertTrue(pin(tripId).completed());
    }

    /* THE DATE ALONE IS NOT EVIDENCE */

    @Test
    @DisplayName("an unfinished stay does not complete the trip")
    void unfinishedStayLeavesThePinGrey() {
        Long tripId = pastTrip();
        book(tripId, BookingStatus.CONFIRMED);

        assertFalse(
                pin(tripId).completed(),
                "the end date has passed, but a service is still open. "
                        + "Claiming the trip is finished would record a "
                        + "stay that never happened"
        );
    }

    @Test
    @DisplayName("a checked-in but not checked-out stay does not complete")
    void inHouseGuestDoesNotComplete() {
        Long tripId = pastTrip();
        book(tripId, BookingStatus.CHECKED_IN);

        assertFalse(pin(tripId).completed());
    }

    /* NOTHING BOOKED IS NOT A COMPLETED TRIP */

    @Test
    @DisplayName("a trip with nothing booked does not complete")
    void nothingBookedLeavesThePinGrey() {
        Long tripId = pastTrip();

        assertFalse(
                pin(tripId).completed(),
                "a planned and abandoned trip is not a finished one"
        );
    }

    /* THE DATE STILL MATTERS */

    @Test
    @DisplayName("a trip still in the future does not complete")
    void futureTripDoesNotComplete() {
        LocalDate start = LocalDate.now().plusDays(60);
        LocalDate end = start.plusDays(3);

        Long tripId = tripService.create(
                traveller.getUserId(),
                new CreateTripRequest(
                        "Future Trip", start, end, 1, null, "INR"
                )
        ).tripId();

        route(tripId, start, end);
        book(tripId, BookingStatus.COMPLETED);

        assertFalse(
                pin(tripId).completed(),
                "finishing every service early is not the same as the "
                        + "trip having happened"
        );
    }

    /* IDEMPOTENCE */

    @Test
    @DisplayName("the closing pin keeps its original timestamp")
    void completionIsNotRewound() {
        Long tripId = pastTrip();
        book(tripId, BookingStatus.COMPLETED);

        LocalDateTime first = pin(tripId).completedAt();

        tripService.get(traveller.getUserId(), tripId);

        assertEquals(
                first,
                pin(tripId).completedAt(),
                "revisiting must not rewrite when the trip ended"
        );
    }

    /* FIXTURES */

    private static String rand() {
        return UUID.randomUUID().toString().substring(0, 6);
    }

    private User user(String name) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(rand() + "@tb.local");
        user.setPasswordHash("{noop}password");
        return userRepository.save(user);
    }

    /** Yesterday to today, the earliest span that is already over. */
    private Long pastTrip() {
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now();

        Long tripId = tripService.create(
                traveller.getUserId(),
                new CreateTripRequest(
                        "Finished Trip", start, end, 1, null, "INR"
                )
        ).tripId();

        route(tripId, start, end);
        return tripId;
    }

    private void route(Long tripId, LocalDate start, LocalDate end) {
        tripService.setRoute(
                traveller.getUserId(),
                tripId,
                new SetTripRouteRequest(
                        List.of(new SetTripRouteRequest.CityStopRequest(
                                city.getCityId(), start, end
                        ))
                )
        );
    }

    private void book(Long tripId, BookingStatus status) {
        TripDetailResponse detail = tripService.get(
                traveller.getUserId(), tripId);

        cartService.addSelection(
                traveller.getUserId(),
                tripId,
                new AddTripSelectionRequest(
                        TripSelectionType.HOTEL,
                        detail.cities().get(0).tripCityId(),
                        property.getPropertyId(),
                        roomType.getRoomTypeId(),
                        detail.startDate(),
                        detail.endDate(),
                        1,
                        1,
                        null,
                        "INR"
                )
        );

        TripSelection selection = selectionRepository
                .findByTrip_TripIdOrderBySelectionIdAsc(tripId)
                .get(0);

        Booking booking = new Booking();
        booking.setUser(traveller);
        booking.setBookingReference("BK-" + rand().toUpperCase());
        booking.setTotalAmount(new BigDecimal("2000.00"));
        booking.setCurrency("INR");
        booking.setBookingStatus(status);
        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setGuestCount(1);
        booking.setGuestName("Complete Traveller");
        booking = bookingRepository.save(booking);

        selection.markBooked(booking);
        selectionRepository.save(selection);
    }

    private TripMilestoneResponse pin(Long tripId) {
        return tripService.get(traveller.getUserId(), tripId)
                .milestones()
                .stream()
                .filter(m -> m.milestoneType()
                        == TripMilestoneType.TRIP_COMPLETED)
                .findFirst()
                .orElseThrow();
    }
}