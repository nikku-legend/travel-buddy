package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.voucher.VoucherResponse;
import com.Travel.Buddy.dto.voucher.VoucherScanRequest;
import com.Travel.Buddy.dto.voucher.VoucherVerificationResponse;
import com.Travel.Buddy.dto.voucher.VoucherVoidRequest;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.voucher.VoucherService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Digital vouchers and front-desk QR verification. (FR-24)
 *
 * <p>Two audiences:
 * <ul>
 *   <li>{@code /api/v1/bookings/{id}/voucher} - the guest</li>
 *   <li>{@code /api/v1/partner/hotel/vouchers} - the front desk</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class VoucherController {

    private final VoucherService voucherService;

    private final UserRepository userRepository;

    public VoucherController(
            VoucherService voucherService,
            UserRepository userRepository
    ) {
        this.voucherService = voucherService;
        this.userRepository = userRepository;
    }

    /* ============================================================
     * GUEST
     * ============================================================ */

    /**
     * The guest's voucher wallet.
     */
    @GetMapping("/vouchers")
    public ResponseEntity<List<VoucherResponse>> myVouchers(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                voucherService.myVouchers(
                        currentUserId(authentication)
                )
        );
    }

    /**
     * Issues, or returns, the voucher for one booking.
     *
     * <p>Idempotent, so a guest refreshing their wallet never ends
     * up holding two live vouchers.
     */
    @PostMapping("/bookings/{bookingId}/voucher")
    public ResponseEntity<VoucherResponse> issue(
            Authentication authentication,
            @PathVariable Long bookingId
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        voucherService.issueForBooking(
                                bookingId,
                                currentUserId(authentication)
                        )
                );
    }

    /* ============================================================
     * FRONT DESK
     * ============================================================ */

    /**
     * Scans a voucher.
     *
     * <p>Accepts either the signed QR payload or the short manual
     * code. Returns 200 either way: the front desk needs a verdict
     * and a reason, not an HTTP error it would have to interpret.
     */
    @PostMapping("/partner/hotel/vouchers/verify")
    public ResponseEntity<VoucherVerificationResponse> verify(
            Authentication authentication,
            @Valid @RequestBody VoucherScanRequest request
    ) {
        return ResponseEntity.ok(
                voucherService.verify(
                        currentUserId(authentication),
                        request.code()
                )
        );
    }

    @GetMapping("/partner/hotel/vouchers/{code}")
    public ResponseEntity<VoucherResponse> getByCode(
            Authentication authentication,
            @PathVariable String code
    ) {
        return ResponseEntity.ok(
                voucherService.getByCode(
                        currentUserId(authentication),
                        code
                )
        );
    }

    /**
     * Voids a voucher, for example when the guest cancels.
     */
    @DeleteMapping("/partner/hotel/vouchers/{code}")
    public ResponseEntity<VoucherResponse> voidVoucher(
            Authentication authentication,
            @PathVariable String code,
            @Valid @RequestBody VoucherVoidRequest request
    ) {
        return ResponseEntity.ok(
                voucherService.voidVoucher(
                        currentUserId(authentication),
                        code,
                        request.reason()
                )
        );
    }

    private Long currentUserId(Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new AccessDeniedException(
                    "Authentication is required"
            );
        }

        User user = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Authenticated user no longer exists"
                        )
                );

        return user.getUserId();
    }
}