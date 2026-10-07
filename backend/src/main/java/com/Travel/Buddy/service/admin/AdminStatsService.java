package com.Travel.Buddy.service.admin;

import com.Travel.Buddy.dto.admin.AdminStatsResponse;
import com.Travel.Buddy.entity.BookingStatus;
import com.Travel.Buddy.entity.PaymentStatus;
import com.Travel.Buddy.entity.RefundStatus;
import com.Travel.Buddy.entity.UserStatus;
import com.Travel.Buddy.repository.BookingCancellationRepository;
import com.Travel.Buddy.repository.BookingRepository;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.dispute.DisputeService;
import com.Travel.Buddy.service.partner.PartnerApplicationService;
import com.Travel.Buddy.service.property.PropertyApprovalService;
import com.Travel.Buddy.service.review.ReviewService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * The Admin Command Center's numbers. (FR-30)
 *
 * <p>Every figure comes from the same tables and queue methods
 * the tabs themselves read, so the overview cannot disagree with
 * the queue below it: pending partner applications is literally
 * {@code reviewQueue().size()}, not a parallel count that drifts.
 */
@Service
public class AdminStatsService {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final BookingCancellationRepository cancellationRepository;
    private final PartnerApplicationService partnerApplicationService;
    private final PropertyApprovalService propertyApprovalService;
    private final ReviewService reviewService;
    private final DisputeService disputeService;

    public AdminStatsService(
            UserRepository userRepository,
            BookingRepository bookingRepository,
            BookingCancellationRepository cancellationRepository,
            PartnerApplicationService partnerApplicationService,
            PropertyApprovalService propertyApprovalService,
            ReviewService reviewService,
            DisputeService disputeService
    ) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.cancellationRepository = cancellationRepository;
        this.partnerApplicationService = partnerApplicationService;
        this.propertyApprovalService = propertyApprovalService;
        this.reviewService = reviewService;
        this.disputeService = disputeService;
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse overview() {

        long totalUsers = userRepository.count();
        long suspendedUsers =
                userRepository.countByStatus(UserStatus.SUSPENDED);

        BigDecimal gross = money(
                bookingRepository.sumTotalAmountByPaymentStatus(
                        PaymentStatus.PAID
                )
        );
        BigDecimal refunded = money(
                bookingRepository.sumTotalAmountByPaymentStatus(
                        PaymentStatus.REFUNDED
                )
        );

        /*
         * The same queue methods the tabs call. A summary count
         * computed any other way would eventually disagree with
         * the list it summarizes.
         */
        long pendingPartnerApplications =
                partnerApplicationService.reviewQueue().size();
        long pendingProperties =
                propertyApprovalService.approvalQueue().size();
        long pendingReviews =
                reviewService.moderationQueue().size();

        Map<String, Object> disputeStats = disputeService.stats();
        long openDisputes = number(
                disputeStats.get("openDisputes")
        );

        long refundsPending = cancellationRepository
                .countByRefundStatus(RefundStatus.PENDING);

        List<AdminStatsResponse.RecentSale> sales =
                bookingRepository
                        .findTop8ByOrderByCreatedAtDesc()
                        .stream()
                        .map(booking ->
                                new AdminStatsResponse.RecentSale(
                                        booking.getBookingId(),
                                        booking.getBookingReference(),
                                        booking.getGuestName(),
                                        booking.getBookingType() == null
                                                ? null
                                                : booking.getBookingType()
                                                        .name(),
                                        money(booking.getTotalAmount()),
                                        booking.getCurrency(),
                                        booking.getPaymentStatus() == null
                                                ? null
                                                : booking.getPaymentStatus()
                                                        .name(),
                                        booking.getBookingStatus() == null
                                                ? null
                                                : booking.getBookingStatus()
                                                        .name(),
                                        booking.getCreatedAt()
                                ))
                        .toList();

        return new AdminStatsResponse(
                totalUsers,
                totalUsers - suspendedUsers,
                suspendedUsers,
                bookingRepository.count(),
                bookingRepository.countByBookingStatus(
                        BookingStatus.CONFIRMED
                ),
                bookingRepository.countByBookingStatus(
                        BookingStatus.CANCELLED
                ),
                gross,
                refunded,
                new AdminStatsResponse.PendingApprovals(
                        pendingPartnerApplications,
                        pendingProperties,
                        pendingReviews
                ),
                openDisputes,
                refundsPending,
                sales
        );
    }

    /**
     * The SUM of no rows is null; the console must show 0.00,
     * never an empty cell that reads as "unknown".
     */
    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }

        return 0L;
    }
}