package com.Travel.Buddy.service.voucher;

import com.Travel.Buddy.dto.property.PropertyUpsertRequest;
import com.Travel.Buddy.dto.room.RoomTypeUpsertRequest;
import com.Travel.Buddy.dto.voucher.VoucherResponse;
import com.Travel.Buddy.dto.voucher.VoucherVerificationResponse;
import com.Travel.Buddy.entity.*;
import com.Travel.Buddy.repository.*;
import com.Travel.Buddy.service.partner.RoleService;
import com.Travel.Buddy.service.property.PropertyApprovalService;
import com.Travel.Buddy.service.property.RoomTypeService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Digital vouchers and QR verification. (FR-24)
 *
 * <p>The two properties that matter are unforgeability and single use.
 * A voucher that can be forged admits strangers into rooms, and one
 * that can be replayed admits a second guest with a screenshot.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Digital vouchers and QR verification (FR-24)")
class VoucherServiceTest {

    @Autowired
    private VoucherService service;

    @Autowired
    private VoucherTokenService tokenService;

    @Autowired
    private PropertyApprovalService propertyService;

    @Autowired
    private RoomTypeService roomTypeService;

    @Autowired
    private BookingVoucherRepository voucherRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private HotelReservationRepository reservationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private StateRepository stateRepository;

    @Autowired
    private CountryRepository countryRepository;

    private User partner;

    private User guest;

    private Long bookingId;

    private Integer stateId;

    @BeforeEach
    void setUp() {
        User admin = createUser("vch-admin@test.travelbuddy");
        roleService.grantBaselineTravelerRole(admin);
        roleService.grant(admin, Role.ROLE_SUPER_ADMIN, null);

        partner = createUser("vch-partner@test.travelbuddy");
        roleService.grantBaselineTravelerRole(partner);
        roleService.grant(partner, Role.ROLE_HOTEL_PARTNER, admin);

        guest = createUser("vch-guest@test.travelbuddy");
        roleService.grantBaselineTravelerRole(guest);

        if (stateId == null) {
            Country country = new Country();
            country.setName("Testland");
            country.setIsoCode("TL");
            country = countryRepository.save(country);

            State state = new State();
            state.setName("Test State");
            state.setCountry(country);
            state.setRegionZone(RegionZone.EAST);
            state = stateRepository.save(state);

            stateId = state.getStateId();
        }

        Long propertyId = propertyService.create(
                partner,
                new PropertyUpsertRequest(
                        "Voucher Hotel",
                        PropertyType.HOTEL,
                        stateId,
                        "Address",
                        "Description",
                        new BigDecimal("19.8"),
                        new BigDecimal("85.7")
                )
        ).propertyId();

        Long roomTypeId = roomTypeService.create(
                partner.getUserId(),
                propertyId,
                new RoomTypeUpsertRequest(
                        "Deluxe", 2, new BigDecimal("2500.00"), "INR", 3
                )
        ).roomTypeId();

        RoomType roomType = roomTypeRepository
                .findById(roomTypeId)
                .orElseThrow();

        LocalDate checkIn = LocalDate.now();
        LocalDate checkOut = checkIn.plusDays(2);

        Booking booking = new Booking();

        booking.setUser(guest);
        booking.setBookingReference(
                "TB-" + UUID.randomUUID()
                        .toString()
                        .substring(0, 10)
                        .toUpperCase()
        );
        booking.setTotalAmount(new BigDecimal("5000.00"));
        booking.setCurrency("INR");
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setGuestCount(2);
        booking.setGuestName("Voucher Guest");
        booking.setGuestEmail("vch-guest@test.travelbuddy");
        booking.setGuestPhone("9000000000");

        booking = bookingRepository.save(booking);

        HotelReservation reservation = new HotelReservation();

        reservation.setBooking(booking);
        reservation.setRoomType(roomType);
        reservation.setCheckIn(checkIn);
        reservation.setCheckOut(checkOut);
        reservation.setRoomsBooked(1);

        reservationRepository.save(reservation);

        bookingId = booking.getBookingId();
    }

    private User createUser(String email) {
        User user = new User();
        user.setFullName("Test User");
        user.setEmail(email);
        user.setPasswordHash("{noop}password");

        return userRepository.save(user);
    }
    @Test
    @DisplayName("A voucher is issued with a code and a signed QR payload")
    void voucherIsIssued() {
        VoucherResponse voucher = service.issueForBooking(
                bookingId, guest.getUserId()
        );

        assertEquals(VoucherStatus.ACTIVE, voucher.status());
        assertTrue(voucher.voucherCode().startsWith("TBV-"));
        assertTrue(voucher.usable());
        assertNotNull(voucher.qrPayload());
        assertTrue(
                voucher.qrPayload().startsWith("TBV1."),
                "The QR payload must be a signed Travel Buddy token"
        );
    }

    @Test
    @DisplayName("Issuing twice returns the same voucher, never two")
    void issuingIsIdempotent() {
        VoucherResponse first = service.issueForBooking(
                bookingId, guest.getUserId()
        );
        VoucherResponse second = service.issueForBooking(
                bookingId, guest.getUserId()
        );

        assertEquals(first.voucherId(), second.voucherId());
        assertEquals(first.voucherCode(), second.voucherCode());
    }

