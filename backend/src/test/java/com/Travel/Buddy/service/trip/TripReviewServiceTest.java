package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.dto.trip.*;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Review Center is earned, not granted. (SRS 2.3 TP-12)
 *
 * <p>TripStatus had a REVIEW_OPEN state and TripMilestoneType had
 * REVIEW_OPENED. Neither was ever reached. This pins the behaviour
 * that replaces that gap: a card appears only once the service
 * behind it has actually been used.
 *
 * <p>The rule worth defending is that eligibility follows
 * <em>completion of that one service</em>, not the trip end date.
 * A traveller three days into a trip has finished the hotel they
 * checked out of and can say something true about it, while their
 * onward cab has not run yet. Keying eligibility off the date would
 * let a partner check a guest out early and still collect a review
 * for a stay that never happened.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Review Center unlocks per service, not per trip date")
class TripReviewServiceTest {

    @Autowired
    private TripReviewService reviewCentre;
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
    private TouristPlaceRepository placeRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private TripSelectionRepository selectionRepository;
    @Autowired
    private GuideRepository guideRepository;
    @Autowired
    private GuideReservationRepository guideReservationRepository;
    @Autowired
    private CabRepository cabRepository;
    @Autowired
    private CabRideRepository cabRideRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private ReviewUnlockRepository unlockRepository;

    private static State sharedState;

    private User traveller;
    private User partner;
    private City city;
    private Property property;
    private RoomType roomType;
    private TouristPlace place;
    private Long routedTrip;
    private Long routedCityId;
    private LocalDate start;
    private LocalDate end;

    @BeforeEach
    void setUp() {
        traveller = user("Review Traveller");
        partner = user("Review Partner");
        start = LocalDate.now().plusDays(150);
        end = start.plusDays(3);

        if (sharedState == null) {
            Country country = countryRepository
                    .findByIsoCode("RVM")
                    .orElseGet(() -> {
                        Country created = new Country();
                        created.setName("Review Land");
                        created.setIsoCode("RVM");
                        return created;
                    });
            country = countryRepository.save(country);

            sharedState = new State();
            sharedState.setName("Review State");
            sharedState.setCountry(country);
            sharedState.setRegionZone(RegionZone.EAST);
            sharedState = stateRepository.save(sharedState);
        }

        city = new City(sharedState, "RV-" + rand(), "rv-" + rand());
        city = cityRepository.save(city);

        property = new Property();
        property.setName("Review Inn-" + rand());
        property.setPropertyType(PropertyType.HOTEL);
        property.setState(sharedState);
        property.setCity(city);
        property.setPartner(partner);
        property.setAddress("Review Road");
        property.setDescription("A property for the review test");
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

        /*
         * A real attraction. The activity selection used to
         * point at the property's id, which the cart accepted
         * because nothing resolved it -- now the cart loads the
         * place, so the id has to be one.
         */
        place = new TouristPlace();
        place.setState(sharedState);
        place.setCity(city);
        place.setName("Review Temple-" + rand());
        place.setCurrency("INR");
        place.setEntryFee(new BigDecimal("150.00"));
        place.setActive(true);
        place = placeRepository.save(place);
    }
    /* A COMPLETED STAY EARNS A CARD */

    @Test
    @DisplayName("a completed stay opens a review card")
    void completedStayOpensACard() {
        Long tripId = trip();
        TripSelection selection = hotel(tripId);
        booking(selection, BookingStatus.COMPLETED);

        TripReviewCardResponse card = cards(tripId).stream()
                .filter(c -> c.targetType() == ReviewTargetType.HOTEL)
                .findFirst()
                .orElseThrow();

        assertEquals(ReviewUnlockStatus.ELIGIBLE, card.status());
        assertTrue(
                card.actionable(),
                "an unlocked card must offer a review form"
        );
        assertEquals(
                property.getPropertyId(),
                card.targetId(),
                "the card must point at the property stayed in"
        );
        assertEquals(property.getName(), card.targetName());
        assertNotNull(card.bookingId());
        assertNotNull(
                card.reason(),
                "a card that cannot explain itself will not be trusted"
        );
        assertNotNull(
                selection.getBooking(),
                "the card is backed by a real booking"
        );
    }

    /* A STAY THAT HAS NOT HAPPENED EARNS NOTHING */

