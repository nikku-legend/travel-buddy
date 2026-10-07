package com.Travel.Buddy.service.admin;

import com.Travel.Buddy.dto.admin.AdminLedgerResponse;
import com.Travel.Buddy.dto.admin.AdminRefundDecisionRequest;
import com.Travel.Buddy.dto.admin.AdminRefundResponse;
import com.Travel.Buddy.entity.Booking;
import com.Travel.Buddy.entity.BookingCancellation;
import com.Travel.Buddy.entity.PaymentStatus;
import com.Travel.Buddy.entity.RefundStatus;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.BookingCancellationRepository;
import com.Travel.Buddy.repository.BookingRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The financial desk. (FR-33)
 *
 * <p>Ledger inspection and refund authorization over what the
 * system actually records: bookings carry the money, and
 * cancellations carry the money owed back. The desk never
 * invents a number the booking system cannot reproduce.
 */
@Service
public class FinanceService {

    private static final int PAGE_SIZE = 25;

    private final BookingRepository bookingRepository;
    private final BookingCancellationRepository cancellationRepository;
    private final AdminAuditService auditService;

    public FinanceService(
            BookingRepository bookingRepository,
            BookingCancellationRepository cancellationRepository,
            AdminAuditService auditService
    ) {
        this.bookingRepository = bookingRepository;
        this.cancellationRepository = cancellationRepository;
        this.auditService = auditService;
    }

    /**
     * Paged ledger, newest first. A null payment status is the
     * whole book; a status filters it, and the totals line up
     * with what the filter shows.
     */
    @Transactional(readOnly = true)
    public AdminLedgerResponse ledger(
            PaymentStatus paymentStatus,
            int page,
            int size
    ) {
        int pageNumber = Math.max(page, 0);
        int pageSize = size <= 0 ? PAGE_SIZE : Math.min(size, 100);

        PageRequest pageable = PageRequest.of(pageNumber, pageSize);

        List<Booking> bookings =
                paymentStatus == null
                        ? bookingRepository
                                .findAllByOrderByCreatedAtDesc(pageable)
                        : bookingRepository
                                .findAllByPaymentStatusOrderByCreatedAtDesc(
                                        paymentStatus, pageable
                                );

        long total = paymentStatus == null
                ? bookingRepository.count()
                : bookingRepository.countByPaymentStatus(paymentStatus);

        List<AdminLedgerResponse.Row> rows = bookings.stream()
                .map(booking ->
                        new AdminLedgerResponse.Row(
                                booking.getBookingId(),
                                booking.getBookingReference(),
                                booking.getGuestName(),
                                booking.getBookingType(),
                                booking.getTotalAmount(),
                                booking.getCurrency(),
                                booking.getPaymentStatus(),
                                booking.getBookingStatus(),
                                booking.getCreatedAt()
                        ))
                .toList();

        return new AdminLedgerResponse(
                pageNumber,
                pageSize,
                total,
                (int) Math.ceil(total / (double) pageSize),
                rows
        );
    }

    /**
     * Cancellations whose refund is still owed, newest first.
     */
    @Transactional(readOnly = true)
    public List<AdminRefundResponse> pendingRefunds() {
        return cancellationRepository
                .findByRefundStatusInOrderByCreatedAtDesc(
                        List.of(
                                RefundStatus.PENDING,
                                RefundStatus.APPROVED
                        )
                )
                .stream()
                .map(this::toRefundResponse)
                .toList();
    }

    /**
     * Approving authorizes a refund but does not claim the money
     * was disbursed. A provider integration must move it to
     * COMPLETED and set the processed timestamp after confirmation.
     *
     * <p>Only PENDING or legacy PROCESSING refunds can be decided;
     * terminal decisions cannot be overwritten.
     */
    @Transactional
    public AdminRefundResponse decideRefund(
            Long actorUserId,
            Long cancellationId,
            AdminRefundDecisionRequest request
    ) {
        BookingCancellation cancellation = cancellationRepository
                .findById(cancellationId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Cancellation not found"
                        )
                );

        RefundStatus current = cancellation.getRefundStatus();

        if (current != RefundStatus.PENDING
                && current != RefundStatus.PROCESSING) {
            throw PartnerApplicationException.conflict(
                    "Only a pending refund can be decided; this "
                            + "one is " + current
            );
        }

        if (request.approve()) {
            cancellation.setRefundStatus(RefundStatus.APPROVED);

            auditService.record(
                    actorUserId,
                    "REFUND_APPROVED",
                    "BookingCancellation",
                    cancellationId,
                    "Refund of " + cancellation.getRefundAmount()
                            + " "
                            + cancellation.getRefundCurrency()
                            + " authorized; disbursement has not "
                            + "been confirmed"
            );
        } else {
            if (request.reason() == null
                    || request.reason().isBlank()) {
                throw PartnerApplicationException.badRequest(
                        "A reason is required to reject a refund"
                );
            }
            cancellation.setRefundStatus(RefundStatus.FAILED);
            cancellation.setRefundStatus(RefundStatus.FAILED);

            auditService.record(
                    actorUserId,
                    "REFUND_REJECTED",
                    "BookingCancellation",
                    cancellationId,
                    request.reason()
            );
        }

        return toRefundResponse(cancellation);
    }

    private AdminRefundResponse toRefundResponse(
            BookingCancellation cancellation
    ) {
        return new AdminRefundResponse(
                cancellation.getCancellationId(),
                cancellation.getBooking() == null
                        ? null
                        : cancellation.getBooking().getBookingId(),
                cancellation.getBooking() == null
                        ? null
                        : cancellation.getBooking()
                                .getBookingReference(),
                cancellation.getBooking() == null
                        ? null
                        : cancellation.getBooking().getGuestName(),
                cancellation.getCancellationReason(),
                cancellation.getRefundAmount(),
                cancellation.getRefundCurrency(),
                cancellation.getRefundStatus(),
                cancellation.getCancelledAt()
        );
    }
}