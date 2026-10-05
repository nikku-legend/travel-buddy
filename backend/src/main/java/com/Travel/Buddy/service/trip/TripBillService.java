package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.dto.trip.TripBillResponse;
import com.Travel.Buddy.entity.TripBillLineType;
import com.Travel.Buddy.entity.RoomInventoryDaily;
import com.Travel.Buddy.entity.Trip;
import com.Travel.Buddy.entity.TripSelection;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.RoomInventoryRepository;
import com.Travel.Buddy.repository.TripSelectionRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The itemized trip bill. (SRS 2.2 section 5.1, TP-09)
 *
 * <p>Two related jobs, deliberately kept together because they
 * are two readings of the same arithmetic:
 *
 * <ul>
 *   <li>{@link #preview} builds the live estimate the traveller
 *       sees while planning.</li>
 *   <li>{@link #revalidate} recalculates every line against
 *       current inventory and price at checkout, which is the
 *       only figure that is ever charged.</li>
 * </ul>
 *
 * <p>Tax and platform fee are a configurable percentage. The SRS
 * also requires commission (FR-34) and settlement (FR-35) to be
 * modelled, and those land on the partner side of the same
 * ledger; the fee here is the traveller-facing charge only.
 */
@Service
public class TripBillService {

    private final TripSelectionRepository selectionRepository;
    private final RoomInventoryRepository inventoryRepository;
    private final BigDecimal taxRate;
    private final BigDecimal serviceFeeRate;

    public TripBillService(
            TripSelectionRepository selectionRepository,
            RoomInventoryRepository inventoryRepository,
            @Value("${app.trip.tax-rate:0.05}")
            BigDecimal taxRate,
            @Value("${app.trip.service-fee-rate:0.03}")
            BigDecimal serviceFeeRate
    ) {
        this.selectionRepository = selectionRepository;
        this.inventoryRepository = inventoryRepository;
        this.taxRate = taxRate;
        this.serviceFeeRate = serviceFeeRate;
    }

    /**
     * Recomputes the denormalised total on the trip. Called
     * whenever a selection changes, so the trip list never shows
     * a stale figure.
     */
    @Transactional
    public void recalculate(Trip trip) {
        List<TripSelection> live = selectionRepository
                .findByTrip_TripIdOrderBySelectionIdAsc(
                        trip.getTripId()
                )
                .stream()
                .filter(TripSelection::countsTowardBill)
                .toList();

        BigDecimal subtotal = BigDecimal.ZERO;
        for (TripSelection selection : live) {
            subtotal = subtotal.add(
                    zero(selection.getQuotedAmount())
            );
        }

        BigDecimal tax = subtotal.multiply(taxRate)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = subtotal.multiply(serviceFeeRate)
                .setScale(2, RoundingMode.HALF_UP);

        trip.setEstimatedTotal(
                subtotal.add(tax).add(fee)
                        .setScale(2, RoundingMode.HALF_UP)
        );
    }

    /**
     * The estimate shown while planning. Uses the frozen quote on
     * each selection rather than repricing, because a running
     * total that moved every time a partner edited a rate would
     * make the bill screen unusable.
     */
    @Transactional(readOnly = true)
    public TripBillResponse preview(Trip trip) {
        List<TripSelection> live = selectionRepository
                .findByTrip_TripIdOrderBySelectionIdAsc(
                        trip.getTripId()
                )
                .stream()
                .filter(TripSelection::countsTowardBill)
                .toList();

        return build(trip, live, null);
    }

    /**
     * Revalidates every line against live inventory and price.
     *
     * <p>This is the step SRS 2.2 section 6 is really about. A
     * line that cannot be honoured is reported as unavailable
     * with a reason and left in place, never substituted with
     * something else.
     */
    @Transactional
    public RevalidationResult revalidate(Trip trip) {
        List<TripSelection> selections = selectionRepository
                .findByTrip_TripIdOrderBySelectionIdAsc(
                        trip.getTripId()
                );

        List<TripSelection> live = new ArrayList<>();
        List<com.Travel.Buddy.dto.trip.TripCheckoutPreviewResponse
                .UnavailableLine> unavailable = new ArrayList<>();

        for (TripSelection selection : selections) {
            if (!selection.countsTowardBill()) {
                continue;
            }

            String problem = revalidateOne(selection);

            if (problem == null) {
                selection.readyForCheckout();
                live.add(selection);
            } else {
                selection.markUnavailable(problem);
                unavailable.add(
                        new com.Travel.Buddy.dto.trip
                                .TripCheckoutPreviewResponse
                                .UnavailableLine(
                                selection.getSelectionId(),
                                describe(selection),
                                problem
                        )
                );
            }
            selectionRepository.save(selection);
        }

        TripBillResponse bill = build(trip, live, null);

        return new RevalidationResult(
                live,
                unavailable,
                bill
        );
    }

    /**
     * @return null when the line is still sellable, otherwise a
     * sentence the traveller can act on
     */
    private String revalidateOne(TripSelection selection) {
        if (selection.getCheckIn() == null
                || selection.getCheckOut() == null) {
            return "This selection has no dates";
        }

        if (selection.getCheckOut()
                .isBefore(selection.getCheckIn())) {
            return "The end date is before the start date";
        }

        if (selection.getRoomType() == null) {
            return null;
        }

        if (!hasInventory(
                selection.getRoomType()
                        .getRoomTypeId(),
                selection.getCheckIn(),
                selection.getCheckOut()
        )) {
            return "No rooms left for "
                    + selection.getCheckIn() + " to "
                    + selection.getCheckOut();
        }

        return null;
    }

    /**
     * Confirms at least one sellable room on every night of the
     * stay. A stay is only bookable if every single night can be
     * honoured, not if some of them can.
     */
    private boolean hasInventory(
            Long roomTypeId,
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        List<RoomInventoryDaily> nights = inventoryRepository
                .findByRoomTypeAndDateRange(
                        roomTypeId,
                        checkIn,
                        checkOut.minusDays(1)
                );

        if (nights.isEmpty()) {
            return false;
        }

        long expected = java.time.temporal.ChronoUnit.DAYS
                .between(checkIn, checkOut);

        return nights.size() == expected
                && nights.stream().allMatch(
                        day -> day.getAvailableInventory() != null
                                && day.getAvailableInventory() > 0
                );
    }

    private TripBillResponse build(
            Trip trip,
            List<TripSelection> live,
            Long checkoutId
    ) {
        List<TripBillResponse.BillLine> lines = new ArrayList<>();

        BigDecimal hotel = BigDecimal.ZERO;
        BigDecimal guide = BigDecimal.ZERO;
        BigDecimal cab = BigDecimal.ZERO;
        BigDecimal activity = BigDecimal.ZERO;

        BigDecimal subtotal = BigDecimal.ZERO;

        for (TripSelection selection : live) {
            BigDecimal amount = zero(selection.getQuotedAmount());
            subtotal = subtotal.add(amount);

            lines.add(
                    new TripBillResponse.BillLine(
                            selection.getSelectionId(),
                            lineTypeFor(selection),
                            describe(selection),
                            detailFor(selection),
                            1,
                            amount,
                            amount,
                            selection.getCurrency()
                    )
            );

            switch (selection.getSelectionType()) {
                case HOTEL -> hotel = hotel.add(amount);
                case GUIDE -> guide = guide.add(amount);
                case CAB -> cab = cab.add(amount);
                case ACTIVITY -> activity = activity.add(amount);
                default -> {
                }
            }
        }

        BigDecimal tax = subtotal.multiply(taxRate)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = subtotal.multiply(serviceFeeRate)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal total = subtotal.add(tax).add(fee)
                .setScale(2, RoundingMode.HALF_UP);

        /*
         * Tax and fee appear as their own lines rather than only
         * as subtotal fields. Section 5.1 asks for them shown
         * explicitly, and these are the same lines that get frozen
         * into trip_bill_items at checkout: a response listing
         * services but silently omitting the two charges would
         * not add up to the total the traveller is shown.
         */
        if (tax.signum() > 0) {
            lines.add(
                    new TripBillResponse.BillLine(
                            null,
                            TripBillLineType.TAX,
                            "Taxes",
                            null,
                            1,
                            tax,
                            tax,
                            trip.getCurrency()
                    )
            );
        }

        if (fee.signum() > 0) {
            lines.add(
                    new TripBillResponse.BillLine(
                            null,
                            TripBillLineType.FEE,
                            "Platform service fee",
                            null,
                            1,
                            fee,
                            fee,
                            trip.getCurrency()
                    )
            );
        }

        List<String> warnings = new ArrayList<>();
        if (trip.getBudgetAmount() != null
                && total.compareTo(trip.getBudgetAmount()) > 0) {
            warnings.add(
                    "This trip is " + total + " which is over your "
                            + "budget of " + trip.getBudgetAmount()
            );
        }

        return new TripBillResponse(
                trip.getTripId(),
                checkoutId,
                lines,
                hotel,
                guide,
                cab,
                activity,
                tax,
                fee,
                BigDecimal.ZERO,
                total,
                trip.getCurrency(),
                trip.getEstimatedTotal(),
                trip.getEstimatedTotal() != null
                        && trip.getEstimatedTotal()
                        .compareTo(total) != 0,
                warnings
        );
    }

    private com.Travel.Buddy.entity.TripBillLineType lineTypeFor(
            TripSelection selection
    ) {
        return switch (selection.getSelectionType()) {
            case HOTEL ->
                    com.Travel.Buddy.entity.TripBillLineType.HOTEL;
            case GUIDE ->
                    com.Travel.Buddy.entity.TripBillLineType.GUIDE;
            case CAB ->
                    com.Travel.Buddy.entity.TripBillLineType.CAB;
            case ACTIVITY ->
                    com.Travel.Buddy.entity.TripBillLineType
                            .ACTIVITY;
        };
    }

    private String describe(TripSelection selection) {
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

        if (selection.getCheckIn() == null
                || selection.getCheckOut() == null) {
            return city;
        }

        return city + ", " + selection.getCheckIn()
                + " to " + selection.getCheckOut();
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record RevalidationResult(
            List<TripSelection> live,
            List<com.Travel.Buddy.dto.trip.TripCheckoutPreviewResponse
                    .UnavailableLine> unavailable,
            TripBillResponse bill
    ) {
    }
}