    /**
     * The guarantee the whole feature rests on. A card that opened on
     * the trip end date would let a hotel collect a review for a stay
     * the guest has not taken.
     */
    @Test
    @DisplayName("a stay that has not finished produces no card")
    void unfinishedStayIsNotReviewable() {
        Long tripId = trip();
        hotel(tripId);

        TripReviewCardResponse.TripReviewCentreResponse centre =
                reviewCentre.reviewCentre(traveller.getUserId(), tripId);

        assertFalse(
                centre.reviewsUnlocked(),
                "nothing has been used yet, so the center must stay shut"
        );
        assertEquals(0, centre.summary().total());
        assertEquals(
                0,
                reviewCentre.pendingCount(traveller.getUserId(), tripId)
        );
    }

    /**
     * A locked card is absent, not shown with a padlock. Section 10
     * defines the center as the services that became reviewable.
     */
    @Test
    @DisplayName("a locked card is hidden rather than listed")
    void lockedCardIsHidden() {
        Long tripId = trip();
        TripSelection selection = hotel(tripId);
        booking(selection, BookingStatus.CONFIRMED);

        /* Reading the centre is what derives the cards. */
        assertEquals(
                0,
                cards(tripId).size(),
                "a hotel the traveller has not reached is not part of "
                        + "the Review Center"
        );
        assertTrue(
                unlockRepository
                        .existsByTrip_TripIdAndTargetTypeAndTargetId(
                                tripId,
                                ReviewTargetType.HOTEL,
                                property.getPropertyId()
                        ),
                "a card is recorded, it is merely not reviewable yet"
        );
        assertEquals(
                ReviewUnlockStatus.LOCKED,
                unlockRepository
                        .findByTrip_TripIdAndTargetTypeAndTargetId(
                                tripId,
                                ReviewTargetType.HOTEL,
                                property.getPropertyId()
                        ).orElseThrow()
                        .getStatus()
        );
    }

    /* THE TRIP DATE DOES NOT DECIDE */

    /**
     * The hotel is finished; the cab has not run. Eligibility is per
     * service, so the hotel card opens alone.
     */
    @Test
    @DisplayName("a finished stay opens while an unused cab stays shut")
    void eligibilityIsPerServiceNotPerTrip() {
        Long tripId = trip();
        TripSelection hotel = hotel(tripId);
        TripSelection cab = cab(tripId);

        booking(hotel, BookingStatus.COMPLETED);
        booking(cab, BookingStatus.CONFIRMED);

        List<TripReviewCardResponse> shown = cards(tripId);

        assertEquals(
                1,
                shown.size(),
                "only the service actually used may be asked about"
        );
        assertEquals(ReviewTargetType.HOTEL, shown.get(0).targetType());
    }

    /* A CAB IS REVIEWED WHEN THE RIDE IS */

    @Test
    @DisplayName("a completed ride opens the cab card")
    void completedRideOpensTheCabCard() {
        Long tripId = trip();
        TripSelection cab = cab(tripId);

        Booking rideBooking = booking(cab, BookingStatus.CONFIRMED);
        ride(rideBooking, cab.getTargetId(), RideStatus.COMPLETED);

        TripReviewCardResponse card = cards(tripId).stream()
                .filter(c -> c.targetType() == ReviewTargetType.CAB)
                .findFirst()
                .orElseThrow();

        assertEquals(ReviewUnlockStatus.ELIGIBLE, card.status());
    }

    /**
     * Booking the vehicle is not the service. A paid booking whose
     * ride never happened must not become a review.
     */
    @Test
    @DisplayName("a booked cab with no completed ride earns no card")
    void bookedButNotRiddenCabIsNotReviewable() {
        Long tripId = trip();
        TripSelection cab = cab(tripId);

        Booking rideBooking = booking(cab, BookingStatus.CONFIRMED);
        ride(rideBooking, cab.getTargetId(), RideStatus.CANCELLED);

        assertEquals(0, cards(tripId).size());
    }

    /* A GUIDE IS PROVEN BY A RESERVATION */

