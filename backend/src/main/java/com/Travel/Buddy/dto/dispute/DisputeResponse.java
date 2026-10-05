package com.Travel.Buddy.dto.dispute;

import com.Travel.Buddy.entity.Dispute;
import com.Travel.Buddy.entity.DisputeCategory;
import com.Travel.Buddy.entity.DisputeResolution;
import com.Travel.Buddy.entity.DisputeStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DisputeResponse(

        Long disputeId,

        Long bookingId,

        String bookingReference,

        String propertyName,

        Long raisedByUserId,

        String raisedByName,

        String partnerName,

        DisputeCategory category,

        DisputeStatus status,

        String subject,

        String description,

        BigDecimal requestedAmount,

        String currency,

        DisputeResolution resolution,

        BigDecimal resolvedAmount,

        String resolutionNotes,

        String assignedToName,

        LocalDateTime assignedAt,

        LocalDateTime resolvedAt,

        LocalDateTime createdAt,

        LocalDateTime updatedAt,

        boolean canWithdraw,

        boolean canAddEvidence,

        List<DisputeEvidenceResponse> evidence,

        List<DisputeTimelineResponse> timeline
) {
    public static DisputeResponse from(Dispute d) {
        return of(d, List.of(), List.of());
    }

    /**
     * Detail view. The lists are only populated when the caller
     * asked for the full dispute, because loading evidence and
     * timeline for a list of forty claims is four queries per row
     * that the queue view never displays.
     */
    public static DisputeResponse of(
            Dispute d,
            List<DisputeEvidenceResponse> evidence,
            List<DisputeTimelineResponse> timeline
    ) {
        String currency = d.getBooking() == null
                ? null
                : d.getBooking().getCurrency();

        return new DisputeResponse(
                d.getDisputeId(),
                d.getBooking() == null
                        ? null
                        : d.getBooking().getBookingId(),
                d.getBooking() == null
                        ? null
                        : d.getBooking().getBookingReference(),
                null,
                d.getRaisedBy() == null
                        ? null
                        : d.getRaisedBy().getUserId(),
                d.getRaisedBy() == null
                        ? null
                        : d.getRaisedBy().getFullName(),
                d.getPartner() == null
                        ? null
                        : d.getPartner().getFullName(),
                d.getCategory(),
                d.getStatus(),
                d.getSubject(),
                d.getDescription(),
                d.getRequestedAmount(),
                currency,
                d.getResolution(),
                d.getResolvedAmount(),
                d.getResolutionNotes(),
                d.getAssignedTo() == null
                        ? null
                        : d.getAssignedTo().getFullName(),
                d.getAssignedAt(),
                d.getResolvedAt(),
                d.getCreatedAt(),
                d.getUpdatedAt(),
                d.getStatus().isWithdrawableByClaimant(),
                d.isOpen(),
                evidence,
                timeline
        );
    }
}