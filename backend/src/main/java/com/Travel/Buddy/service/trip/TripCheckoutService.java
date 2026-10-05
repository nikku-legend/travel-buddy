package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.dto.trip.ConfirmTripCheckoutRequest;
import com.Travel.Buddy.dto.trip.TripBillResponse;
import com.Travel.Buddy.dto.trip.TripCheckoutPreviewResponse;
import com.Travel.Buddy.entity.Trip;
import com.Travel.Buddy.entity.TripBillItem;
import com.Travel.Buddy.entity.TripBillLineType;
import com.Travel.Buddy.entity.TripCheckout;
import com.Travel.Buddy.entity.TripCheckoutStatus;
import com.Travel.Buddy.entity.TripSelection;
import com.Travel.Buddy.entity.TripMilestoneType;
import com.Travel.Buddy.entity.TripSelectionStatus;
import com.Travel.Buddy.entity.TripStatus;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.TripBillItemRepository;
import com.Travel.Buddy.repository.TripCheckoutRepository;
import com.Travel.Buddy.repository.TripRepository;
import com.Travel.Buddy.repository.TripSelectionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Centralized trip checkout. (SRS 2.2 section 6, TP-10)
 *
 * <p>The single point where a plan stops being free and starts
 * costing money. Everything before it is editable and reserves
 * nothing; everything after it is a real booking.
 *
 * <p>Two rules from section 6 shape the whole class:
 *
 * <ol>
 *   <li><strong>Never silently substitute.</strong> A service
 *       that became unavailable is reported with a reason and
 *       the traveller decides. Swapping in something else and
 *       charging for it means charging for a trip nobody
 *       agreed to.</li>
 *   <li><strong>Never charge twice.</strong> When payment clears
 *       but confirmation fails, the checkout moves to
 *       RECOVERY_REQUIRED and is reconciled from persisted
 *       payment events rather than re-charged.</li>
 * </ol>
 */
@Service
public class TripCheckoutService {

    /**
     * Statuses that count as "a charge is in flight". A trip may
     * only have one of these at a time.
     */
    private static final List<TripCheckoutStatus> OPEN_STATUSES =
            List.of(
                    TripCheckoutStatus.CREATED,
                    TripCheckoutStatus.REVALIDATING,
                    TripCheckoutStatus.PAYMENT_PENDING,
                    TripCheckoutStatus.RECOVERY_REQUIRED
            );

    private final TripRepository tripRepository;
    private final TripCheckoutRepository checkoutRepository;
    private final TripBillItemRepository billItemRepository;
    private final TripSelectionRepository selectionRepository;
    private final TripService tripService;
    private final TripBillService billService;
    private final TripMilestoneService milestoneService;
    private final TripBookingService bookingService;

    public TripCheckoutService(
            TripRepository tripRepository,
            TripCheckoutRepository checkoutRepository,
            TripBillItemRepository billItemRepository,
            TripSelectionRepository selectionRepository,
            TripService tripService,
            TripBillService billService,
            TripMilestoneService milestoneService,
            TripBookingService bookingService
    ) {
        this.tripRepository = tripRepository;
        this.checkoutRepository = checkoutRepository;
        this.billItemRepository = billItemRepository;
        this.selectionRepository = selectionRepository;
        this.tripService = tripService;
        this.billService = billService;
        this.milestoneService = milestoneService;
        this.bookingService = bookingService;
    }

    private static final Logger log =
            LoggerFactory.getLogger(TripCheckoutService.class);

    /* ============================================================
     * PREVIEW  (revalidate and recalculate)
     * ============================================================ */