    @Test
    @DisplayName("a completed tour opens the guide card")
    void completedTourOpensTheGuideCard() {
        Long tripId = trip();
        TripSelection guide = guide(tripId);

        Booking tour = booking(guide, BookingStatus.COMPLETED);
        reserve(tour, guide.getTargetId());

        TripReviewCardResponse card = cards(tripId).stream()
                .filter(c -> c.targetType() == ReviewTargetType.GUIDE)
                .findFirst()
                .orElseThrow();

        assertEquals(ReviewUnlockStatus.ELIGIBLE, card.status());
    }

    /**
     * A guide booking with no reservation row cannot name a subject,
     * so no card is made rather than a card pointing at nothing.
     */
    @Test
    @DisplayName("a guide booking with no reservation produces no card")
    void guideWithoutReservationMakesNoCard() {
        Long tripId = trip();
        TripSelection guide = guide(tripId);
        booking(guide, BookingStatus.COMPLETED);

        assertEquals(
                0,
                cards(tripId).size(),
                "better a missing card than a card naming nothing"
        );
    }

    /* RE-DERIVING IS SAFE */

    /**
     * Review Center is a read. Every visit re-derives, so a second
     * visit must not double every card.
     */
    @Test
    @DisplayName("visiting twice does not duplicate cards")
    void derivationIsIdempotent() {
        Long tripId = trip();
        TripSelection hotel = hotel(tripId);
        booking(hotel, BookingStatus.COMPLETED);

        int first = cards(tripId).size();
        int second = cards(tripId).size();

        assertEquals(first, second);
        assertEquals(1, first);
    }

    /**
     * A card past LOCKED is never rewound, so a traveller cannot lose
     * a published review by revisiting the center.
     */
    @Test
    @DisplayName("a published review survives re-derivation")
    void publishedReviewIsNotUndone() {
        Long tripId = trip();
        TripSelection hotel = hotel(tripId);
        booking(hotel, BookingStatus.COMPLETED);

        cards(tripId);
        review(hotel, ReviewStatus.PUBLISHED);

        TripReviewCardResponse card = cards(tripId).stream()
                .filter(c -> c.targetType() == ReviewTargetType.HOTEL)
                .findFirst()
                .orElseThrow();

        assertEquals(ReviewUnlockStatus.PUBLISHED, card.status());
        assertFalse(
                card.actionable(),
                "an already reviewed hotel must not be offered again"
        );
    }

    /**
     * A review written from the hotel own page, outside the planner,
     * must not be offered a second time.
     */
    @Test
    @DisplayName("a review made elsewhere is linked, not duplicated")
    void existingReviewIsLinked() {
        Long tripId = trip();
        TripSelection hotel = hotel(tripId);
        booking(hotel, BookingStatus.COMPLETED);

        review(hotel, ReviewStatus.PENDING);

        TripReviewCardResponse card = cards(tripId).stream()
                .filter(c -> c.targetType() == ReviewTargetType.HOTEL)
                .findFirst()
                .orElseThrow();

        assertEquals(
                ReviewUnlockStatus.SUBMITTED,
                card.status(),
                "the card must agree with the review that exists"
        );
        assertEquals(ReviewStatus.PENDING, card.reviewStatus());
    }

    /**
     * A rejected review is withdrawn, so the traveller is entitled to
     * write another. The card has to reopen or the service can never be
     * reviewed at all.
     */
    @Test
    @DisplayName("a rejected review reopens the card")
    void rejectedReviewReopensTheCard() {
        Long tripId = trip();
        TripSelection hotel = hotel(tripId);
        booking(hotel, BookingStatus.COMPLETED);

        review(hotel, ReviewStatus.REJECTED);

        TripReviewCardResponse card = cards(tripId).stream()
                .filter(c -> c.targetType() == ReviewTargetType.HOTEL)
                .findFirst()
                .orElseThrow();

        assertEquals(
                ReviewUnlockStatus.ELIGIBLE,
                card.status(),
                "a rejected review must not close the door permanently"
        );
        assertTrue(card.actionable());
    }


    /* THE MAP REFLECTS THE REVIEW WINDOW */

    /**
     * The last unmapped checkpoint. A map whose review pin is
     * permanently grey tells the traveller the platform does not
     * expect them to say anything.
     */
    @Test
    @DisplayName("an opened review card ticks the review pin")
    void openedCardTicksTheReviewPin() {
        Long tripId = trip();
        TripSelection hotel = hotel(tripId);
        booking(hotel, BookingStatus.COMPLETED);

        cards(tripId);

        assertTrue(
                pin(tripId, TripMilestoneType.REVIEW_OPENED)
                        .completed(),
                "a reviewable stay must reach the map"
        );
    }

