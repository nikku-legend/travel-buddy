package com.Travel.Buddy.service.dispute;

import com.Travel.Buddy.dto.dispute.ChangeDisputeStatusRequest;
import com.Travel.Buddy.dto.dispute.DisputeEvidenceResponse;
import com.Travel.Buddy.dto.dispute.DisputeResponse;
import com.Travel.Buddy.dto.dispute.DisputePageResponse;
import com.Travel.Buddy.dto.dispute.DisputeQueueResponse;
import com.Travel.Buddy.dto.dispute.DisputeSummaryResponse;
import com.Travel.Buddy.dto.dispute.DisputeTimelineResponse;
import com.Travel.Buddy.dto.dispute.RaiseDisputeRequest;
import com.Travel.Buddy.dto.dispute.ResolveDisputeRequest;
import com.Travel.Buddy.entity.Booking;
import com.Travel.Buddy.entity.Dispute;
import com.Travel.Buddy.entity.DisputeEventType;
import com.Travel.Buddy.entity.DisputeResolution;
import com.Travel.Buddy.entity.DisputeStatus;
import com.Travel.Buddy.entity.DisputeTimelineEntry;
import com.Travel.Buddy.entity.HotelReservation;
import com.Travel.Buddy.entity.PaymentStatus;
import com.Travel.Buddy.entity.Property;
import com.Travel.Buddy.entity.RoomType;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.BookingRepository;
import com.Travel.Buddy.repository.DisputeEvidenceRepository;
import com.Travel.Buddy.repository.DisputeRepository;
import com.Travel.Buddy.repository.DisputeTimelineRepository;
import com.Travel.Buddy.repository.HotelReservationRepository;
import com.Travel.Buddy.repository.RoomTypeRepository;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.notification.NotificationEvents;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dispute lifecycle. (FR-28)
 *
 * <pre>
 *   raise -> OPEN -> UNDER_REVIEW -> RESOLVED
 *                  |           |  -> REJECTED
 *                  |           -> AWAITING_PARTNER_RESPONSE -> UNDER_REVIEW
 *                  |-> WITHDRAWN (claimant only, while OPEN)
 * </pre>
 *
 * <p>Four rules this class exists to enforce, each of which is
 * money or trust:
 *
 * <ol>
 *   <li><strong>Only the claimant can raise, and only against a
 *       booking they actually paid for.</strong> The booking is
 *       never taken from the request body.</li>
 *   <li><strong>One open dispute per booking.</strong> A second
 *       claim is refused until the first is closed, so a partner
 *       cannot be buried under duplicates of the same complaint.</li>
 *   <li><strong>A claim must be made in time.</strong> The window
 *       runs from checkout, because that is the point at which
 *       the traveller can first know the trip did not match what
 *       was sold.</li>
 *   <li><strong>A ruling can never pay out more than was
 *       claimed or charged.</strong> Enforced here and again by a
 *       CHECK constraint.</li>
 * </ol>
 */
@Service
public class DisputeService {

    /**
     * Statuses that count as "still live" for the duplicate check.
     * A closed dispute is history and does not block a new claim.
     */
    private static final List<DisputeStatus> LIVE_STATUSES =
            List.of(
                    DisputeStatus.OPEN,
                    DisputeStatus.UNDER_REVIEW,
                    DisputeStatus.AWAITING_PARTNER_RESPONSE
            );

    private static final int MAX_PAGE_SIZE = 50;

    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceRepository evidenceRepository;
    private final DisputeTimelineRepository timelineRepository;
    private final BookingRepository bookingRepository;
    private final HotelReservationRepository reservationRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final UserRepository userRepository;
    private final NotificationEvents notificationEvents;
    private final int claimWindowDays;

    public DisputeService(
            DisputeRepository disputeRepository,
            DisputeEvidenceRepository evidenceRepository,
            DisputeTimelineRepository timelineRepository,
            BookingRepository bookingRepository,
            HotelReservationRepository reservationRepository,
            RoomTypeRepository roomTypeRepository,
            UserRepository userRepository,
            NotificationEvents notificationEvents,
            @Value("${app.dispute.claim-window-days:30}")
            int claimWindowDays
    ) {
        this.disputeRepository = disputeRepository;
        this.evidenceRepository = evidenceRepository;
        this.timelineRepository = timelineRepository;
        this.bookingRepository = bookingRepository;
        this.reservationRepository = reservationRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.userRepository = userRepository;
        this.notificationEvents = notificationEvents;
        this.claimWindowDays = claimWindowDays;
    }

