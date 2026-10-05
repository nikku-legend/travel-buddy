package com.Travel.Buddy.dto.trip;

import com.Travel.Buddy.entity.TripCheckout;
import com.Travel.Buddy.entity.TripCheckoutStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A checkout, as the client is allowed to see it. (SRS 2.2 TP-10)
 *
 * <p>This exists because the controller used to return the
 * {@link TripCheckout} entity straight out of the service. That
 * failed at the worst possible moment: the transaction had already
 * committed, and only the JSON write failed, when Jackson walked
 * into a lazy {@code User} proxy with no session left to open it.
 * The client received a 500 for a payment that had genuinely
 * succeeded and a trip that was genuinely confirmed -- money moved
 * and the traveller was told it failed.
 *
 * <p>Serialising a managed entity is never safe for exactly this
 * reason. The DTO also keeps internals private: the recovery and
 * failure reasons are operator-facing, and the raw gateway order id
 * is not something a browser needs echoed back.
 */
public record TripCheckoutResponse(

        Long checkoutId,

        Long tripId,

        String checkoutReference,

        TripCheckoutStatus status,

        BigDecimal totalAmount,

        String currency,

        BigDecimal taxAmount,

        BigDecimal feeAmount,

        BigDecimal discountAmount,

        /*
         * What the traveller was quoted before revalidation. Kept
         * so a client can still show the change after the fact.
         */
        BigDecimal quotedTotal,

        boolean priceChanged,

        /*
         * Present only on a FAILED checkout. Null otherwise, so
         * "no reason recorded" is distinguishable from "recorded as
         * empty".
         */
        String failureReason,

        /*
         * Why a paid checkout could not be completed. Set only
         * for RECOVERY_REQUIRED, and deliberately distinct from
         * failureReason: a failed payment was declined, whereas
         * recovery means the money cleared and something still has
         * to be put right. A traveller owed a refund must not be shown
         * the two as the same kind of event.
         */
        String recoveryReason,

        LocalDateTime createdAt,

        LocalDateTime confirmedAt
) {
    public static TripCheckoutResponse of(
            TripCheckout c
    ) {
        if (c == null) {
            return null;
        }

        return new TripCheckoutResponse(
                c.getCheckoutId(),
                c.getTrip() == null
                        ? null
                        : c.getTrip().getTripId(),
                c.getCheckoutReference(),
                c.getStatus(),
                c.getTotalAmount(),
                c.getCurrency(),
                c.getTaxAmount(),
                c.getFeeAmount(),
                c.getDiscountAmount(),
                c.getQuotedTotal(),
                c.getQuotedTotal() != null
                        && c.getTotalAmount() != null
                        && c.getQuotedTotal()
                        .compareTo(c.getTotalAmount()) != 0,
                c.getFailureReason(),
                c.getRecoveryReason(),
                c.getCreatedAt(),
                c.getConfirmedAt()
        );
    }
}