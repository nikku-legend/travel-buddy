package com.Travel.Buddy.service.voucher;

import com.Travel.Buddy.dto.voucher.VoucherResponse;
import com.Travel.Buddy.dto.voucher.VoucherVerificationResponse;
import com.Travel.Buddy.entity.*;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.*;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Digital vouchers and front-desk verification. (FR-24)
 *
 * <pre>
 *   Booking confirmed -> Voucher issued -> guest shows QR
 *                                        -> staff scans
 *                                        -> verified
 *                                        -> check-in CONSUMES it
 * </pre>
 *
 * <h2>Rules that matter</h2>
 *
 * <ol>
 *   <li><strong>Single use.</strong> Check-in consumes the voucher, so
 *       a replayed photo will not admit a second guest.</li>
 *   <li><strong>Nothing is revealed on failure.</strong> A bad scan
 *       returns a reason but no guest data, so scanning random codes
 *       is not a way to enumerate bookings.</li>
 *   <li><strong>Voided, never deleted.</strong> A cancelled booking
 *       still leaves an auditable record that a voucher existed.</li>
 * </ol>
 */
@Service
public class VoucherService {

    /**
     * Unambiguous alphabet: no 0/O, 1/I/L. The code is read aloud on
     * the phone in the fallback path, so look-alike characters would
     * cause real mistakes.
     */
    private static final char[] CODE_ALPHABET =
            "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private static final int CODE_LENGTH = 8;

    private final BookingVoucherRepository voucherRepository;

    private final BookingRepository bookingRepository;

    private final HotelReservationRepository reservationRepository;

    private final RoomStayRepository roomStayRepository;

    private final VoucherTokenService tokenService;

    private final SecureRandom secureRandom = new SecureRandom();

    public VoucherService(
            BookingVoucherRepository voucherRepository,
            BookingRepository bookingRepository,
            HotelReservationRepository reservationRepository,
            RoomStayRepository roomStayRepository,
            VoucherTokenService tokenService
    ) {
        this.voucherRepository = voucherRepository;
        this.bookingRepository = bookingRepository;
        this.reservationRepository = reservationRepository;
        this.roomStayRepository = roomStayRepository;
        this.tokenService = tokenService;
    }

    /* ============================================================
     * ISSUING
     * ============================================================ */

