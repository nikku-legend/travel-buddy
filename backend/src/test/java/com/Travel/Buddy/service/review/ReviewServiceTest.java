package com.Travel.Buddy.service.review;

import com.Travel.Buddy.dto.property.PropertyUpsertRequest;
import com.Travel.Buddy.dto.review.ReviewDecisionRequest;
import com.Travel.Buddy.dto.review.ReviewRequest;
import com.Travel.Buddy.dto.review.ReviewResponse;
import com.Travel.Buddy.dto.review.ReviewSummaryResponse;
import com.Travel.Buddy.dto.room.RoomTypeUpsertRequest;
import com.Travel.Buddy.entity.*;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.*;
import com.Travel.Buddy.service.partner.RoleService;
import com.Travel.Buddy.service.property.PropertyApprovalService;
import com.Travel.Buddy.service.property.RoomTypeService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reviews and ratings. (FR-26)
 *
 * <p>Entitlement is the behaviour that matters: the SRS says only
 * eligible users should review, and "eligible" means a completed
 * stay. These tests pin that down, plus moderation and the rating
 * aggregate.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Reviews and ratings (FR-26)")
class ReviewServiceTest {

    @Autowired
    private ReviewService service;

    @Autowired
    private PropertyApprovalService propertyService;

    @Autowired
    private RoomTypeService roomTypeService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private HotelReservationRepository reservationRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private StateRepository stateRepository;

    @Autowired
    private CountryRepository countryRepository;

    private User admin;

    private User partner;

    private User guest;

    private User stranger;

    private Long propertyId;

    private Long roomTypeId;

    private Integer stateId;

