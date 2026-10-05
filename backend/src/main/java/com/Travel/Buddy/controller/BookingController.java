package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.booking.BookingResponse;
import com.Travel.Buddy.dto.booking.CreateHotelBookingRequest;
import com.Travel.Buddy.dto.booking.MockPaymentRequest;
import com.Travel.Buddy.dto.cancellation.BookingCancellationResponse;
import com.Travel.Buddy.dto.cancellation.CancelBookingRequest;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.booking.BookingCancellationService;
import com.Travel.Buddy.service.booking.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final BookingCancellationService bookingCancellationService;
    private final UserRepository userRepository;

    public BookingController(
            BookingService bookingService,
            BookingCancellationService bookingCancellationService,
            UserRepository userRepository
    ) {
        this.bookingService = bookingService;
        this.bookingCancellationService = bookingCancellationService;
        this.userRepository = userRepository;
    }

    @PostMapping("/hotel")
    public ResponseEntity<BookingResponse> createHotelBooking(
            Authentication authentication,
            @Valid @RequestBody CreateHotelBookingRequest request
    ) {

        User user = getAuthenticatedUser(authentication);

        BookingResponse response =
                bookingService.createHotelBooking(
                        user.getUserId(),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyBookings(
            Authentication authentication
    ) {

        User user = getAuthenticatedUser(authentication);

        return ResponseEntity.ok(
                bookingService.getUserBookings(
                        user.getUserId()
                )
        );
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> getBooking(
            Authentication authentication,
            @PathVariable Long bookingId
    ) {

        User user = getAuthenticatedUser(authentication);

        return ResponseEntity.ok(
                bookingService.getUserBooking(
                        user.getUserId(),
                        bookingId
                )
        );
    }

    @PostMapping("/{bookingId}/payment/mock")
    public ResponseEntity<BookingResponse> verifyMockPayment(
            Authentication authentication,
            @PathVariable Long bookingId,
            @Valid @RequestBody MockPaymentRequest request
    ) {

        User user = getAuthenticatedUser(authentication);

        return ResponseEntity.ok(
                bookingService.verifyMockPayment(
                        user.getUserId(),
                        bookingId,
                        request.paymentSuccessful()
                )
        );
    }

    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(
            Authentication authentication,
            @PathVariable Long bookingId,
            @Valid @RequestBody CancelBookingRequest request
    ) {

        User user = getAuthenticatedUser(authentication);

        BookingResponse response =
                bookingCancellationService.cancelBooking(
                        user.getUserId(),
                        bookingId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{bookingId}/cancellation")
    public ResponseEntity<BookingCancellationResponse> getCancellation(
            Authentication authentication,
            @PathVariable Long bookingId
    ) {

        User user = getAuthenticatedUser(authentication);

        BookingCancellationResponse response =
                bookingCancellationService.getCancellation(
                        user.getUserId(),
                        bookingId
                );

        return ResponseEntity.ok(response);
    }

    private User getAuthenticatedUser(
            Authentication authentication
    ) {

        if (authentication == null
                || authentication.getName() == null
                || authentication.getName().isBlank()) {

            throw new IllegalStateException(
                    "Authenticated user not found"
            );
        }

        return userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user not found"
                        )
                );
    }
}