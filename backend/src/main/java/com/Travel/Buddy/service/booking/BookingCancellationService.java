package com.Travel.Buddy.service.booking;

import com.Travel.Buddy.dto.booking.BookingResponse;
import com.Travel.Buddy.dto.cancellation.BookingCancellationResponse;
import com.Travel.Buddy.dto.cancellation.CancelBookingRequest;
import com.Travel.Buddy.entity.Booking;
import com.Travel.Buddy.entity.BookingCancellation;
import com.Travel.Buddy.entity.BookingStatus;
import com.Travel.Buddy.entity.CancellationReason;
import com.Travel.Buddy.entity.HotelReservation;
import com.Travel.Buddy.entity.PaymentStatus;
import com.Travel.Buddy.entity.RefundStatus;
import com.Travel.Buddy.entity.RoomInventoryDaily;
import com.Travel.Buddy.repository.BookingCancellationRepository;
import com.Travel.Buddy.repository.BookingRepository;
import com.Travel.Buddy.repository.HotelReservationRepository;
import com.Travel.Buddy.repository.RoomInventoryRepository;
import com.Travel.Buddy.service.notification.NotificationEvents;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class BookingCancellationService {

    private final BookingRepository bookingRepository;

    private final BookingCancellationRepository
            bookingCancellationRepository;

    private final HotelReservationRepository
            hotelReservationRepository;

    private final RoomInventoryRepository
            roomInventoryRepository;

    private final NotificationEvents notificationEvents;

    public BookingCancellationService(
            BookingRepository bookingRepository,
            BookingCancellationRepository bookingCancellationRepository,
            HotelReservationRepository hotelReservationRepository,
            RoomInventoryRepository roomInventoryRepository,
            NotificationEvents notificationEvents
    ) {

        this.bookingRepository =
                bookingRepository;

        this.bookingCancellationRepository =
                bookingCancellationRepository;

        this.hotelReservationRepository =
                hotelReservationRepository;

        this.roomInventoryRepository =
                roomInventoryRepository;

        this.notificationEvents =
                notificationEvents;
    }


    /*
     * ============================================================
     * CANCEL BOOKING
     * ============================================================
     */

    @Transactional
    public BookingResponse cancelBooking(
            Long userId,
            Long bookingId,
            CancelBookingRequest request
    ) {

        /*
         * --------------------------------------------------------
         * Validate request
         * --------------------------------------------------------
         */

        if (request == null) {

            throw new BookingException(
                    "Cancellation request is required"
            );
        }


        if (request.reason() == null) {

            throw new BookingException(
                    "Cancellation reason is required"
            );
        }


        /*
         * --------------------------------------------------------
         * Lock booking
         * --------------------------------------------------------
         *
         * Prevents two simultaneous cancellation operations.
         */

        Booking booking =
                bookingRepository
                        .findByIdForUpdate(
                                bookingId
                        )
                        .orElseThrow(
                                () ->
                                        new BookingException(
                                                "Booking not found"
                                        )
                        );


        /*
         * --------------------------------------------------------
         * Ownership
         * --------------------------------------------------------
         */

        validateOwnership(
                booking,
                userId
        );


        /*
         * --------------------------------------------------------
         * Validate status
         * --------------------------------------------------------
         */

        validateBookingStatus(
                booking
        );


        /*
         * --------------------------------------------------------
         * Prevent duplicate cancellation
         * --------------------------------------------------------
         */

        if (bookingCancellationRepository
                .existsByBooking_BookingId(
                        bookingId
                )) {

            throw new BookingException(
                    "Cancellation has already been recorded for this booking"
            );
        }


        /*
         * --------------------------------------------------------
         * Find reservation
         * --------------------------------------------------------
         */

        HotelReservation reservation =
                hotelReservationRepository
                        .findByBooking_BookingId(
                                bookingId
                        )
                        .orElseThrow(
                                () ->
                                        new BookingException(
                                                "Hotel reservation not found"
                                        )
                        );


        /*
         * --------------------------------------------------------
         * Validate cancellation date
         * --------------------------------------------------------
         */

        validateCancellationDate(
                reservation
        );


        /*
         * --------------------------------------------------------
         * Release inventory
         * --------------------------------------------------------
         */

        releaseInventory(
                reservation
        );


        /*
         * --------------------------------------------------------
         * Create cancellation history
         * --------------------------------------------------------
         */

        BookingCancellation cancellation =
                new BookingCancellation();


        cancellation.setBooking(
                booking
        );


        cancellation.setCancelledByUser(
                booking.getUser()
        );


        cancellation.setCancellationReason(
                request.reason()
        );


        /*
         * --------------------------------------------------------
         * Cancellation note
         * --------------------------------------------------------
         */

        String note =
                request.note();

        if (note != null) {

            note =
                    note.trim();

            if (note.isEmpty()) {

                note = null;
            }
        }

        cancellation.setCancellationNote(
                note
        );


        /*
         * --------------------------------------------------------
         * Cancellation timestamp
         * --------------------------------------------------------
         */

        cancellation.setCancelledAt(
                LocalDateTime.now()
        );


        /*
         * --------------------------------------------------------
         * Refund foundation
         * --------------------------------------------------------
         *
         * Actual Razorpay refund processing is deliberately
         * postponed until the final payment phase.
         */

        if (booking.getPaymentStatus()
                == PaymentStatus.PAID) {

            cancellation.setRefundStatus(
                    RefundStatus.PENDING
            );


            cancellation.setRefundAmount(
                    safeRefundAmount(
                            booking.getTotalAmount()
                    )
            );


            cancellation.setRefundCurrency(
                    booking.getCurrency()
            );


            cancellation.setRefundReference(
                    null
            );


            cancellation.setRefundProcessedAt(
                    null
            );

        } else {

            cancellation.setRefundStatus(
                    RefundStatus.NOT_APPLICABLE
            );


            cancellation.setRefundAmount(
                    null
            );


            cancellation.setRefundCurrency(
                    null
            );


            cancellation.setRefundReference(
                    null
            );


            cancellation.setRefundProcessedAt(
                    null
            );
        }


        /*
         * --------------------------------------------------------
         * Save cancellation history
         * --------------------------------------------------------
         */

        bookingCancellationRepository.save(
                cancellation
        );


        /*
         * --------------------------------------------------------
         * Cancel booking
         * --------------------------------------------------------
         */

        booking.setBookingStatus(
                BookingStatus.CANCELLED
        );


        /*
         * --------------------------------------------------------
         * Remove temporary payment hold
         * --------------------------------------------------------
         *
         * A cancelled booking must never retain an active
         * payment-hold timestamp.
         */

        booking.setHoldExpiresAt(
                null
        );


        /*
         * --------------------------------------------------------
         * Save booking
         * --------------------------------------------------------
         */

        bookingRepository.save(
                booking
        );


        /*
         * --------------------------------------------------------
         * Return updated booking
         * --------------------------------------------------------
         */

        BookingResponse response =
                toBookingResponse(
                        booking,
                        reservation
                );

        notificationEvents.bookingCancelled(
                booking,
                readableReason(request.reason())
        );

        return response;
    }


    /*
     * ============================================================
     * GET CANCELLATION HISTORY
     * ============================================================
     */

    @Transactional(readOnly = true)
    public BookingCancellationResponse getCancellation(
            Long userId,
            Long bookingId
    ) {

        BookingCancellation cancellation =
                bookingCancellationRepository
                        .findByBooking_BookingId(
                                bookingId
                        )
                        .orElse(null);


        /*
         * Some older cancelled bookings may not have a
         * cancellation-history row.
         */

        if (cancellation == null) {

            return null;
        }


        Booking booking =
                cancellation.getBooking();


        /*
         * --------------------------------------------------------
         * Ownership
         * --------------------------------------------------------
         */

        if (booking == null
                || booking.getUser() == null
                || booking.getUser().getUserId() == null
                || !booking.getUser()
                .getUserId()
                .equals(userId)) {

            throw new BookingException(
                    "You are not allowed to view this cancellation"
            );
        }


        return toCancellationResponse(
                cancellation
        );
    }


    /*
     * ============================================================
     * OWNERSHIP VALIDATION
     * ============================================================
     */

    private void validateOwnership(
            Booking booking,
            Long userId
    ) {

        if (booking.getUser() == null
                || booking.getUser().getUserId() == null
                || !booking.getUser()
                .getUserId()
                .equals(userId)) {

            throw new BookingException(
                    "You are not allowed to cancel this booking"
            );
        }
    }


    /*
     * ============================================================
     * BOOKING STATUS VALIDATION
     * ============================================================
     */

    private void validateBookingStatus(
            Booking booking
    ) {

        /*
         * Already cancelled.
         */

        if (booking.getBookingStatus()
                == BookingStatus.CANCELLED) {

            throw new BookingException(
                    "This booking has already been cancelled"
            );
        }


        /*
         * Completed bookings cannot be cancelled.
         */

        if (booking.getBookingStatus()
                == BookingStatus.COMPLETED) {

            throw new BookingException(
                    "Completed bookings cannot be cancelled"
            );
        }


        /*
         * Only pending and confirmed bookings can be cancelled.
         */

        if (booking.getBookingStatus()
                != BookingStatus.PENDING
                && booking.getBookingStatus()
                != BookingStatus.CONFIRMED) {

            throw new BookingException(
                    "This booking cannot be cancelled"
            );
        }
    }


    /*
     * ============================================================
     * CANCELLATION DATE VALIDATION
     * ============================================================
     */

    private void validateCancellationDate(
            HotelReservation reservation
    ) {

        LocalDate today =
                LocalDate.now();


        if (!reservation
                .getCheckIn()
                .isAfter(today)) {

            throw new BookingException(
                    "This booking can no longer be cancelled because check-in has started or passed"
            );
        }
    }


    /*
     * ============================================================
     * RELEASE INVENTORY
     * ============================================================
     */

    private void releaseInventory(
            HotelReservation reservation
    ) {

        /*
         * Lock inventory rows.
         */

        List<RoomInventoryDaily> inventory =
                roomInventoryRepository
                        .findForAvailability(
                                reservation
                                        .getRoomType()
                                        .getRoomTypeId(),

                                reservation
                                        .getCheckIn(),

                                reservation
                                        .getCheckOut()
                        );


        /*
         * Calculate required nights.
         */

        int requiredNights =
                numberOfNights(
                        reservation
                );


        /*
         * Ensure inventory exists for every night.
         */

        if (inventory.size()
                != requiredNights) {

            throw new BookingException(
                    "Inventory is incomplete for reservation "
                            + reservation.getReservationId()
            );
        }


        /*
         * Release reserved rooms.
         */

        for (RoomInventoryDaily daily :
                inventory) {

            daily.releaseRooms(
                    reservation.getRoomsBooked()
            );
        }
    }


    /*
     * ============================================================
     * NUMBER OF NIGHTS
     * ============================================================
     */

    private int numberOfNights(
            HotelReservation reservation
    ) {

        return (int)
                ChronoUnit.DAYS.between(
                        reservation.getCheckIn(),
                        reservation.getCheckOut()
                );
    }


    /*
     * ============================================================
     * REFUND AMOUNT
     * ============================================================
     */

    private BigDecimal safeRefundAmount(
            BigDecimal totalAmount
    ) {

        if (totalAmount == null) {

            throw new BookingException(
                    "Booking total amount is missing"
            );
        }


        if (totalAmount.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            throw new BookingException(
                    "Booking refund amount must be greater than zero"
            );
        }


        return totalAmount;
    }


    /*
     * ============================================================
     * BOOKING RESPONSE
     * ============================================================
     */

    private BookingResponse toBookingResponse(
            Booking booking,
            HotelReservation reservation
    ) {

        int nights =
                numberOfNights(
                        reservation
                );


        Integer guests =
                booking.getGuestCount();


        Integer rooms =
                reservation.getRoomsBooked();


        return new BookingResponse(

                booking.getBookingId(),

                booking.getBookingReference(),

                booking.getUser()
                        .getUserId(),

                reservation
                        .getRoomType()
                        .getProperty()
                        .getPropertyId(),

                reservation
                        .getRoomType()
                        .getProperty()
                        .getName(),

                reservation
                        .getRoomType()
                        .getRoomTypeId(),

                reservation
                        .getRoomType()
                        .getCategoryName(),

                reservation.getCheckIn(),

                reservation.getCheckOut(),

                guests,

                rooms,

                nights,

                booking.getTotalAmount(),

                booking.getCurrency(),

                booking.getBookingStatus(),

                booking.getPaymentStatus(),

                booking.getHoldExpiresAt(),

                booking.getCreatedAt(),
                booking.getBookingType()
        );
    }


    /**
     * Turns a cancellation reason code into something a traveller
     * can read in a notification. The stored enum is deliberately
     * machine-readable, which makes it useless in a sentence.
     */
    private String readableReason(
            CancellationReason reason
    ) {

        if (reason == null) {
            return null;
        }

        return reason.name()
                .toLowerCase()
                .replace('_', ' ');
    }

    /*
     * ============================================================
     * CANCELLATION RESPONSE
     * ============================================================
     */
    private BookingCancellationResponse
    toCancellationResponse(
            BookingCancellation cancellation
    ) {

        Booking booking =
                cancellation.getBooking();


        return new BookingCancellationResponse(

                cancellation.getCancellationId(),

                booking.getBookingId(),

                booking.getBookingReference(),

                cancellation
                        .getCancellationReason(),

                cancellation
                        .getCancellationNote(),

                cancellation
                        .getCancelledAt(),

                cancellation
                        .getRefundStatus(),

                cancellation
                        .getRefundAmount(),

                cancellation
                        .getRefundCurrency(),

                cancellation
                        .getRefundReference(),

                cancellation
                        .getRefundProcessedAt(),

                cancellation
                        .getCreatedAt()
        );
    }
}