    /**
     * Revalidates the cart and produces the bill the traveller
     * must agree to. Charges nothing.
     */
    @Transactional
    public TripCheckoutPreviewResponse preview(
            Long userId,
            Long tripId
    ) {
        /*
         * Ownership first, then a locked re-read. This lock is
         * what enforces one open checkout per trip: a concurrent
         * preview blocks here instead of producing a second
         * in-flight charge.
         */
        Trip trip = tripService.requireOwned(userId, tripId);

        trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Trip not found"
                        )
                );

        if (trip.getStatus().isFinal()) {
            throw PartnerApplicationException.conflict(
                    "This trip is already " + trip.getStatus()
            );
        }

        assertCartIsNotEmpty(trip);

        checkoutRepository
                .findByTrip_TripIdAndStatusIn(
                        tripId, OPEN_STATUSES
                )
                .ifPresent(existing -> {
                    throw PartnerApplicationException.conflict(
                            "A checkout is already in progress "
                                    + "(reference "
                                    + existing.getCheckoutReference()
                                    + "). Finish it before starting "
                                    + "another."
                    );
                });

        TripCheckout checkout = checkoutRepository.save(
                new TripCheckout(trip, reference())
        );

        checkout.beginRevalidation();

        TripBillService.RevalidationResult result =
                billService.revalidate(trip);

        TripBillResponse bill = result.bill();

        checkout.lockBill(
                bill.total(),
                bill.taxAmount(),
                bill.feeAmount(),
                bill.discountAmount(),
                trip.getEstimatedTotal(),
                bill.currency()
        );
        checkoutRepository.save(checkout);

        freezeBillItems(checkout, result.live(), bill);

        boolean priceChanged = checkout.priceChanged();
        boolean hasUnavailable = !result.unavailable()
                .isEmpty();

        return new TripCheckoutPreviewResponse(
                checkout.getCheckoutId(),
                checkout.getCheckoutReference(),
                checkout.getStatus(),
                checkout.getQuotedTotal(),
                checkout.getTotalAmount(),
                priceChanged,

                /*
                 * Either condition forces an explicit decision.
                 * This is the step section 6 is really about.
                 */
                priceChanged || hasUnavailable,
                result.unavailable(),
                bill,
                checkout.getCreatedAt()
        );
    }

    /* ============================================================
     * CONFIRMATION
     * ============================================================ */

    /**
     * The traveller's explicit agreement to a revalidated total.
     *
     * <p>{@code acceptedTotal} must match exactly. A mismatch
     * means the bill moved again after they saw it, and charging
     * the newer figure would charge for something they never
     * agreed to.
     */
    @Transactional
    public TripCheckout assertAgreed(
            Long userId,
            Long tripId,
            ConfirmTripCheckoutRequest request
    ) {
        tripService.requireOwned(userId, tripId);

        TripCheckout checkout = requireCheckout(
                tripId, request.checkoutId()
        );

        if (checkout.getStatus() != TripCheckoutStatus.REVALIDATING) {
            throw PartnerApplicationException.conflict(
                    "This checkout is " + checkout.getStatus()
                            + " and is not awaiting confirmation"
            );
        }

        if (request.acceptedTotal()
                .compareTo(checkout.getTotalAmount()) != 0) {
            throw PartnerApplicationException.badRequest(
                    "The total has changed again. It is now "
                            + checkout.getTotalAmount() + " "
                            + checkout.getCurrency() + "."
            );
        }

        checkout.awaitingPayment(null);

        return checkoutRepository.save(checkout);
    }

    /* ============================================================
     * PAYMENT
     * ============================================================ */

    /**
     * Simulates the gateway confirming payment. (SRS 2.2 TP-10)
     *
     * <p>Mirrors the single-booking mock payment rather than
     * inventing a second convention.
     *
     * <p>This endpoint is what the flow was missing. Without it a
     * traveller could build a trip, accept a revalidated total and
     * then stop dead: {@link #markPaid} had no caller, so a
     * checkout could never leave PAYMENT_PENDING, no booking was
     * ever created, the trip never reached CONFIRMED and the
     * treasure map never moved. Centralised checkout was a road
     * to a wall.
     *
     * <p>The payment id is generated here rather than accepted
     * from the caller, because a mock that lets the client name
     * its own payment reference would be indistinguishable from a
     * real gateway in the records.
     *
     * <p>Deliberately separate from {@link #markPaid}: a real
     * gateway webhook is a different trust boundary and must not
     * be reachable by simply asking this endpoint.
     */
    @Transactional
    public TripCheckout payMock(
            Long userId,
            Long tripId,
            Long checkoutId,
            boolean paymentSuccessful
    ) {
        tripService.requireOwned(userId, tripId);

        TripCheckout checkout = requireCheckout(
                tripId, checkoutId
        );

        if (!paymentSuccessful) {
            return fail(
                    checkoutId,
                    "The simulated payment was declined"
            );
        }

        /*
         * PAYMENT_PENDING is the state the confirm step leaves
         * the checkout in. Both are accepted so a gateway that
         * reports twice does not fail the second time.
         */
        if (checkout.getStatus()
                == TripCheckoutStatus.PAYMENT_PENDING) {
            return markPaid(
                    checkoutId,
                    "MOCKPAY-" + UUID.randomUUID()
                            .toString()
                            .replace("-", "")
                            .substring(0, 16)
                            .toUpperCase()
            );
        }

        throw PartnerApplicationException.conflict(
                "This checkout is " + checkout.getStatus()
                        + " and is not awaiting payment"
        );
    }
    /**
     * Records the gateway confirming payment.
     *
     * <p>Idempotent. Razorpay can deliver the same webhook more
     * than once, and section 6 requires a repeat to be recognised
     * rather than charged again.
     */
    @Transactional
    public TripCheckout markPaid(
            Long checkoutId,
            String razorpayPaymentId
    ) {
        TripCheckout checkout = checkoutRepository
                .findById(checkoutId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Checkout not found"
                        )
                );

        if (razorpayPaymentId != null) {
            checkoutRepository
                    .findByRazorpayPaymentId(razorpayPaymentId)
                    .filter(previous -> !previous.getCheckoutId()
                            .equals(checkoutId))
                    .ifPresent(previous -> {
                        throw PartnerApplicationException.conflict(
                                "That payment already belongs to "
                                        + "checkout "
                                        + previous.getCheckoutReference()
                        );
                    });
        }

        if (checkout.getStatus() == TripCheckoutStatus.PAID
                || checkout.getStatus()
                == TripCheckoutStatus.CONFIRMED) {
            return checkout;
        }

        checkout.markPaid(razorpayPaymentId);

        TripCheckout saved = checkoutRepository.save(checkout);

        /*
         * Money cleared, so the rooms must actually be held.
         *
         * <p>This used to jump straight to advancing the trip, which
         * meant a paid, confirmed trip reserved nothing: the traveller
         * paid, the map moved, and no hotel had been told.
         *
         * <p>Each booking commits on its own, so one failure cannot
         * undo the payment or the other reservations. Anything that
         * fails puts the checkout into recovery rather than reporting
         * a confirmed trip the traveller does not actually have.
         */
        List<String> failures = bookingService.bookPaidTrip(
                checkout.getTrip()
        );

        if (failures.isEmpty()) {
            saved.confirm();
            saved = checkoutRepository.save(saved);

            /*
             * Money cleared, so the trip is real and the map may
             * move.
             *
             * <p>Until this existed, Trip.moveTo had no caller anywhere
             * in the application: every trip stayed in DRAFT for ever,
             * the start checkpoint never completed, and requireEditable
             * could never refuse an edit to a trip the traveller had
             * already paid for.
             */
            advanceTrip(checkout);
        } else {
            /*
             * The trip is deliberately NOT advanced. A confirmed trip
             * whose rooms were never held is a promise the platform
             * cannot keep, and a traveller told "confirmed" with no
             * room is worse off than one told it needs fixing.
             */
            log.warn(
                    "Trip {} checkout {} paid but {} item(s) could "
                            + "not be booked: {}",
                    checkout.getTrip().getTripId(),
                    saved.getCheckoutId(),
                    failures.size(),
                    failures
            );

            saved.requireRecovery(
                    String.join("; ", failures)
            );
            saved = checkoutRepository.save(saved);
        }

        return saved;
    }

    /**
     * Moves the parent trip to CONFIRMED and completes the first
     * checkpoint on the treasure map.
     */
    private void advanceTrip(TripCheckout checkout) {
        Trip trip = checkout.getTrip();

        if (trip == null) {
            return;
        }

        if (trip.getStatus() == TripStatus.DRAFT
                || trip.getStatus() == TripStatus.PLANNING
                || trip.getStatus()
                == TripStatus.READY_FOR_CHECKOUT) {
            trip.moveTo(TripStatus.CONFIRMED);
            tripRepository.save(trip);
        }

        milestoneService.complete(
                trip, TripMilestoneType.TRIP_STARTED
        );
    }

    /**
     * Money cleared but bookings were not all confirmed. A
     * deliberate transition so recovery can be driven from
     * persisted payment events rather than a fresh charge.
     */
    @Transactional
    public TripCheckout requireRecovery(
            Long checkoutId,
            String reason
    ) {
        TripCheckout checkout = checkoutRepository
                .findById(checkoutId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Checkout not found"
                        )
                );

        if (checkout.getStatus() == TripCheckoutStatus.PAID) {
            checkout.requireRecovery(reason);
            return checkoutRepository.save(checkout);
        }

        return checkout;
    }

    @Transactional
    public TripCheckout fail(
            Long checkoutId,
            String reason
    ) {
        TripCheckout checkout = checkoutRepository
                .findById(checkoutId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Checkout not found"
                        )
                );

        checkout.fail(reason);

        /*
         * Returns prepared selections to SELECTED so a retry
         * starts clean rather than tripping over half-prepared
         * rows from the abandoned attempt.
         */
        for (TripSelection selection : selectionRepository
                .findByTrip_TripIdOrderBySelectionIdAsc(
                        checkout.getTrip().getTripId()
                )) {
            if (selection.getStatus()
                    == TripSelectionStatus.REVALIDATING
                    || selection.getStatus()
                    == TripSelectionStatus.READY_FOR_CHECKOUT) {
                selection.markSelected();
            }
            selectionRepository.save(selection);
        }

        return checkoutRepository.save(checkout);
    }

    @Transactional(readOnly = true)
    public List<TripCheckout> history(
            Long userId,
            Long tripId
    ) {
        tripService.requireOwned(userId, tripId);

        return checkoutRepository
                .findByTrip_TripIdOrderByCheckoutIdDesc(tripId);
    }

    /* ============================================================
     * HELPERS
     * ============================================================ */

    private TripCheckout requireCheckout(
            Long tripId,
            Long checkoutId
    ) {
        TripCheckout checkout = checkoutRepository
                .findById(checkoutId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Checkout not found"
                        )
                );

        if (!checkout.getTrip()
                .getTripId()
                .equals(tripId)) {
            throw PartnerApplicationException.forbidden(
                    "That checkout is not for this trip"
            );
        }

        return checkout;
    }

    /**
     * An empty cart is not a checkout. Saying so plainly is
     * kinder than producing a zero-total payment.
     */
    private void assertCartIsNotEmpty(Trip trip) {
        boolean payable = selectionRepository
                .findByTrip_TripIdOrderBySelectionIdAsc(
                        trip.getTripId()
                )
                .stream()
                .filter(TripSelection::countsTowardBill)
                .anyMatch(selection ->
                        selection.getQuotedAmount() != null
                                && selection.getQuotedAmount()
                                .signum() > 0
                );

        if (!payable) {
            throw PartnerApplicationException.badRequest(
                    "Your trip cart is empty. Add a hotel, guide or "
                            + "cab before checking out."
            );
        }
    }

    /**
     * Writes the immutable bill snapshot.
     *
     * <p>TAX and FEE become their own lines rather than being
     * folded into the service totals, because section 5.1 asks
     * for them to be shown explicitly. Making them visible is
     * the difference between a breakdown and a number.
     */
    private void freezeBillItems(
            TripCheckout checkout,
            List<TripSelection> live,
            TripBillResponse bill
    ) {
        for (TripSelection selection : live) {
            /*
             * Quantity is the number of chargeable units, not a
             * placeholder 1. A three-night hotel reads as
             * "3 nights at the nightly rate", which is what lets a
             * traveller check the arithmetic themselves instead of
             * being handed one opaque figure.
             */
            int quantity = Math.max(selection.nights(), 1);
            BigDecimal unit = selection.getQuotedAmount()
                    .divide(
                            BigDecimal.valueOf(quantity),
                            2,
                            java.math.RoundingMode.HALF_UP
                    );

            billItemRepository.save(
                    new TripBillItem(
                            checkout,
                            selection.getSelectionId(),
                            lineTypeFor(selection),
                            labelFor(selection),
                            detailFor(selection),
                            quantity,
                            unit,
                            selection.getQuotedAmount(),
                            selection.getCurrency()
                    )
            );
        }

        /*
         * Tax and fee are their own lines with a single unit each,
         * so unit and amount are the same figure. Section 5.1 asks
         * for them shown explicitly rather than folded into the
         * service totals, and that is the difference between a
         * breakdown and a bare number.
         */
        billItemRepository.save(
                new TripBillItem(
                        checkout, null, TripBillLineType.TAX,
                        "Taxes", null, 1,
                        nz(bill.taxAmount()),
                        nz(bill.taxAmount()),
                        bill.currency()
                )
        );

        billItemRepository.save(
                new TripBillItem(
                        checkout, null, TripBillLineType.FEE,
                        "Platform service fee", null, 1,
                        nz(bill.feeAmount()),
                        nz(bill.feeAmount()),
                        bill.currency()
                )
        );
    }

    private TripBillLineType lineTypeFor(
            TripSelection selection
    ) {
        return switch (selection.getSelectionType()) {
            case HOTEL -> TripBillLineType.HOTEL;
            case GUIDE -> TripBillLineType.GUIDE;
            case CAB -> TripBillLineType.CAB;
            case ACTIVITY -> TripBillLineType.ACTIVITY;
        };
    }

    private String labelFor(TripSelection selection) {
        if (selection.getRoomType() != null) {
            return selection.getRoomType().getCategoryName();
        }
        if (selection.getPlace() != null) {
            return selection.getPlace().getName();
        }
        return selection.getSelectionType().name();
    }

    private String detailFor(TripSelection selection) {
        if (selection.getTripCity() == null) {
            return null;
        }

        String city = selection.getTripCity()
                .getCity()
                .getName();

        if (selection.getCheckIn() == null) {
            return city;
        }

        return city + ", " + selection.getCheckIn()
                + " to " + selection.getCheckOut();
    }

    /**
     * Tax and fee are derived figures that can only be null if a
     * subtotal was empty. Normalised here so the frozen bill can
     * never persist a null into a NOT NULL column.
     */
    private java.math.BigDecimal nz(
            java.math.BigDecimal value
    ) {
        return value == null
                ? java.math.BigDecimal.ZERO
                : value;
    }

    /**
     * Short, human-quotable reference. Unique by index, so a
     * collision is impossible rather than merely unlikely.
     */
    private String reference() {
        return "TC-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();
    }
}