    /**
     * Every booked service produces a LOCKED card, so ticking on
     * those would light up a pin for a traveller with nothing to
     * review.
     */
    @Test
    @DisplayName("a locked card does not tick the review pin")
    void lockedCardLeavesThePinAlone() {
        Long tripId = trip();
        TripSelection hotel = hotel(tripId);
        booking(hotel, BookingStatus.CONFIRMED);

        cards(tripId);

        assertFalse(
                pin(tripId, TripMilestoneType.REVIEW_OPENED)
                        .completed(),
                "nothing has been used, so there is nothing to review"
        );
    }

    /*
     * Re-reading the centre derives again, so an already ticked
     * pin must keep its original timestamp rather than moving every
     * time the page is opened.
     */
    @Test
    @DisplayName("the review pin keeps its original timestamp")
    void reviewPinIsNotRewound() {
        Long tripId = trip();
        TripSelection hotel = hotel(tripId);
        booking(hotel, BookingStatus.COMPLETED);

        cards(tripId);
        java.time.LocalDateTime first = pin(
                tripId, TripMilestoneType.REVIEW_OPENED)
                .completedAt();

        cards(tripId);
        java.time.LocalDateTime second = pin(
                tripId, TripMilestoneType.REVIEW_OPENED)
                .completedAt();

        assertEquals(
                first, second,
                "revisiting must not rewrite when the review opened"
        );
    }

    private TripMilestoneResponse pin(
            Long tripId,
            TripMilestoneType type
    ) {
        return tripService.get(traveller.getUserId(), tripId)
                .milestones()
                .stream()
                .filter(m -> m.milestoneType() == type)
                .findFirst()
                .orElseThrow(
                () -> new AssertionError(
                        "no " + type + " pin was seeded")
        );
    }
    /* AN ACTIVITY HAS NO LISTING TO REVIEW */

    @Test
    @DisplayName("an activity selection produces no card")
    void activityMakesNoCard() {
        Long tripId = trip();
        TripSelection selection = activity(tripId);
        booking(selection, BookingStatus.COMPLETED);

        assertEquals(0, cards(tripId).size());
    }

    /* ANOTHER TRAVELLERS TRIP */

