package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.dto.booking.CreateHotelBookingRequest;
import com.Travel.Buddy.entity.Booking;
import com.Travel.Buddy.entity.BookingStatus;
import com.Travel.Buddy.entity.BookingType;
import com.Travel.Buddy.entity.Cab;
import com.Travel.Buddy.entity.CabRide;
import com.Travel.Buddy.entity.Guide;
import com.Travel.Buddy.entity.GuideReservation;
import com.Travel.Buddy.entity.PaymentStatus;
import com.Travel.Buddy.entity.RideStatus;
import com.Travel.Buddy.entity.TouristPlace;
import com.Travel.Buddy.entity.Trip;
import com.Travel.Buddy.entity.TripSelection;
import com.Travel.Buddy.entity.TripSelectionStatus;
import com.Travel.Buddy.entity.TripSelectionType;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.BookingRepository;
import com.Travel.Buddy.repository.CabRepository;
import com.Travel.Buddy.repository.CabRideRepository;
import com.Travel.Buddy.repository.GuideRepository;
import com.Travel.Buddy.repository.GuideReservationRepository;
import com.Travel.Buddy.repository.TouristPlaceRepository;
import com.Travel.Buddy.repository.TripSelectionRepository;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.booking.BookingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns a paid trip's cart into real bookings. (SRS 2.2 TP-10)
 *
 * <p>This is the step that was missing between "the traveller paid"
 * and "the traveller has a room, a guide and a car". TripSelection
 * already carried a booking reference and a markBooked transition,
 * and TripSelectionStatus's own javadoc says inventory is taken in
 * TripCheckoutService when payment is created -- but nothing ever
 * called it. A confirmed, paid trip reserved nothing at all.
 *
 * <h2>Why each booking runs in its own transaction</h2>
 *
 * <p>The gateway has already taken the money by the time this runs,
 * so the payment record must not be undone by a booking failure.
 *
 * <p>A TransactionTemplate rather than
 * {@code @Transactional(REQUIRES_NEW)}: bookPaidTrip and bookOne
 * live in the same class, so an annotation would be bypassed by
 * self-invocation and each booking would silently join the
 * caller's transaction. The first failure would then mark
 * everything rollback-only and the caller would throw
 * UnexpectedRollbackException instead of recovering -- precisely
 * the outcome the separate transactions exist to prevent.
 *
 * <h2>Why nothing is read back afterwards</h2>
 *
 * <p>An earlier version reserved in the nested transaction and then
 * looked the booking up in the caller's transaction. That lookup
 * always came back empty, because MySQL defaults to REPEATABLE
 * READ: the caller's snapshot was taken before the nested
 * transaction committed, so it could not see a row the nested
 * transaction had just written. The result was a booking that
 * really existed, reported as missing, with the stay marked
 * unavailable and the checkout sent to recovery.
 *
 * <p>So the whole per-selection unit -- load, reserve, link -- runs
 * inside the nested transaction, and only a description of what
 * happened crosses back out.
 */
@Service
public class TripBookingService {

    private static final Logger log =
            LoggerFactory.getLogger(TripBookingService.class);

    private final BookingService bookingService;
    private final TripSelectionRepository selectionRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final GuideRepository guideRepository;
    private final GuideReservationRepository guideReservationRepository;
    private final CabRepository cabRepository;
    private final CabRideRepository cabRideRepository;
    private final TouristPlaceRepository placeRepository;
    private final TransactionTemplate requiresNew;

