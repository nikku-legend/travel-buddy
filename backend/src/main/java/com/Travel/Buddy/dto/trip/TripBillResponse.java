package com.Travel.Buddy.dto.trip;

import com.Travel.Buddy.entity.TripBillLineType;
import com.Travel.Buddy.entity.TripSelectionStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * The itemized trip bill. (SRS 2.2 section 5.1, TP-09)
 *
 * <p>Section 5.1 asks for hotel, transport, guide and activity
 * subtotals, taxes and fees shown explicitly, discounts
 * separately, and a clear total. Every one of those is a field
 * here rather than something the client has to derive.
 */
public record TripBillResponse(

        Long tripId,

        Long checkoutId,

        List<BillLine> lines,

        /* Per-category subtotals, which is how section 5.1 asks
         * for the bill to be grouped. */
        BigDecimal hotelSubtotal,

        BigDecimal guideSubtotal,

        BigDecimal cabSubtotal,

        BigDecimal activitySubtotal,

        BigDecimal taxAmount,

        BigDecimal feeAmount,

        BigDecimal discountAmount,

        BigDecimal total,

        String currency,

        /*
         * What the traveller saw in the cart before
         * revalidation. A different total is the "something
         * changed" signal that section 6 requires be surfaced
         * before charging.
         */
        BigDecimal quotedTotal,

        boolean priceChanged,

        List<String> warnings
) {
    public record BillLine(
            Long selectionId,
            TripBillLineType lineType,
            String label,
            String detail,
            int quantity,
            BigDecimal unitAmount,
            BigDecimal amount,
            String currency
    ) {
    }
}