    /**
     * Issues the voucher for a confirmed booking, or returns the
     * existing one.
     *
     * <p>Idempotent on purpose: a guest opening their wallet twice
     * must not end up with two live vouchers for one booking.
     */
    @Transactional
    public VoucherResponse issueForBooking(
            Long bookingId,
            Long requesterId
    ) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Booking not found"
                        )
                );

        requireGuestOrOwner(booking, requesterId);

        if (booking.getBookingStatus() == BookingStatus.CANCELLED
                || booking.getBookingStatus() == BookingStatus.NO_SHOW) {

            throw PartnerApplicationException.conflict(
                    "A " + booking.getBookingStatus()
                            + " booking cannot have a voucher"
            );
        }

        BookingVoucher existing =
                voucherRepository.findByBooking_BookingId(bookingId)
                        .orElse(null);

        if (existing != null) {
            return toResponse(existing);
        }

        HotelReservation reservation =
                reservationRepository.findByBooking_BookingId(bookingId)
                        .orElseThrow(() ->
                                PartnerApplicationException.badRequest(
                                        "This booking has no room reservation to issue a voucher for"
                                )
                        );

        BookingVoucher voucher = new BookingVoucher();

        voucher.setBooking(booking);
        voucher.setVoucherCode(generateUniqueCode());
        voucher.setStatus(VoucherStatus.ACTIVE);
        voucher.setGuestName(booking.getGuestName());

        /*
         * Valid on the arrival day. The stay runs to the morning of
         * check-out, so the voucher is good for the check-out date too
         * when the guest needs to re-present it.
         */
        voucher.setValidFrom(reservation.getCheckIn());
        voucher.setValidUntil(reservation.getCheckOut());

        return toResponse(voucherRepository.save(voucher));
    }

    @Transactional(readOnly = true)
    public List<VoucherResponse> myVouchers(Long userId) {
        return voucherRepository.findByUser(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VoucherResponse getByCode(
            Long partnerId,
            String voucherCode
    ) {
        return toResponse(requireOwnedVoucher(partnerId, voucherCode));
    }

    @Transactional
    public VoucherResponse voidVoucher(
            Long partnerId,
            String voucherCode,
            String reason
    ) {
        BookingVoucher voucher =
                voucherRepository.findByCodeForUpdate(
                        voucherCode.toUpperCase(Locale.ROOT)
                ).orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Voucher not found"
                        )
                );

        requireOwnedVoucher(partnerId, voucher);

        if (voucher.getStatus() == VoucherStatus.USED) {

            throw PartnerApplicationException.conflict(
                    "This voucher has already been used for check-in"
            );
        }

        voucher.setStatus(VoucherStatus.VOID);
        voucher.setVoidedAt(LocalDateTime.now());
        voucher.setVoidReason(
                reason == null || reason.isBlank()
                        ? "Voided by the property"
                        : reason.trim()
        );

        return toResponse(voucherRepository.save(voucher));
    }
    /* ============================================================
     * VERIFICATION
     * ============================================================ */

    /**
     * Verifies a scanned QR or a keyed-in short code.
     *
     * <p>Never throws for a bad voucher: the front desk needs a
     * reason to show the guest, not a stack trace. Genuine
     * programming errors still surface.
     *
     * <p>Two input shapes are accepted, because a phone camera will
     * not always focus on a laminated or damaged voucher:
     * the signed QR payload, or the short manual code.
     */
    @Transactional
    public VoucherVerificationResponse verify(
            Long partnerId,
            String scanned
    ) {
        String code = scanned == null ? "" : scanned.trim();

        if (code.isEmpty()) {

            return VoucherVerificationResponse.rejected(
                    "No voucher code was supplied"
            );
        }

        BookingVoucher voucher;

        if (code.startsWith("TBV1.")) {

            VoucherTokenService.VerifiedToken token;

            try {

                token = tokenService.verify(code);

            } catch (VoucherTokenException exception) {

                return VoucherVerificationResponse.rejected(
                        exception.getMessage()
                );
            }

            voucher =
                    voucherRepository.findByVoucherCodeIgnoreCase(
                                    token.voucherCode()
                            )
                            .orElse(null);

            if (voucher == null) {

                return VoucherVerificationResponse.rejected(
                        "This voucher does not belong to Travel Buddy"
                );
            }

            /*
             * The signature proved the token is ours, but the voucher
             * may since have been reissued with a different code.
             * Confirm the two still agree.
             */
            if (!voucher.getBooking()
                    .getBookingId()
                    .equals(token.bookingId())) {

                return VoucherVerificationResponse.rejected(
                        "This voucher has been reissued and the scanned code is out of date"
                );
            }

        } else {

            voucher =
                    voucherRepository.findByCodeForUpdate(
                                    code.toUpperCase(Locale.ROOT)
                            )
                            .orElse(null);

            if (voucher == null) {

                return VoucherVerificationResponse.rejected(
                        "No voucher matches that code"
                );
            }
        }

        return evaluate(partnerId, voucher);
    }

    private VoucherVerificationResponse evaluate(
            Long partnerId,
            BookingVoucher voucher
    ) {
        if (!propertyOwnerOf(voucher.getBooking())
                .map(owner -> owner.equals(partnerId))
                .orElse(false)) {

            /*
             * Refuse, but do not confirm the code exists elsewhere.
             */
            return VoucherVerificationResponse.rejected(
                    "This voucher belongs to a different property"
            );
        }

        voucher.setVerificationCount(
                (voucher.getVerificationCount() == null
                        ? 0
                        : voucher.getVerificationCount()) + 1
        );
        voucher.setLastVerifiedAt(LocalDateTime.now());
        voucherRepository.save(voucher);

        if (voucher.getStatus() == VoucherStatus.VOID) {

            return VoucherVerificationResponse.rejected(
                    "This voucher was voided"
                            + (voucher.getVoidReason() == null
                            ? ""
                            : ": " + voucher.getVoidReason())
            );
        }

        if (voucher.getStatus() == VoucherStatus.USED) {

            return VoucherVerificationResponse.rejected(
                    "This voucher was already used on "
                            + voucher.getConsumedAt()
            );
        }

        Booking booking = voucher.getBooking();

        if (booking.getBookingStatus() == BookingStatus.CANCELLED) {

            markVoid(voucher, "The booking was cancelled");

            return VoucherVerificationResponse.rejected(
                    "This booking was cancelled"
            );
        }

        if (booking.getBookingStatus() == BookingStatus.COMPLETED) {

            return VoucherVerificationResponse.rejected(
                    "This stay has already ended"
            );
        }

        if (voucher.getStatus() == VoucherStatus.EXPIRED
                || LocalDate.now()
                .isAfter(voucher.getValidUntil())) {

            voucher.setStatus(VoucherStatus.EXPIRED);
            voucherRepository.save(voucher);

            return VoucherVerificationResponse.rejected(
                    "This voucher expired on "
                            + voucher.getValidUntil()
            );
        }

        if (booking.getPaymentStatus() != PaymentStatus.PAID) {

            return VoucherVerificationResponse.rejected(
                    "Payment for this booking has not completed"
            );
        }

        HotelReservation reservation =
                reservationRepository.findByBooking_BookingId(
                                booking.getBookingId()
                        )
                        .orElse(null);

        RoomStay stay =
                roomStayRepository.findByBooking_BookingIdAndStatusIn(
                                booking.getBookingId(),
                                List.of(RoomStayStatus.CHECKED_IN)
                        )
                        .stream()
                        .findFirst()
                        .orElse(null);

        VoucherResponse voucherView = toResponse(voucher);

        return new VoucherVerificationResponse(
                true,
                "VERIFIED",
                "Voucher is valid",
                voucherView,
                booking.getBookingReference(),
                reservation == null
                        ? null
                        : reservation.getRoomType().getProperty().getPropertyId(),
                reservation == null
                        ? null
                        : reservation.getRoomType().getProperty().getName(),
                reservation == null
                        ? null
                        : reservation.getRoomType()
                        .getCategoryName(),
                booking.getGuestName(),
                reservation == null
                        ? null
                        : reservation.getCheckIn(),
                reservation == null
                        ? null
                        : reservation.getCheckOut(),
                reservation == null
                        ? null
                        : reservation.getRoomsBooked(),
                reservation == null
                        ? null
                        : reservation.getAssignedRoomNumbers(),
                stay != null,
                stay == null
                        ? null
                        : stay.getStayId()
        );
    }
    /* ============================================================
     * CONSUMPTION
     * ============================================================ */

    /**
     * Consumes a voucher at check-in.
     *
     * <p>This is what makes the voucher single use. The row is
     * locked first, so two staff pressing the button at the same
     * moment cannot both succeed.
     */
    @Transactional
    public BookingVoucher consume(
            String voucherCode,
            RoomStay stay
    ) {
        BookingVoucher voucher =
                voucherRepository.findByCodeForUpdate(
                                voucherCode.toUpperCase(Locale.ROOT)
                        )
                        .orElseThrow(() ->
                                PartnerApplicationException.notFound(
                                        "Voucher not found"
                                )
                        );

        if (voucher.getStatus() == VoucherStatus.USED) {

            throw PartnerApplicationException.conflict(
                    "This voucher was already used on "
                            + voucher.getConsumedAt()
            );
        }

        if (voucher.getStatus() != VoucherStatus.ACTIVE) {

            throw PartnerApplicationException.conflict(
                    "This voucher is " + voucher.getStatus()
                            + " and cannot admit a guest"
            );
        }

        voucher.setStatus(VoucherStatus.USED);
        voucher.setConsumedAt(LocalDateTime.now());
        voucher.setConsumedByStay(stay);

        return voucherRepository.save(voucher);
    }

    /**
     * Consumes every active voucher for a booking once the guest is
     * in house. Called by the check-in flow so a multi-room booking
     * has no leftover live vouchers.
     */
    @Transactional
    public void consumeForStay(
            Long bookingId,
            RoomStay stay
    ) {
        BookingVoucher voucher =
                voucherRepository.findByBooking_BookingId(bookingId)
                        .orElse(null);

        if (voucher == null
                || voucher.getStatus() != VoucherStatus.ACTIVE) {

            return;
        }

        consume(voucher.getVoucherCode(), stay);
    }

    /**
     * Marks vouchers whose stay has passed as EXPIRED.
     *
     * <p>Run hourly so a stale voucher is never presented as valid
     * by the state check alone.
     */
    @Scheduled(fixedDelayString = "${app.voucher.expiry-sweep-ms:3600000}")
    @Transactional
    public void expireStaleVouchers() {

        List<BookingVoucher> stale =
                voucherRepository.findByStatusAndValidUntilBefore(
                        VoucherStatus.ACTIVE,
                        LocalDate.now()
                );

        for (BookingVoucher voucher : stale) {

            voucher.setStatus(VoucherStatus.EXPIRED);
            voucherRepository.save(voucher);
        }
    }

    /* ============================================================
     * HELPERS
     * ============================================================ */

    /**
     * The partner who owns this booking.
     *
     * <p>A booking links to a property only indirectly, through its
     * hotel reservation, so ownership is resolved there rather than
     * assumed. Returns empty when the booking has no reservation yet.
     */
    private java.util.Optional<Long> propertyOwnerOf(
            Booking booking
    ) {
        return reservationRepository.findByBooking_BookingId(
                        booking.getBookingId()
                )
                .map(reservation ->
                        reservation.getRoomType()
                                .getProperty()
                                .getPartner()
                                .getUserId()
                );
    }

    private void markVoid(
            BookingVoucher voucher,
            String reason
    ) {
        voucher.setStatus(VoucherStatus.VOID);
        voucher.setVoidedAt(LocalDateTime.now());
        voucher.setVoidReason(reason);

        voucherRepository.save(voucher);
    }

    private void requireGuestOrOwner(
            Booking booking,
            Long requesterId
    ) {
        boolean isGuest =
                booking.getUser()
                        .getUserId()
                        .equals(requesterId);

        boolean isOwner =
                propertyOwnerOf(booking)
                        .map(owner -> owner.equals(requesterId))
                        .orElse(false);

        if (!isGuest && !isOwner) {

            throw PartnerApplicationException.forbidden(
                    "This booking does not belong to you"
            );
        }
    }

    private BookingVoucher requireOwnedVoucher(
            Long partnerId,
            String voucherCode
    ) {
        return requireOwnedVoucher(
                partnerId,
                voucherRepository.findByVoucherCodeIgnoreCase(
                                voucherCode
                        )
                        .orElseThrow(() ->
                                PartnerApplicationException.notFound(
                                        "Voucher not found"
                                )
                        )
        );
    }

    private BookingVoucher requireOwnedVoucher(
            Long partnerId,
            BookingVoucher voucher
    ) {
        if (!propertyOwnerOf(voucher.getBooking())
                .map(owner -> owner.equals(partnerId))
                .orElse(false)) {

            throw PartnerApplicationException.forbidden(
                    "This voucher belongs to a different property"
            );
        }

        return voucher;
    }

    /**
     * Issues a short code that no other voucher is using.
     *
     * <p>The unique index is the real guarantee; this loop just
     * avoids burning a transaction on a predictable collision.
     */
    private String generateUniqueCode() {

        for (int attempt = 0; attempt < 12; attempt++) {

            String candidate = "TBV-" + randomCode();

            if (voucherRepository.findByVoucherCodeIgnoreCase(
                    candidate
            ).isEmpty()) {

                return candidate;
            }
        }

        throw new IllegalStateException(
                "Could not generate a unique voucher code"
        );
    }

    private String randomCode() {

        StringBuilder builder = new StringBuilder(CODE_LENGTH);

        for (int i = 0; i < CODE_LENGTH; i++) {

            builder.append(
                    CODE_ALPHABET[secureRandom
                            .nextInt(CODE_ALPHABET.length)]
            );
        }

        return builder.toString();
    }
    /* ============================================================
     * MAPPER
     * ============================================================ */

    private VoucherResponse toResponse(BookingVoucher voucher) {
        Booking booking = voucher.getBooking();

        LocalDate today = LocalDate.now();

        String unusable = null;

        if (!voucher.isUsableOn(today)) {

            if (voucher.getStatus() == VoucherStatus.VOID) {

                unusable = "This voucher was voided";

            } else if (voucher.getStatus() == VoucherStatus.USED) {

                unusable = "This voucher has already been used";

            } else if (voucher.getStatus() == VoucherStatus.EXPIRED) {

                unusable = "This voucher has expired";

            } else {

                unusable = today.isBefore(voucher.getValidFrom())
                        ? "This voucher is not valid until "
                        + voucher.getValidFrom()
                        : "This voucher expired on "
                        + voucher.getValidUntil();
            }
        }

        return new VoucherResponse(
                voucher.getVoucherId(),
                booking.getBookingId(),
                booking.getBookingReference(),
                voucher.getStatus(),
                voucher.getVoucherCode(),
                voucher.getGuestName(),
                voucher.getValidFrom(),
                voucher.getValidUntil(),
                voucher.getIssuedAt(),
                voucher.getConsumedAt(),
                voucher.getVoidedAt(),
                voucher.getVoidReason(),
                voucher.getVerificationCount(),

                /*
                 * The exact string a QR encoder should render. It
                 * carries no guest data, so the image is safe to
                 * screenshot or share.
                 */
                tokenService.issue(
                        booking.getBookingId(),
                        voucher.getVoucherCode(),
                        voucher.getValidUntil()
                ),

                unusable == null,
                unusable
        );
    }
}