    /* ============================================================
     * RAISING
     * ============================================================ */

    @Transactional
    public DisputeResponse raise(
            Long userId,
            Long bookingId,
            RaiseDisputeRequest request
    ) {
        User claimant = requireUser(userId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Booking not found"
                        )
                );

        requireOwnership(booking, userId);

        /*
         * Money must have actually changed hands. A dispute about
         * an unpaid or refunded booking is a support request, not
         * a claim for money back.
         */
        if (booking.getPaymentStatus() != PaymentStatus.PAID) {
            throw PartnerApplicationException.badRequest(
                    "Only a paid booking can be disputed. This "
                            + "booking is "
                            + booking.getPaymentStatus() + "."
            );
        }

        if (booking.getBookingStatus() == null) {
            throw PartnerApplicationException.badRequest(
                    "This booking cannot be disputed yet"
            );
        }

        /*
         * The claim window runs from checkout. Before it, the
         * traveller has not yet stayed, so there is nothing to
         * complain about; long after it, recollection and evidence
         * are both too weak to rule on fairly.
         */
        LocalDate checkout = checkoutDateOf(booking);
        if (checkout == null) {
            throw PartnerApplicationException.badRequest(
                    "This booking has no stay dates and cannot be disputed"
            );
        }

        LocalDate deadline = checkout.plusDays(claimWindowDays);
        if (LocalDate.now().isAfter(deadline)) {
            throw PartnerApplicationException.badRequest(
                    "The " + claimWindowDays + "-day dispute window "
                            + "for this stay closed on " + deadline
            );
        }

        /*
         * The window runs from checkout, so a stay that has not
         * ended yet is outside it in the other direction. Without
         * this a traveller could open a dispute about a trip that
         * has not happened, and an admin would be asked to rule on
         * a complaint about the future.
         */
        if (checkout.isAfter(LocalDate.now())) {
            throw PartnerApplicationException.badRequest(
                    "This stay has not finished yet. You can "
                            + "dispute it from " + checkout + "."
            );
        }

        /*
         * Re-checked here, not only by @DecimalMin on the request.
         * The service is a public entry point in its own right, and
         * a negative claim would otherwise reach the database.
         */
        if (request.requestedAmount() == null
                || request.requestedAmount().signum() < 0) {
            throw PartnerApplicationException.badRequest(
                    "A claim cannot ask for a negative amount"
            );
        }

        if (disputeRepository
                .findLiveByBookingId(
                        bookingId, LIVE_STATUSES
                ).isPresent()) {
            throw PartnerApplicationException.conflict(
                    "You already have an open dispute on this booking. "
                            + "Wait for it to be resolved before raising another."
            );
        }

        /*
         * A claim may not exceed what was charged. Without this a
         * traveller could "dispute" a 2,000 room for 50,000 and
         * the admin queue would be flooded with impossible asks.
         */
        BigDecimal paid = booking.getTotalAmount();
        if (paid == null) {
            paid = BigDecimal.ZERO;
        }

        if (request.requestedAmount().compareTo(paid) > 0) {
            throw PartnerApplicationException.badRequest(
                    "You cannot claim more than the "
                            + paid + " " + booking.getCurrency()
                            + " you paid"
            );
        }

        Dispute dispute = new Dispute(
                booking,
                claimant,
                partnerOf(booking),
                request.category(),
                request.subject().trim(),
                request.description().trim(),
                request.requestedAmount()
        );

        Dispute saved = disputeRepository.save(dispute);

        /*
         * The first entry in the trail. Every later action reads
         * from_status off this, so a dispute with no RAISED entry
         * would have a broken history from the very first read.
         */
        record(
                saved,
                DisputeEventType.RAISED,
                claimant,
                null,
                DisputeStatus.OPEN,
                request.subject().trim()
        );