    @BeforeEach
    void setUp() {
        admin = createUser("rev-admin@test.travelbuddy");
        roleService.grantBaselineTravelerRole(admin);
        roleService.grant(admin, Role.ROLE_SUPER_ADMIN, null);

        partner = createUser("rev-partner@test.travelbuddy");
        roleService.grantBaselineTravelerRole(partner);
        roleService.grant(partner, Role.ROLE_HOTEL_PARTNER, admin);

        guest = createUser("rev-guest@test.travelbuddy");
        roleService.grantBaselineTravelerRole(guest);

        stranger = createUser("rev-stranger@test.travelbuddy");
        roleService.grantBaselineTravelerRole(stranger);

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
                        "Review Hotel",
                        PropertyType.HOTEL,
                        stateId,
                        "Address",
                        "Description",
                        new BigDecimal("19.8"),
                        new BigDecimal("85.7")
                )
        ).propertyId();

        roomTypeId = roomTypeService.create(
                partner.getUserId(),
                propertyId,
                new RoomTypeUpsertRequest(
                        "Deluxe", 2, new BigDecimal("2500.00"), "INR", 3
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

    /**
     * Creates a booking for the guest with the given status, so a
     * test can prove entitlement depends on COMPLETED and not merely
     * on a booking existing.
     */
    private Long bookingFor(User user, BookingStatus status) {
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow();

        Booking booking = new Booking();

        booking.setUser(user);
        booking.setBookingReference(
                "TB-" + UUID.randomUUID()
                        .toString()
                        .substring(0, 10)
                        .toUpperCase()
        );
        booking.setTotalAmount(new BigDecimal("5000.00"));
        booking.setCurrency("INR");
        booking.setBookingStatus(status);
        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setGuestCount(2);
        booking.setGuestName("Review Guest");
        booking.setGuestEmail("rev-guest@test.travelbuddy");
        booking.setGuestPhone("9000000000");

        booking = bookingRepository.save(booking);

        HotelReservation reservation = new HotelReservation();

        reservation.setBooking(booking);
        reservation.setRoomType(roomType);
        reservation.setCheckIn(java.time.LocalDate.now());
        reservation.setCheckOut(
                java.time.LocalDate.now().plusDays(2)
        );
        reservation.setRoomsBooked(1);

        reservationRepository.save(reservation);

        return booking.getBookingId();
    }

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    private ReviewRequest review(int rating) {
        return new ReviewRequest(
                ReviewTargetType.HOTEL,
                propertyId,
                rating,
                "Great stay",
                "Comfortable room and friendly staff."
        );
    }
    @Test
    @DisplayName("Only a guest with a completed stay may review a hotel")
    void completedStayRequired() {
        bookingFor(guest, BookingStatus.PENDING);

        assertThrows(
                PartnerApplicationException.class,
                () -> service.submit(
                        guest.getUserId(), review(5)
                ),
                "An unfinished stay must not be reviewable"
        );

        bookingFor(guest, BookingStatus.CONFIRMED);

        assertThrows(
                PartnerApplicationException.class,
                () -> service.submit(
                        guest.getUserId(), review(5)
                ),
                "A confirmed but not completed stay must not be reviewable"
        );

        bookingFor(guest, BookingStatus.COMPLETED);

        ReviewResponse submitted = service.submit(
                guest.getUserId(), review(5)
        );

        assertNotNull(submitted.reviewId());
    }

    @Test
    @DisplayName("Someone who never stayed cannot review")
    void nonGuestCannotReview() {
        bookingFor(guest, BookingStatus.COMPLETED);

        assertThrows(
                PartnerApplicationException.class,
                () -> service.submit(
                        stranger.getUserId(), review(1)
                ),
                "Eligibility must not be transferable"
        );
    }

    @Test
    @DisplayName("A review is not public until a moderator publishes it")
    void reviewIsPrivateUntilPublished() {
        bookingFor(guest, BookingStatus.COMPLETED);

        ReviewResponse submitted = service.submit(
                guest.getUserId(), review(4)
        );

        assertEquals(ReviewStatus.PENDING, submitted.status());

        assertTrue(
                service.publicReviews(
                        ReviewTargetType.HOTEL, propertyId
                ).isEmpty(),
                "A pending review must not be publicly visible"
        );

        service.moderate(
                admin.getUserId(),
                submitted.reviewId(),
                new ReviewDecisionRequest(true, null)
        );

        assertEquals(
                1,
                service.publicReviews(
                        ReviewTargetType.HOTEL, propertyId
                ).size()
        );
    }

    @Test
    @DisplayName("Publishing a review updates the rating aggregate")
    void summaryTracksPublishedReviews() {
        bookingFor(guest, BookingStatus.COMPLETED);
        bookingFor(stranger, BookingStatus.COMPLETED);

        ReviewResponse first = service.submit(
                guest.getUserId(), review(5)
        );
        ReviewResponse second = service.submit(
                stranger.getUserId(),
                new ReviewRequest(
                        ReviewTargetType.HOTEL,
                        propertyId,
                        3,
                        "Fine",
                        "Decent, nothing special."
                )
        );

        /*
         * A pending review must not move the average, because it is
         * not yet public.
         */
        assertEquals(
                0, service.summary(
                        ReviewTargetType.HOTEL, propertyId
                ).reviewCount(),
                "A pending review must not count towards the rating"
        );

        service.moderate(
                admin.getUserId(),
                first.reviewId(),
                new ReviewDecisionRequest(true, null)
        );

        ReviewSummaryResponse afterOne = service.summary(
                ReviewTargetType.HOTEL, propertyId
        );

        assertEquals(1, afterOne.reviewCount());
        assertEquals(
                1, afterOne.fiveStar(),
                "The single 5 star review must be counted"
        );
        assertEquals(
                0,
                afterOne.averageRating()
                        .compareTo(new BigDecimal("5.00"))
        );

        service.moderate(
                admin.getUserId(),
                second.reviewId(),
                new ReviewDecisionRequest(true, null)
        );

        ReviewSummaryResponse afterTwo = service.summary(
                ReviewTargetType.HOTEL, propertyId
        );

        assertEquals(2, afterTwo.reviewCount());
        assertEquals(1, afterTwo.fiveStar());
        assertEquals(1, afterTwo.threeStar());
        assertEquals(
                0,
                afterTwo.averageRating()
                        .compareTo(new BigDecimal("4.00")),
                "Mean of 5 and 3 is 4"
        );
    }

    @Test
    @DisplayName("A traveller may only review a subject once")
    void oneReviewPerSubject() {
        bookingFor(guest, BookingStatus.COMPLETED);

        service.submit(guest.getUserId(), review(5));

        assertThrows(
                PartnerApplicationException.class,
                () -> service.submit(guest.getUserId(), review(1))
        );
    }

    @Test
    @DisplayName("A rejection must carry a reason and stay out of the rating")
    void rejectionRequiresReason() {
        bookingFor(guest, BookingStatus.COMPLETED);

        ReviewResponse submitted = service.submit(
                guest.getUserId(), review(1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.moderate(
                        admin.getUserId(),
                        submitted.reviewId(),
                        new ReviewDecisionRequest(false, "  ")
                )
        );

        service.moderate(
                admin.getUserId(),
                submitted.reviewId(),
                new ReviewDecisionRequest(
                        false,
                        "Contains personal information about a third party."
                )
        );

        assertTrue(
                service.publicReviews(
                        ReviewTargetType.HOTEL, propertyId
                ).isEmpty()
        );

        assertEquals(
                0,
                service.summary(
                        ReviewTargetType.HOTEL, propertyId
                ).reviewCount()
        );
    }

    @Test
    @DisplayName("A published review cannot be silently edited")
    void publishedReviewCannotBeEdited() {
        bookingFor(guest, BookingStatus.COMPLETED);

        ReviewResponse submitted = service.submit(
                guest.getUserId(), review(5)
        );

        service.moderate(
                admin.getUserId(),
                submitted.reviewId(),
                new ReviewDecisionRequest(true, null)
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> service.edit(
                        guest.getUserId(),
                        submitted.reviewId(),
                        review(1)
                ),
                "Editing must not bypass moderation"
        );
    }

    @Test
    @DisplayName("Withdrawing a published review removes it from the rating")
    void withdrawRemovesFromRating() {
        bookingFor(guest, BookingStatus.COMPLETED);

        ReviewResponse submitted = service.submit(
                guest.getUserId(), review(5)
        );

        service.moderate(
                admin.getUserId(),
                submitted.reviewId(),
                new ReviewDecisionRequest(true, null)
        );

        service.withdraw(guest.getUserId(), submitted.reviewId());

        assertEquals(
                0,
                service.summary(
                        ReviewTargetType.HOTEL, propertyId
                ).reviewCount()
        );
    }

    @Test
    @DisplayName("A review for a subject that does not exist is refused")
    void unknownTargetRefused() {
        bookingFor(guest, BookingStatus.COMPLETED);

        assertThrows(
                PartnerApplicationException.class,
                () -> service.submit(
                        guest.getUserId(),
                        new ReviewRequest(
                                ReviewTargetType.HOTEL,
                                999_999L,
                                5,
                                "Ghost",
                                "Does not exist."
                        )
                )
        );
    }

    @Test
    @DisplayName("A traveller cannot edit someone else's review")
    void cannotEditForeignReview() {
        bookingFor(guest, BookingStatus.COMPLETED);

        ReviewResponse submitted = service.submit(
                guest.getUserId(), review(5)
        );

        assertThrows(
                PartnerApplicationException.class,
                () -> service.edit(
                        stranger.getUserId(),
                        submitted.reviewId(),
                        review(1)
                )
        );
    }
}