    @Test
    @DisplayName("another traveller cannot read the Review Center")
    void centreIsOwnerOnly() {
        Long tripId = trip();
        TripSelection hotel = hotel(tripId);
        booking(hotel, BookingStatus.COMPLETED);

        assertThrows(
                RuntimeException.class,
                () -> reviewCentre.reviewCentre(partner.getUserId(), tripId),
                "a review center is the traveller own record"
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

    private Long trip() {
        return tripService.create(
                traveller.getUserId(),
                new CreateTripRequest(
                        "Review Trip", start, end, 1, null, "INR"
                )
        ).tripId();
    }

    /**
     * The stop id, resolved once per trip.
     *
     * <p>Re-running setRoute deletes and recreates the trip cities, so a
     * second selection added after a second call would point at a
     * row that no longer exists.
     */
    private Long tripCityId(Long tripId) {
        if (routedTrip == null || !routedTrip.equals(tripId)) {
            routedTrip = tripId;
            routedCityId = tripService.setRoute(
                    traveller.getUserId(),
                    tripId,
                    new SetTripRouteRequest(
                            List.of(
                                    new SetTripRouteRequest.CityStopRequest(
                                            city.getCityId(), start, end
                                    ))
                    )
            ).cities().get(0).tripCityId();
        }

        return routedCityId;
    }
    private TripSelection add(
            Long tripId,
            TripSelectionType type,
            Long targetId,
            Long roomId
    ) {
        return selectionRepository
                .findByTrip_TripIdOrderBySelectionIdAsc(
                        cartService.addSelection(
                                traveller.getUserId(),
                                tripId,
                                new AddTripSelectionRequest(
                                        type,
                                        tripCityId(tripId),
                                        targetId,
                                        roomId,
                                        start,
                                        end,
                                        1,
                                        1,
                                        null,
                                        "INR"
                                )
                        ).tripId()
                )
                .get(sizeAfterAdd(tripId) - 1);
    }

    private TripSelection hotel(Long tripId) {
        return add(tripId, TripSelectionType.HOTEL,
                property.getPropertyId(), roomType.getRoomTypeId());
    }

    private TripSelection cab(Long tripId) {
        return add(tripId, TripSelectionType.CAB, cabId(), null);
    }

    private TripSelection guide(Long tripId) {
        return add(tripId, TripSelectionType.GUIDE, guideId(), null);
    }

    private TripSelection activity(Long tripId) {
        return add(tripId, TripSelectionType.ACTIVITY,
                place.getPlaceId(), null);
    }

    /** How many selections the trip had after the last add. */
    private int sizeAfterAdd(Long tripId) {
        return selectionRepository
                .findByTrip_TripIdOrderBySelectionIdAsc(tripId)
                .size();
    }

    /** Links a real booking to the selection, as checkout would. */
    private Booking booking(
            TripSelection selection,
            BookingStatus status
    ) {
        Booking booking = new Booking();
        booking.setUser(traveller);
        booking.setBookingReference("BK-" + rand().toUpperCase());
        booking.setTotalAmount(new BigDecimal("2000.00"));
        booking.setCurrency("INR");
        booking.setBookingStatus(status);
        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setGuestCount(1);
        booking.setGuestName("Review Traveller");
        booking = bookingRepository.save(booking);

        selection.markBooked(booking);
        selectionRepository.save(selection);

        return booking;
    }

    private void ride(
            Booking booking,
            Long cabId,
            RideStatus status
    ) {
        CabRide ride = new CabRide();
        ride.setUser(traveller);
        ride.setBooking(booking);
        ride.setCab(cabRepository.findById(cabId).orElseThrow());
        ride.setPickupLocation("Puri");
        ride.setDropLocation("Bhubaneswar");
        ride.setPickupTime(java.time.LocalDateTime.now().plusDays(1));
        ride.setDistanceKm(new BigDecimal("60.00"));
        ride.setFareAmount(new BigDecimal("1500.00"));
        ride.setStatus(status);
        ride.setOtpCode("1234");
        cabRideRepository.save(ride);
    }

    private void reserve(Booking booking, Long guideId) {
        GuideReservation reservation = new GuideReservation();
        reservation.setBooking(booking);
        reservation.setGuide(
                guideRepository.findById(guideId).orElseThrow()
        );
        reservation.setTourDate(start);
        guideReservationRepository.save(reservation);
    }

    private void review(
            TripSelection selection,
            ReviewStatus status
    ) {
        Review review = new Review();
        review.setUser(traveller);
        review.setTargetType(ReviewTargetType.HOTEL);
        review.setTargetId(property.getPropertyId());
        review.setBooking(selection.getBooking());
        review.setRating(5);
        review.setTitle("Genuinely good");
        review.setComment("Worth the trip");
        review.setStatus(status);
        review.setFlaggedCount(0);
        review.setHelpfulCount(0);
        reviewRepository.save(review);
    }

    private List<TripReviewCardResponse> cards(Long tripId) {
        return reviewCentre
                .reviewCentre(traveller.getUserId(), tripId)
                .cards();
    }

    /* Unique per run, because registration_number is unique. */
    private Long cabId() {
        Cab cab = new Cab();
        cab.setPartner(partner);
        cab.setState(sharedState);
        cab.setVehicleName("Review Sedan " + rand());
        cab.setVehicleType(VehicleType.SEDAN);
        cab.setRegistrationNumber("RV" + rand().toUpperCase());
        cab.setSeatingCapacity(4);
        cab.setDriverName("Review Driver");
        cab.setDriverPhone("9000000000");
        cab.setPricePerKm(new BigDecimal("20.00"));
        cab.setBaseFare(new BigDecimal("250.00"));
        cab.setAvailable(true);
        cab.setVerified(true);
        cab.setActive(true);
        return cabRepository.save(cab).getCabId();
    }

    private Long guideId() {
        Guide guide = new Guide();
        guide.setUser(partner);
        guide.setState(sharedState);
        guide.setDailyRate(new BigDecimal("2500.00"));
        guide.setCurrencyCode("INR");
        guide.setBio("A guide for the review test");
        guide.setYearsOfExperience(5);
        guide.setVerified(true);
        guide.setActive(true);
        return guideRepository.save(guide).getGuideId();
    }
}