    @Test
    @DisplayName("The QR payload contains no guest data")
    void qrPayloadLeaksNothing() {
        VoucherResponse voucher = service.issueForBooking(
                bookingId, guest.getUserId()
        );

        String payload = voucher.qrPayload();

        assertFalse(
                payload.contains("Voucher Guest"),
                "A photographed voucher must not reveal the guest name"
        );
        assertFalse(
                payload.contains("9000000000"),
                "A photographed voucher must not reveal a phone number"
        );
    }

    @Test
    @DisplayName("A genuine QR payload verifies")
    void genuinePayloadVerifies() {
        VoucherResponse voucher = service.issueForBooking(
                bookingId, guest.getUserId()
        );

        VoucherVerificationResponse result = service.verify(
                partner.getUserId(), voucher.qrPayload()
        );

        assertTrue(result.valid());
        assertEquals("Voucher Guest", result.guestName());
        assertNotNull(result.propertyName());
    }

    @Test
    @DisplayName("A tampered QR payload is rejected")
    void tamperedPayloadRejected() {
        VoucherResponse voucher = service.issueForBooking(
                bookingId, guest.getUserId()
        );

        String tampered = tamperMidpoint(voucher.qrPayload());

        assertNotEquals(
                voucher.qrPayload(), tampered,
                "the tamper must actually change the payload"
        );

        VoucherVerificationResponse result = service.verify(
                partner.getUserId(), tampered
        );

        assertFalse(result.valid());
        assertNull(
                result.guestName(),
                "A failed scan must reveal nothing about the guest"
        );
    }

    /**
     * Rewrites one character in the middle of the token.
     *
     * <p>The token is {@code VERSION.payload.signature}, both
     * segments unpadded base64url. The previous version of this
     * test changed the FINAL character, on the theory that
     * choosing a different letter was enough to alter the token.
     * It is not: unpadded base64 leaves unused low bits in the
     * last character, so roughly one change in sixteen decodes to
     * the very same bytes, the signature still verified, and the
     * test failed for no reason.
     *
     * <p>Every character before the last contributes all six of
     * its bits, so a midpoint rewrite is guaranteed to change the
     * decoded value. The property under test, that the signature
     * covers the payload, holds either way; only the reliability
     * of the test was broken.
     */
    private String tamperMidpoint(String token) {
        int index = token.length() / 2;
        char original = token.charAt(index);
        char replacement = original == 'a' ? 'b' : 'a';

        return token.substring(0, index)
                + replacement
                + token.substring(index + 1);
    }

    @Test
    @DisplayName("A forged payload for another booking is rejected")
    void forgedPayloadRejected() {
        service.issueForBooking(bookingId, guest.getUserId());

        /* Sign a token this server never issued for any real voucher. */
        String forged = tokenService.issue(
                999_999L,
                "TBV-FAKEFAKE",
                LocalDate.now().plusDays(5)
        );

        VoucherVerificationResponse result = service.verify(
                partner.getUserId(), forged
        );

        assertFalse(result.valid());
    }

    @Test
    @DisplayName("An unknown manual code is rejected without leaking anything")
    void unknownCodeRejected() {
        VoucherVerificationResponse result = service.verify(
                partner.getUserId(), "TBV-XXXXXXXX"
        );

        assertFalse(result.valid());
        assertNull(result.bookingReference());
        assertNull(result.guestName());
    }

    @Test
    @DisplayName("Check-in consumes the voucher so it cannot be replayed")
    void voucherIsSingleUse() {
        VoucherResponse voucher = service.issueForBooking(
                bookingId, guest.getUserId()
        );

        service.consumeForStay(bookingId, null);

        VoucherVerificationResponse result = service.verify(
                partner.getUserId(), voucher.qrPayload()
        );

        assertFalse(
                result.valid(),
                "A used voucher must not admit a second guest"
        );

        assertEquals(
                VoucherStatus.USED,
                voucherRepository.findById(voucher.voucherId())
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    @DisplayName("A voided voucher is rejected")
    void voidedVoucherRejected() {
        VoucherResponse voucher = service.issueForBooking(
                bookingId, guest.getUserId()
        );

        service.voidVoucher(
                partner.getUserId(),
                voucher.voucherCode(),
                "Guest cancelled"
        );

        VoucherVerificationResponse result = service.verify(
                partner.getUserId(), voucher.voucherCode()
        );

        assertFalse(result.valid());

        assertTrue(
                voucherRepository.existsById(voucher.voucherId()),
                "A voided voucher must be kept for audit, not deleted"
        );
    }

    @Test
    @DisplayName("A voucher for another property is rejected")
    void crossPropertyScanRejected() {
        VoucherResponse voucher = service.issueForBooking(
                bookingId, guest.getUserId()
        );

        User stranger = createUser("vch-stranger@test.travelbuddy");
        roleService.grantBaselineTravelerRole(stranger);
        roleService.grant(stranger, Role.ROLE_HOTEL_PARTNER, null);

        VoucherVerificationResponse result = service.verify(
                stranger.getUserId(), voucher.voucherCode()
        );

        assertFalse(result.valid());
    }

    @Test
    @DisplayName("Another traveler cannot issue a voucher for this booking")
    void issuingIsOwnershipChecked() {
        User stranger = createUser("vch-other@test.travelbuddy");
        roleService.grantBaselineTravelerRole(stranger);

        assertThrows(
                com.Travel.Buddy.exception.PartnerApplicationException
                        .class,
                () -> service.issueForBooking(
                        bookingId, stranger.getUserId()
                )
        );
    }
}