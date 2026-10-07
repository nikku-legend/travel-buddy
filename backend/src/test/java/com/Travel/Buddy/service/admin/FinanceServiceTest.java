package com.Travel.Buddy.service.admin;

import com.Travel.Buddy.dto.admin.AdminRefundDecisionRequest;
import com.Travel.Buddy.dto.admin.AdminRefundResponse;
import com.Travel.Buddy.entity.BookingCancellation;
import com.Travel.Buddy.entity.CancellationReason;
import com.Travel.Buddy.entity.RefundStatus;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.BookingCancellationRepository;
import com.Travel.Buddy.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceServiceTest {

    private final BookingRepository bookingRepository =
            mock(BookingRepository.class);
    private final BookingCancellationRepository
            cancellationRepository =
            mock(BookingCancellationRepository.class);
    private final AdminAuditService auditService =
            mock(AdminAuditService.class);

    private FinanceService financeService;
    private BookingCancellation cancellation;

    @BeforeEach
    void setUp() {
        financeService = new FinanceService(
                bookingRepository,
                cancellationRepository,
                auditService
        );
        cancellation = new BookingCancellation();
        cancellation.setCancellationId(17L);
        cancellation.setCancellationReason(
                CancellationReason.TRIP_CANCELLED
        );
        cancellation.setRefundAmount(new BigDecimal("125.00"));
        cancellation.setRefundCurrency("INR");
        cancellation.setRefundStatus(RefundStatus.PENDING);
        when(cancellationRepository.findById(17L))
                .thenReturn(Optional.of(cancellation));
    }

    @Test
    void approvalDoesNotClaimRefundWasDisbursed() {
        AdminRefundResponse response = financeService.decideRefund(
                9L,
                17L,
                new AdminRefundDecisionRequest(true, null)
        );

        assertEquals(RefundStatus.APPROVED, response.refundStatus());
        assertEquals(RefundStatus.APPROVED, cancellation.getRefundStatus());
        assertNull(cancellation.getRefundProcessedAt());
        assertNull(cancellation.getRefundReference());
        verify(auditService).record(
                eq(9L),
                eq("REFUND_APPROVED"),
                eq("BookingCancellation"),
                eq(17L),
                eq("Refund of 125.00 INR authorized; "
                        + "disbursement has not been confirmed")
        );
    }

    @Test
    void refundDeskKeepsApprovedButUnpaidRefundsVisible() {
        when(cancellationRepository
                .findByRefundStatusInOrderByCreatedAtDesc(anyList()))
                .thenReturn(List.of(cancellation));
        cancellation.setRefundStatus(RefundStatus.APPROVED);

        List<AdminRefundResponse> refunds =
                financeService.pendingRefunds();

        assertEquals(1, refunds.size());
        assertEquals(RefundStatus.APPROVED, refunds.get(0).refundStatus());
        verify(cancellationRepository)
                .findByRefundStatusInOrderByCreatedAtDesc(
                        List.of(
                                RefundStatus.PENDING,
                                RefundStatus.APPROVED
                        )
                );
    }

    @Test
    void approvalCannotBeRecordedTwiceAsASecondDisbursement() {
        cancellation.setRefundStatus(RefundStatus.APPROVED);

        assertThrows(
                PartnerApplicationException.class,
                () -> financeService.decideRefund(
                        9L,
                        17L,
                        new AdminRefundDecisionRequest(true, null)
                )
        );
    }
}
