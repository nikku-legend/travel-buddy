package com.Travel.Buddy.dto.dispute;

import com.Travel.Buddy.entity.Dispute;
import com.Travel.Buddy.entity.DisputeStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Queue row for the admin work list. Deliberately excludes the
 * claimant's description: a long block of text per row makes the
 * queue unreadable, and the detail view is one click away.
 */
public record DisputeSummaryResponse(

        Long disputeId,

        Long bookingId,

        String bookingReference,

        String subject,

        com.Travel.Buddy.entity.DisputeCategory category,

        DisputeStatus status,

        BigDecimal requestedAmount,

        String currency,

        String raisedByName,

        String partnerName,

        DisputeStatus from,

        DisputeStatus to,

        LocalDateTime createdAt
) {
    public static DisputeSummaryResponse from(Dispute d) {
        return new DisputeSummaryResponse(
                d.getDisputeId(),
                d.getBooking() == null
                        ? null
                        : d.getBooking().getBookingId(),
                d.getBooking() == null
                        ? null
                        : d.getBooking().getBookingReference(),
                d.getSubject(),
                d.getCategory(),
                d.getStatus(),
                d.getRequestedAmount(),
                d.getBooking() == null
                        ? null
                        : d.getBooking().getCurrency(),
                d.getRaisedBy() == null
                        ? null
                        : d.getRaisedBy().getFullName(),
                d.getPartner() == null
                        ? null
                        : d.getPartner().getFullName(),
                null,
                d.getStatus(),
                d.getCreatedAt()
        );
    }

    public static List<DisputeSummaryResponse> from(
            List<Dispute> disputes
    ) {
        return disputes.stream()
                .map(DisputeSummaryResponse::from)
                .toList();
    }
}