        return DisputeResponse.from(saved);
    }

    /* ============================================================
     * CLAIMANT ACTIONS
     * ============================================================ */

    @Transactional
    public DisputeResponse withdraw(Long userId, Long disputeId) {
        Dispute dispute = requireClaimantDispute(userId, disputeId);

        if (!dispute.getStatus()
                .isWithdrawableByClaimant()) {
            throw PartnerApplicationException.conflict(
                    "This dispute is " + dispute.getStatus()
                            + " and can no longer be withdrawn"
            );
        }

        DisputeStatus from = dispute.getStatus();
        dispute.withdraw();

        record(
                dispute,
                DisputeEventType.STATUS_CHANGED,
                dispute.getRaisedBy(),
                from,
                DisputeStatus.WITHDRAWN,
                "Withdrawn by the claimant"
        );

        return DisputeResponse.from(disputeRepository.save(dispute));
    }

    /* ============================================================
     * ADMIN ACTIONS
     * ============================================================ */

    @Transactional
    public DisputeResponse claim(
            Long adminId,
            Long disputeId,
            String note
    ) {
        Dispute dispute = locked(disputeId);

        if (dispute.getStatus().isTerminal()) {
            throw PartnerApplicationException.conflict(
                    "This dispute is " + dispute.getStatus()
                            + " and cannot be worked"
            );
        }

        if (dispute.getAssignedTo() != null
                && !dispute.getAssignedTo()
                        .getUserId()
                        .equals(adminId)) {
            throw PartnerApplicationException.conflict(
                    "This dispute is already assigned to "
                            + dispute.getAssignedTo().getFullName()
            );
        }

        User admin = requireUser(adminId);

        DisputeStatus from = dispute.getStatus();
        dispute.assignTo(admin);

        if (from == DisputeStatus.OPEN) {
            dispute.changeStatus(DisputeStatus.UNDER_REVIEW);
        }

        record(
                dispute,
                DisputeEventType.ASSIGNED,
                admin,
                from,
                dispute.getStatus(),
                note
        );

        return DisputeResponse.from(disputeRepository.save(dispute));
    }

    @Transactional
    public DisputeResponse changeStatus(
            Long adminId,
            Long disputeId,
            ChangeDisputeStatusRequest request
    ) {
        Dispute dispute = locked(disputeId);
        User admin = requireUser(adminId);

        DisputeStatus from = dispute.getStatus();
        DisputeStatus to = request.status();

        if (from.isTerminal()) {
            throw PartnerApplicationException.conflict(
                    "This dispute is " + from
                            + " and can no longer change"
            );
        }

        /*
         * Refuses nonsense transitions such as reopening a closed
         * dispute or jumping straight from OPEN to a state that
         * presupposes someone has picked it up.
         */
        requireReachableTransition(from, to);

        dispute.changeStatus(to);

        record(
                dispute,
                DisputeEventType.STATUS_CHANGED,
                admin,
                from,
                to,
                request.note()
        );

        return DisputeResponse.from(disputeRepository.save(dispute));
    }

    /**
     * Rules on a dispute and applies the money.
     *
     * <p>Refund handling is deliberately coarse. This marks the
     * booking as refunded and records the ruling; the actual
     * disbursement and the partner's share of it belong to the
     * settlement work in FR-35, which is where the ledger that
     * pays partners out will live.
     */
    @Transactional
    public DisputeResponse resolve(
            Long adminId,
            Long disputeId,
            ResolveDisputeRequest request
    ) {
        Dispute dispute = locked(disputeId);
        User admin = requireUser(adminId);

        if (dispute.getStatus().isTerminal()) {
            throw PartnerApplicationException.conflict(
                    "This dispute is already " + dispute.getStatus()
            );
        }

        String notes = request.notes() == null
                ? null
                : request.notes().trim();

        DisputeStatus from = dispute.getStatus();

        if (request.isRejection()) {
            if (notes == null || notes.isBlank()) {
                throw new IllegalArgumentException(
                        "A reason is required when rejecting a dispute"
                );
            }

            dispute.reject(notes);

            record(
                    dispute,
                    DisputeEventType.RESOLVED,
                    admin,
                    from,
                    DisputeStatus.REJECTED,
                    notes
            );

            Dispute saved = disputeRepository.save(dispute);
            notifyClaimant(saved, "Your dispute was not upheld");
            return DisputeResponse.from(saved);
        }

        DisputeResolution resolution = request.resolution();
        if (resolution == null) {
            throw new IllegalArgumentException(
                    "A decision is required to resolve a dispute"
            );
        }

        if (resolution.involvesRefund()
                && (notes == null || notes.isBlank())) {
            throw new IllegalArgumentException(
                    "A reason is required when awarding a refund"
            );
        }

        BigDecimal amount = request.resolvedAmount();

        if (resolution == DisputeResolution.NO_REFUND) {
            amount = BigDecimal.ZERO;
        } else if (amount == null) {
            throw new IllegalArgumentException(
                    "An amount is required for a "
                            + resolution + " decision"
            );
        }

        /*
         * Ceilings, checked in order so the message names the
         * actual breach rather than a generic "invalid amount".
         */
        if (amount.compareTo(dispute.getRequestedAmount()) > 0) {
            throw PartnerApplicationException.badRequest(
                    "You cannot award more than the claimed amount of "
                            + dispute.getRequestedAmount()
            );
        }

        Booking booking = dispute.getBooking();
        BigDecimal paid = booking.getTotalAmount() == null
                ? BigDecimal.ZERO
                : booking.getTotalAmount();

        if (amount.compareTo(paid) > 0) {
            throw PartnerApplicationException.badRequest(
                    "You cannot award more than the "
                            + paid + " that was paid"
            );
        }

        dispute.resolve(resolution, amount, notes);

        if (resolution.involvesRefund()
                && booking.getPaymentStatus() == PaymentStatus.PAID) {
            booking.setPaymentStatus(PaymentStatus.REFUNDED);
        }

        record(
                dispute,
                DisputeEventType.RESOLVED,
                admin,
                from,
                DisputeStatus.RESOLVED,
                resolution + (amount.signum() > 0
                        ? " of " + amount + " " + booking.getCurrency()
                        : "")
                        + (notes == null || notes.isBlank()
                        ? ""
                        : " - " + notes)
        );

        Dispute saved = disputeRepository.save(dispute);

        notifyClaimant(
                saved,
                resolution.involvesRefund()
                        ? "Your dispute was resolved"
                        : "Your dispute was resolved"
        );

        return DisputeResponse.from(saved);
    }

    /* ============================================================
     * READS
     * ============================================================ */

    @Transactional(readOnly = true)
    public DisputePageResponse myDisputes(
            Long userId,
            int page,
            int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        var result = disputeRepository
                .findByRaisedBy_UserIdOrderByCreatedAtDesc(
                        userId,
                        PageRequest.of(safePage, safeSize)
                );

        return DisputePageResponse.of(
                result.getContent()
                        .stream()
                        .map(DisputeResponse::from)
                        .toList(),
                safePage,
                safeSize,
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    /**
     * The partner's view. A partner sees disputes raised against
     * their own properties, and never the claimant's full
     * description, because a claimant is entitled to describe the
     * problem in their own words.
     */
    @Transactional(readOnly = true)
    public DisputeQueueResponse disputesAgainst(
            Long partnerId,
            int page,
            int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        var result = disputeRepository
                .findByPartner_UserIdOrderByCreatedAtDesc(
                        partnerId,
                        PageRequest.of(safePage, safeSize)
                );

        return new DisputeQueueResponse(
                DisputeSummaryResponse.from(
                        result.getContent()
                ),
                safePage,
                safeSize,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getTotalElements(),
                BigDecimal.ZERO
        );
    }

    @Transactional(readOnly = true)
    public DisputeQueueResponse queue(
            int page,
            int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        var result = disputeRepository
                .findByStatusInOrderByCreatedAtAsc(
                        LIVE_STATUSES,
                        PageRequest.of(safePage, safeSize)
                );

        return new DisputeQueueResponse(
                DisputeSummaryResponse.from(
                        result.getContent()
                ),
                safePage,
                safeSize,
                result.getTotalElements(),
                result.getTotalPages(),
                disputeRepository.countByStatusIn(LIVE_STATUSES),
                disputeRepository.sumResolvedAmounts()
        );
    }

    /**
     * Full detail including evidence and the audit trail.
     *
     * <p>Visible to the claimant, the partner the complaint is
     * against, and admins. A partner who cannot see the claim
     * against their property has no way to answer it, which is
     * why AWAITING_PARTNER_RESPONSE exists.
     */
    @Transactional(readOnly = true)
    public DisputeResponse get(
            Long userId,
            boolean isAdmin,
            Long disputeId
    ) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Dispute not found"
                        )
                );

        if (!isAdmin
                && !isParty(dispute, userId)) {
            throw PartnerApplicationException.forbidden(
                    "You cannot view this dispute"
            );
        }

        return toDetail(dispute);
    }

    /* ============================================================
     * HELPERS
     * ============================================================ */

    /**
     * Records an entry on the immutable trail.
     *
     * <p>Every status change in this service funnels through
     * here, which is what makes the trail complete. There is no
     * code path that moves a dispute without leaving a record.
     */
    private void record(
            Dispute dispute,
            DisputeEventType type,
            User actor,
            DisputeStatus from,
            DisputeStatus to,
            String note
    ) {
        timelineRepository.save(
                new DisputeTimelineEntry(
                        dispute, type, actor, from, to, note
                )
        );
    }

    private DisputeResponse toDetail(Dispute dispute) {
        List<DisputeEvidenceResponse> evidence =
                evidenceRepository
                        .findByDispute_DisputeIdOrderByCreatedAtAsc(
                                dispute.getDisputeId()
                        )
                        .stream()
                        .map(DisputeEvidenceResponse::from)
                        .toList();

        List<DisputeTimelineResponse> timeline =
                timelineRepository
                        .findByDispute_DisputeIdOrderByCreatedAtAsc(
                                dispute.getDisputeId()
                        )
                        .stream()
                        .map(DisputeTimelineResponse::from)
                        .toList();

        return DisputeResponse.of(dispute, evidence, timeline);
    }

    private boolean isParty(Dispute dispute, Long userId) {
        Long claimantId = dispute.getRaisedBy() == null
                ? null
                : dispute.getRaisedBy().getUserId();
        Long partnerId = dispute.getPartner() == null
                ? null
                : dispute.getPartner().getUserId();

        return userId.equals(claimantId) || userId.equals(partnerId);
    }

    private Dispute requireClaimantDispute(
            Long userId,
            Long disputeId
    ) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Dispute not found"
                        )
                );

        if (dispute.getRaisedBy() == null
                || !dispute.getRaisedBy()
                        .getUserId()
                        .equals(userId)) {
            throw PartnerApplicationException.forbidden(
                    "This is not your dispute"
            );
        }

        return dispute;
    }

    private Dispute locked(Long disputeId) {
        return disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Dispute not found"
                        )
                );
    }

    private void requireOwnership(
            Booking booking,
            Long userId
    ) {
        if (booking.getUser() == null
                || !booking.getUser()
                        .getUserId()
                        .equals(userId)) {
            throw PartnerApplicationException.forbidden(
                    "You cannot dispute a booking that is not yours"
            );
        }
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "User not found"
                        )
                );
    }

    private LocalDate checkoutDateOf(Booking booking) {
        return reservationRepository
                .findByBooking_BookingId(booking.getBookingId())
                .map(HotelReservation::getCheckOut)
                .orElse(null);
    }

    /**
     * Walks the reservation to the property owner so a partner
     * can be told about the claim without a four-table join.
     */
    private User partnerOf(Booking booking) {
        return reservationRepository
                .findByBooking_BookingId(booking.getBookingId())
                .map(reservation ->
                        roomTypeRepository
                                .findById(
                                        reservation.getRoomType()
                                                .getRoomTypeId()
                                )
                                .map(RoomType::getProperty)
                                .map(Property::getPartner)
                                .orElse(null)
                )
                .orElse(null);
    }

    /**
     * Blocks transitions that would put the dispute into a state
     * that does not make sense or that quietly rewrites history.
     */
    private void requireReachableTransition(
            DisputeStatus from,
            DisputeStatus to
    ) {
        if (to == null) {
            throw new IllegalArgumentException(
                    "A status is required"
            );
        }

        if (to.isTerminal()) {
            throw PartnerApplicationException.badRequest(
                    "Use the resolve action to close a dispute, "
                            + "so a decision and its amount are recorded"
            );
        }

        Set<DisputeStatus> allowed = switch (from) {
            case OPEN -> Set.of(DisputeStatus.UNDER_REVIEW);
            case UNDER_REVIEW -> Set.of(
                    DisputeStatus.AWAITING_PARTNER_RESPONSE,
                    DisputeStatus.OPEN
            );
            case AWAITING_PARTNER_RESPONSE ->
                    Set.of(DisputeStatus.UNDER_REVIEW);
            default -> Set.of();
        };

        if (!allowed.contains(to)) {
            throw PartnerApplicationException.badRequest(
                    "A dispute that is " + from
                            + " cannot move to " + to
            );
        }
    }

    private void notifyClaimant(
            Dispute dispute,
            String title
    ) {
        if (dispute.getRaisedBy() == null) {
            return;
        }

        notificationEvents.disputeRuled(
                dispute,
                dispute.getRaisedBy(),
                title
        );
    }

    /**
     * Exposed for the admin dashboard.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        return Map.of(
                "openDisputes",
                disputeRepository.countByStatusIn(LIVE_STATUSES),
                "totalRefunded",
                disputeRepository.sumResolvedAmounts()
        );
    }
}