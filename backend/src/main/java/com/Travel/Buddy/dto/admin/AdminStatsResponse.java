package com.Travel.Buddy.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The command centre's answer. (FR-30)
 *
 * <p>Everything here is a fact the backend can prove: counts from
 * the tables that back the queues, revenue from PAID bookings
 * only, and alerts from the queues that actually need a human.
 * The old console showed a hard-coded "Active", "₹0.00" and
 * "Normal" regardless of what the platform contained.
 */
public record AdminStatsResponse(

        long totalUsers,

        long activeUsers,

        long suspendedUsers,

        long totalBookings,

        long confirmedBookings,

        long cancelledBookings,

        /** Paid gross. Unpaid holds are not revenue. */
        BigDecimal grossRevenue,

        /** Bookings whose payment status is REFUNDED. */
        BigDecimal refundedRevenue,

        /** Depth of the three review queues. */
        PendingApprovals pendingApprovals,

        /** Disputes still awaiting a ruling. */
        long openDisputes,

        /** Cancellations with money still owed to travellers. */
        long refundsPending,

        /** Newest paid sales, for the live feed. */
        List<RecentSale> recentSales
) {

    public record PendingApprovals(
            long partnerApplications,
            long properties,
            long reviews
    ) {
    }

    public record RecentSale(
            Long bookingId,
            String bookingReference,
            String guestName,
            String bookingType,
            BigDecimal totalAmount,
            String currency,
            String paymentStatus,
            String bookingStatus,
            LocalDateTime createdAt
    ) {
    }
}