    public TripBookingService(
            BookingService bookingService,
            TripSelectionRepository selectionRepository,
            BookingRepository bookingRepository,
            UserRepository userRepository,
            GuideRepository guideRepository,
            GuideReservationRepository guideReservationRepository,
            CabRepository cabRepository,
            CabRideRepository cabRideRepository,
            TouristPlaceRepository placeRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.bookingService = bookingService;
        this.selectionRepository = selectionRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.guideRepository = guideRepository;
        this.guideReservationRepository = guideReservationRepository;
        this.cabRepository = cabRepository;
        this.cabRideRepository = cabRideRepository;
        this.placeRepository = placeRepository;

        this.requiresNew = new TransactionTemplate(
                transactionManager
        );
        this.requiresNew.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    /**
     * Books every outstanding selection on a paid trip.
     *
     * @return human-readable reasons for anything that could not be
     *         booked. Empty means everything is reserved.
     */
    public List<String> bookPaidTrip(Trip trip) {
        List<String> failures = new ArrayList<>();

        User traveller = userRepository
                .findById(trip.getUser().getUserId())
                .orElse(null);

        if (traveller == null) {
            return List.of("The traveller account could not be "
                    + "loaded, so nothing was reserved");
        }

        for (TripSelection selection
                : selectionRepository
                        .findByTrip_TripIdOrderBySelectionIdAsc(
                                trip.getTripId())) {

            if (!selection.countsTowardBill()
                    || selection.getBooking() != null) {
                continue;
            }

            String failure = bookOne(
                    selection.getSelectionId(),
                    traveller.getUserId(),
                    trip
            );

            if (failure != null) {
                failures.add(failure);
            }
        }

        return failures;
    }

    /**
     * Retries only selections that failed after the trip payment cleared.
     * The reset and reservation are separate committed units so booking
     * runs with a fresh view of the selection and its inventory.
     */
    public List<String> retryUnavailableBookings(Trip trip) {
        List<Long> unavailableIds = selectionRepository
                .findByTrip_TripIdOrderBySelectionIdAsc(
                        trip.getTripId()
                )
                .stream()
                .filter(selection ->
                        selection.getStatus()
                                == TripSelectionStatus.UNAVAILABLE
                                && selection.getBooking() == null
                )
                .map(TripSelection::getSelectionId)
                .toList();

        List<String> failures = new ArrayList<>();
        Long travellerId = trip.getUser().getUserId();
        for (Long selectionId : unavailableIds) {
            requiresNew.executeWithoutResult(status ->
                    selectionRepository.findById(selectionId)
                            .filter(selection ->
                                    selection.getStatus()
                                            == TripSelectionStatus.UNAVAILABLE
                                            && selection.getBooking() == null
                            )
                            .ifPresent(selection -> {
                                selection.markSelected();
                                selectionRepository.save(selection);
                            })
            );

            String failure = bookOne(
                    selectionId, travellerId, trip
            );
            if (failure != null) {
                failures.add(failure);
            }
        }
        return failures;
    }

    /**
     * Books one selection, entirely inside its own transaction.
     *
     * <p>The catch sits outside the template on purpose. Inside it,
     * returning normally from a transaction already marked
     * rollback-only makes Spring throw UnexpectedRollbackException
     * on commit, which surfaces as an error rather than the
     * recovery this is meant to produce.
     *
     * @return null on success, or the reason it could not be booked.
     */
    public String bookOne(
            Long selectionId,
            Long travellerId,
            Trip trip
    ) {
        try {
            String reason = requiresNew.execute(status ->
                    reserveWithinTransaction(
                            selectionId, travellerId, trip
                    )
            );

            if (reason != null) {
                /*
                 * A refusal (the guide is taken, the cab is
                 * offline) is as much a failure as an exception, and
                 * must mark the selection the same way. Otherwise a
                 * traveller sees one stay marked unavailable and
                 * another silently left as though it were still
                 * arranged.
                 */
                markUnavailableAfterwards(selectionId, trip);
            }

            return reason;
        } catch (RuntimeException ex) {
            /*
             * Logged, not swallowed. The caller turns this into a
             * recovery state, but a failure nobody can see is a
             * failure nobody will fix.
             */
            log.warn("Trip {} selection {} could not be booked: {}",
                    trip.getTripId(),
                    selectionId,
                    ex.getMessage());

            markUnavailableAfterwards(selectionId, trip);

            return describe(selectionId) + ": " + ex.getMessage();
        }
    }

    /**
     * Load, reserve and link, as one unit that either completes or
     * leaves nothing behind.
     *
     * @return null on success, otherwise the reason it failed.
     */
    private String reserveWithinTransaction(
            Long selectionId,
            Long travellerId,
            Trip trip
    ) {
        TripSelection selection = selectionRepository
                .findById(selectionId)
                .orElseThrow(() -> new IllegalStateException(
                        "the selected service no longer exists"
                ));

        User traveller = userRepository
                .findById(travellerId)
                .orElseThrow(() -> new IllegalStateException(
                        "the traveller account could not be loaded"
                ));

        return switch (selection.getSelectionType()) {
            case HOTEL -> bookHotel(selection, traveller, trip);
            case GUIDE -> bookGuide(selection, traveller, trip);
            case CAB -> bookCab(selection, traveller, trip);
            case ACTIVITY -> bookActivity(
                    selection, traveller, trip
            );
        };
    }

    private String bookHotel(
            TripSelection selection,
            User traveller,
            Trip trip
    ) {
        Long selectionId = selection.getSelectionId();

        if (selection.getRoomType() == null) {
            return describe(selectionId)
                    + " has no room type, so there is nothing to "
                    + "reserve";
        }

        if (selection.getCheckIn() == null
                || selection.getCheckOut() == null) {
            return describe(selectionId)
                    + " has no dates, so availability cannot be "
                    + "checked";
        }

        /*
         * The id the call returns is used, not "the most recent
         * booking for this user". Two checkouts can be in flight at
         * once, and guessing would attach this trip's stay to
         * somebody else's reservation.
         */
        var created = bookingService.createHotelBooking(
                traveller.getUserId(),
                new CreateHotelBookingRequest(
                        selection.getRoomType().getRoomTypeId(),
                        selection.getCheckIn(),
                        selection.getCheckOut(),
                        selection.getGuests() == null
                                ? 1
                                : selection.getGuests(),
                        selection.getRooms() == null
                                ? 1
                                : selection.getRooms(),
                        traveller.getFullName(),
                        traveller.getEmail(),
                        traveller.getPhoneNumber() == null
                                ? "0000000000"
                                : traveller.getPhoneNumber(),
                        null
                )
        );

        if (created.bookingId() == null) {
            throw new IllegalStateException(
                    "the booking reference was not returned"
            );
        }

        Booking booking = bookingRepository
                .findById(created.bookingId())
                .orElseThrow(() -> new IllegalStateException(
                        "the booking row was not written"
                ));

        link(selection, booking, trip);

        return null;
    }

    /**
     * Reserves a guide for the tour date.
     *
     * <p>The guide is locked before the availability check for the
     * same reason inventory rows are: two checkouts can reach this
     * at once, and without the lock both would read "free" and
     * both would book. The unique index on (guide, date) is the
     * backstop; the lock is what stops it being hit routinely.
     */
    private String bookGuide(
            TripSelection selection,
            User traveller,
            Trip trip
    ) {
        Long selectionId = selection.getSelectionId();

        if (selection.getCheckIn() == null) {
            return describe(selectionId)
                    + " has no tour date, so the guide could not "
                    + "be reserved";
        }

        Guide guide = guideRepository
                .findByIdForUpdate(selection.getTargetId())
                .orElseThrow(() -> new IllegalStateException(
                        "this guide is no longer listed"
                ));

        if (!Boolean.TRUE.equals(guide.getVerified())
                || !Boolean.TRUE.equals(guide.getActive())) {
            return describe(selectionId)
                    + " refers to a guide who is not currently "
                    + "bookable";
        }

        LocalDate tourDate = selection.getCheckIn();

        if (guideReservationRepository
                .existsByGuide_GuideIdAndTourDate(
                        guide.getGuideId(), tourDate
                )) {
            return describe(selectionId)
                    + ": the guide is already booked on "
                    + tourDate;
        }

        Booking booking = newBooking(
                traveller, selection, BookingType.GUIDE
        );

        GuideReservation reservation = new GuideReservation();
        reservation.setBooking(booking);
        reservation.setGuide(guide);
        reservation.setTourDate(tourDate);

        /*
         * Booking first. The reservation holds a reference to
         * it, and Hibernate refuses to persist a row that points at
         * an unsaved transient instance -- the reverse order fails
         * with TransientPropertyValueException.
         */
        bookingRepository.save(booking);
        guideReservationRepository.save(reservation);

        link(selection, booking, trip);

        return null;
    }

    /**
     * Reserves a cab as a ride attached to its booking.
     *
     * <p>A ride is created rather than only a booking because the
     * driver needs a job with a pickup, and a booking on its own
     * gives them nothing to drive. The two are written together so
     * a paid ride can never exist without a booking to refund.
     */
    private String bookCab(
            TripSelection selection,
            User traveller,
            Trip trip
    ) {
        Long selectionId = selection.getSelectionId();

        Cab cab = cabRepository
                .findById(selection.getTargetId())
                .orElseThrow(() -> new IllegalStateException(
                        "this cab is no longer listed"
                ));

        if (!Boolean.TRUE.equals(cab.getAvailable())
                || !Boolean.TRUE.equals(cab.getActive())) {
            return describe(selectionId)
                    + " refers to a cab that is not currently "
                    + "bookable";
        }

        if (selection.getCheckIn() == null) {
            return describe(selectionId)
                    + " has no pickup date, so the cab could not "
                    + "be reserved";
        }

        Booking booking = newBooking(
                traveller, selection, BookingType.CAB
        );

        String city = selection.getTripCity() == null
                || selection.getTripCity().getCity() == null
                ? "the trip"
                : selection.getTripCity().getCity().getName();

        CabRide ride = new CabRide();
        ride.setUser(traveller);
        ride.setCab(cab);
        ride.setBooking(booking);
        ride.setPickupLocation(city);
        ride.setDropLocation(city);
        ride.setPickupTime(selection.getCheckIn()
                .atTime(9, 0));
        ride.setDistanceKm(BigDecimal.ZERO);
        ride.setFareAmount(selection.getQuotedAmount());
        ride.setStatus(RideStatus.CONFIRMED);
        ride.setOtpCode(otp());

        bookingRepository.save(booking);
        cabRideRepository.save(ride);

        link(selection, booking, trip);

        return null;
    }
    /**
     * Books an attraction visit as a paid entitlement.
     *
     * <p>An attraction has no inventory to hold the way a room,
     * a guide or a cab does: there is no operator to assign and
     * no counter that needs to know this traveller is coming.
     * What was bought is the ticket, and the booking row is the
     * proof of it -- typed ACTIVITY so settlement (FR-34) and
     * the bookings page know what they are pricing or labelling.
     *
     * <p>Two things still matter before selling that ticket:
     * the place must still exist, and it must still be open.
     * Both are read from the place itself, because the targetId
     * alone proves only that something once existed.
     */
    private String bookActivity(
            TripSelection selection,
            User traveller,
            Trip trip
    ) {
        Long selectionId = selection.getSelectionId();

        if (selection.getCheckIn() == null) {
            return describe(selectionId)
                    + " has no visit date, so the activity could "
                    + "not be booked";
        }

        TouristPlace place = placeRepository
                .findById(selection.getTargetId())
                .orElseThrow(() -> new IllegalStateException(
                        "this activity is no longer listed"
                ));

        if (!Boolean.TRUE.equals(place.getActive())) {
            return describe(selectionId)
                    + " refers to a place that is no longer open "
                    + "to visitors";
        }

        Booking booking = newBooking(
                traveller, selection, BookingType.ACTIVITY
        );

        bookingRepository.save(booking);

        /*
         * Fill the denormalised handle when the cart did not, so
         * the bill and the voucher can name the place without a
         * second lookup. An existing link is the cart's own
         * resolution and is never overwritten.
         */
        if (selection.getPlace() == null) {
            selection.setPlace(place);
        }

        link(selection, booking, trip);

        return null;
    }

    private Booking newBooking(
            User traveller,
            TripSelection selection,
            BookingType type
    ) {
        Booking booking = new Booking();
        booking.setUser(traveller);
        booking.setBookingType(type);
        booking.setBookingReference(
                "TB-" + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 12)
                        .toUpperCase()
        );
        booking.setTotalAmount(selection.getQuotedAmount());
        booking.setCurrency(selection.getCurrency());
        /*
         * CONFIRMED and PAID, not PENDING and UNPAID. This runs
         * only after the trip's own payment has cleared, so the
         * money is already gone. Marking these unpaid would make a
         * paid trip look outstanding to cancellation and refund, and
         * would let a refund be issued for money never taken.
         */
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setGuestCount(selection.getGuests() == null
                ? 1
                : selection.getGuests());
        booking.setGuestName(traveller.getFullName());
        booking.setGuestEmail(traveller.getEmail());
        booking.setGuestPhone(
                traveller.getPhoneNumber() == null
                        ? "0000000000"
                        : traveller.getPhoneNumber()
        );
        /*
         * createdAt and updatedAt are stamped by the entity's own
         * callbacks. Setting them here would write a field this
         * service does not own, and would be overwritten on flush
         * anyway.
         */
        return booking;
    }

    private void link(
            TripSelection selection,
            Booking booking,
            Trip trip
    ) {
        selection.markBooked(booking);

        log.info("Trip {} selection {} booked as {} ({})",
                trip.getTripId(),
                selection.getSelectionId(),
                booking.getBookingReference(),
                booking.getBookingType());
    }

    private String otp() {
        return String.valueOf(
                1000 + (int) (Math.random() * 9000)
        );
    }

    /**
     * Records why a selection could not be held.
     *
     * <p>Best effort by design. The booking transaction has already
     * rolled back and this one may be too, so failing to write the
     * reason must not turn a recoverable situation into a second
     * error on top of the first.
     */
    private void markUnavailableAfterwards(
            Long selectionId,
            Trip trip
    ) {
        try {
            requiresNew.executeWithoutResult(status ->
                    selectionRepository.findById(selectionId)
                            .ifPresent(selection ->
                                    selection.markUnavailable(
                                            "Could not be reserved "
                                                    + "after payment"
                                    ))
            );
        } catch (RuntimeException ex) {
            log.error("Trip {} selection {} could not even be "
                            + "marked unavailable",
                    trip.getTripId(), selectionId, ex);
        }
    }

    private String describe(Long selectionId) {
        return "Stay selection " + selectionId;
    }
}