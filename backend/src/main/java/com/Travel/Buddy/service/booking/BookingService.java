package com.Travel.Buddy.service.booking;

import com.Travel.Buddy.dto.booking.BookingResponse;
import com.Travel.Buddy.dto.booking.CreateHotelBookingRequest;
import com.Travel.Buddy.dto.payment.PaymentOrderResponse;
import com.Travel.Buddy.dto.payment.VerifyPaymentRequest;
import com.Travel.Buddy.entity.Booking;
import com.Travel.Buddy.entity.BookingStatus;
import com.Travel.Buddy.entity.HotelReservation;
import com.Travel.Buddy.entity.PaymentStatus;
import com.Travel.Buddy.entity.RoomInventoryDaily;
import com.Travel.Buddy.entity.RoomType;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.BookingRepository;
import com.Travel.Buddy.repository.HotelReservationRepository;
import com.Travel.Buddy.repository.RoomInventoryRepository;
import com.Travel.Buddy.repository.RoomTypeRepository;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.notification.NotificationEvents;
import com.Travel.Buddy.service.payment.PaymentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;

    private final HotelReservationRepository
            hotelReservationRepository;

    private final RoomInventoryRepository
            roomInventoryRepository;

    private final RoomTypeRepository
            roomTypeRepository;

    private final UserRepository
            userRepository;

    private final PaymentService
            paymentService;

    private final int holdDurationMinutes;

    private final NotificationEvents notificationEvents;


    public BookingService(
            BookingRepository bookingRepository,
            HotelReservationRepository hotelReservationRepository,
            RoomInventoryRepository roomInventoryRepository,
            RoomTypeRepository roomTypeRepository,
            UserRepository userRepository,
            PaymentService paymentService,
            @Value("${app.booking-hold.duration-minutes:15}")
            int holdDurationMinutes,
            NotificationEvents notificationEvents
    ) {

        this.bookingRepository =
                bookingRepository;

        this.hotelReservationRepository =
                hotelReservationRepository;

        this.roomInventoryRepository =
                roomInventoryRepository;

        this.roomTypeRepository =
                roomTypeRepository;

        this.userRepository =
                userRepository;

        this.paymentService =
                paymentService;

        this.holdDurationMinutes =
                holdDurationMinutes;

        this.notificationEvents =
                notificationEvents;
    }


    /*
     * ============================================================
     * CREATE HOTEL BOOKING
     * ============================================================
     */

    @Transactional
    public BookingResponse createHotelBooking(
            Long userId,
            CreateHotelBookingRequest request
    ) {

        /*
         * --------------------------------------------------------
         * Validate dates
         * --------------------------------------------------------
         */

        validateDates(
                request.checkIn(),
                request.checkOut()
        );


        /*
         * --------------------------------------------------------
         * Validate guests
         * --------------------------------------------------------
         */

        if (request.guests() == null
                || request.guests() < 1) {

            throw new BookingException(
                    "At least one guest is required"
            );
        }


        /*
         * --------------------------------------------------------
         * Validate rooms
         * --------------------------------------------------------
         */

        if (request.rooms() == null
                || request.rooms() < 1) {

            throw new BookingException(
                    "At least one room is required"
            );
        }


        /*
         * --------------------------------------------------------
         * Find user
         * --------------------------------------------------------
         */

        User user =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new BookingException(
                                        "User not found"
                                )
                        );


        /*
         * --------------------------------------------------------
         * Find room type
         * --------------------------------------------------------
         */

        RoomType roomType =
                roomTypeRepository.findById(
                                request.roomTypeId()
                        )
                        .orElseThrow(() ->
                                new BookingException(
                                        "Room type not found"
                                )
                        );


        /*
         * --------------------------------------------------------
         * Room must be active
         * --------------------------------------------------------
         */

        if (!Boolean.TRUE.equals(
                roomType.getActive()
        )) {

            throw new BookingException(
                    "Room type is not active"
            );
        }


        /*
         * --------------------------------------------------------
         * Occupancy validation
         * --------------------------------------------------------
         */

        if (request.guests()
                > roomType.getMaxOccupancy()) {

            throw new BookingException(
                    "This room can accommodate a maximum of "
                            + roomType.getMaxOccupancy()
                            + " guests"
            );
        }


        /*
         * --------------------------------------------------------
         * Calculate nights
         * --------------------------------------------------------
         */

        long numberOfNights =
                ChronoUnit.DAYS.between(
                        request.checkIn(),
                        request.checkOut()
                );


        /*
         * --------------------------------------------------------
         * Maximum stay validation
         * --------------------------------------------------------
         */

        if (numberOfNights > 30) {

            throw new BookingException(
                    "Stay duration cannot exceed 30 nights"
            );
        }


        /*
         * --------------------------------------------------------
         * Lock inventory rows
         * --------------------------------------------------------
         *
         * The repository query uses pessimistic locking.
         *
         * This prevents two simultaneous users from successfully
         * reserving the same last room.
         */

        List<RoomInventoryDaily> inventory =
                roomInventoryRepository.findForAvailability(
                        roomType.getRoomTypeId(),
                        request.checkIn(),
                        request.checkOut()
                );


        /*
         * --------------------------------------------------------
         * Ensure inventory exists for every night
         * --------------------------------------------------------
         */

        if (inventory.size()
                != numberOfNights) {

            throw new BookingException(
                    "Inventory is not configured for all requested dates"
            );
        }


        /*
         * --------------------------------------------------------
         * Check availability for every night
         * --------------------------------------------------------
         */

        for (RoomInventoryDaily daily :
                inventory) {

            if (daily.getAvailableInventory()
                    < request.rooms()) {

                throw new BookingException(
                        "Not enough rooms available for "
                                + daily.getInventoryDate()
                );
            }
        }


        /*
         * --------------------------------------------------------
         * Calculate total
         * --------------------------------------------------------
         */

        BigDecimal totalAmount =
                roomType.getBasePrice()
                        .multiply(
                                BigDecimal.valueOf(
                                        numberOfNights
                                )
                        )
                        .multiply(
                                BigDecimal.valueOf(
                                        request.rooms()
                                )
                        );


        /*
         * --------------------------------------------------------
         * Create booking
         * --------------------------------------------------------
         */

        Booking booking =
                new Booking();

        booking.setUser(
                user
        );

        booking.setBookingReference(
                generateBookingReference()
        );

        booking.setTotalAmount(
                totalAmount
        );

        booking.setCurrency(
                roomType.getCurrency()
        );

        booking.setBookingStatus(
                BookingStatus.PENDING
        );

        booking.setPaymentStatus(
                PaymentStatus.UNPAID
        );


        /*
         * --------------------------------------------------------
         * Temporary payment hold
         * --------------------------------------------------------
         */

        booking.setHoldExpiresAt(
                LocalDateTime.now()
                        .plusMinutes(
                                holdDurationMinutes
                        )
        );


        /*
         * --------------------------------------------------------
         * Guest count
         * --------------------------------------------------------
         */

        booking.setGuestCount(
                request.guests()
        );


        /*
         * --------------------------------------------------------
         * Guest information
         * --------------------------------------------------------
         */

        booking.setGuestName(
                request.guestName().trim()
        );

        booking.setGuestEmail(
                request.guestEmail().trim()
        );

        booking.setGuestPhone(
                request.guestPhone().trim()
        );

        booking.setSpecialRequests(
                request.specialRequests()
        );


        /*
         * --------------------------------------------------------
         * Save booking
         * --------------------------------------------------------
         */

        booking =
                bookingRepository.save(
                        booking
                );


        /*
         * --------------------------------------------------------
         * Reserve inventory
         * --------------------------------------------------------
         */

        for (RoomInventoryDaily daily :
                inventory) {

            daily.reserveRooms(
                    request.rooms()
            );
        }


        /*
         * --------------------------------------------------------
         * Create hotel reservation
         * --------------------------------------------------------
         */

        HotelReservation reservation =
                new HotelReservation();

        reservation.setBooking(
                booking
        );

        reservation.setRoomType(
                roomType
        );

        reservation.setCheckIn(
                request.checkIn()
        );

        reservation.setCheckOut(
                request.checkOut()
        );

        reservation.setRoomsBooked(
                request.rooms()
        );


        /*
         * Physical room numbers will be assigned later
         * by the hotel partner.
         */

        reservation.setAssignedRoomNumbers(
                null
        );


        /*
         * --------------------------------------------------------
         * Save hotel reservation
         * --------------------------------------------------------
         */

        hotelReservationRepository.save(
                reservation
        );


        /*
         * --------------------------------------------------------
         * Return booking response
         * --------------------------------------------------------
         */

        return toResponse(
                booking,
                reservation,
                request.guests(),
                request.rooms(),
                (int) numberOfNights
        );
    }


    /*
     * ============================================================
     * CREATE RAZORPAY PAYMENT ORDER
     * ============================================================
     */

    @Transactional(
            noRollbackFor = PaymentExpiredException.class
    )
    public PaymentOrderResponse createPaymentOrder(
            Long userId,
            Long bookingId
    ) {

        /*
         * --------------------------------------------------------
         * Lock booking
         * --------------------------------------------------------
         */

        Booking booking =
                bookingRepository.findByIdForUpdate(
                                bookingId
                        )
                        .orElseThrow(() ->
                                new BookingException(
                                        "Booking not found"
                                )
                        );


        /*
         * --------------------------------------------------------
         * Ownership validation
         * --------------------------------------------------------
         */

        if (!booking.getUser()
                .getUserId()
                .equals(userId)) {

            throw new BookingException(
                    "You are not allowed to pay for this booking"
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
                        .orElseThrow(() ->
                                new BookingException(
                                        "Hotel reservation not found"
                                )
                        );


        /*
         * --------------------------------------------------------
         * Already paid
         * --------------------------------------------------------
         */

        if (booking.getBookingStatus()
                == BookingStatus.CONFIRMED
                && booking.getPaymentStatus()
                == PaymentStatus.PAID) {

            throw new BookingException(
                    "This booking has already been paid"
            );
        }


        /*
         * --------------------------------------------------------
         * Booking must be payable
         * --------------------------------------------------------
         */

        if (booking.getBookingStatus()
                != BookingStatus.PENDING
                || booking.getPaymentStatus()
                != PaymentStatus.UNPAID) {

            throw new BookingException(
                    "This booking can no longer be paid"
            );
        }


        /*
         * --------------------------------------------------------
         * Check hold expiration
         * --------------------------------------------------------
         */

        if (booking.getHoldExpiresAt() == null
                || !booking.getHoldExpiresAt()
                .isAfter(LocalDateTime.now())) {

            /*
             * Release inventory exactly once.
             */
            releaseInventory(
                    reservation
            );


            /*
             * Cancel booking.
             */
            booking.setBookingStatus(
                    BookingStatus.CANCELLED
            );


            /*
             * Remove hold timestamp.
             */
            booking.setHoldExpiresAt(
                    null
            );


            /*
             * IMPORTANT:
             *
             * Persist cancellation before throwing the expected
             * PaymentExpiredException.
             */
            bookingRepository.save(
                    booking
            );


            throw new PaymentExpiredException(
                    "Your room hold has expired. Inventory has been released."
            );
        }


        /*
         * --------------------------------------------------------
         * Reuse existing Razorpay order
         * --------------------------------------------------------
         *
         * Prevents duplicate Razorpay orders when the frontend
         * retries the request.
         */

        if (booking.getRazorpayOrderId() != null
                && !booking.getRazorpayOrderId()
                .isBlank()) {

            return new PaymentOrderResponse(
                    booking.getRazorpayOrderId(),
                    booking.getTotalAmount(),
                    booking.getCurrency(),
                    paymentService.getKeyId(),
                    booking.getBookingId(),
                    booking.getBookingReference()
            );
        }


        /*
         * --------------------------------------------------------
         * Create Razorpay order
         * --------------------------------------------------------
         */

        String razorpayOrderId =
                paymentService.createOrder(
                        booking
                );


        /*
         * --------------------------------------------------------
         * Store Razorpay order ID
         * --------------------------------------------------------
         */

        booking.setRazorpayOrderId(
                razorpayOrderId
        );


        bookingRepository.save(
                booking
        );


        /*
         * --------------------------------------------------------
         * Return payment order
         * --------------------------------------------------------
         */

        return new PaymentOrderResponse(
                razorpayOrderId,
                booking.getTotalAmount(),
                booking.getCurrency(),
                paymentService.getKeyId(),
                booking.getBookingId(),
                booking.getBookingReference()
        );
    }


    /*
     * ============================================================
     * VERIFY RAZORPAY PAYMENT
     * ============================================================
     */

    @Transactional(
            noRollbackFor = PaymentExpiredException.class
    )
    public BookingResponse verifyPayment(
            Long userId,
            Long bookingId,
            VerifyPaymentRequest request
    ) {

        /*
         * --------------------------------------------------------
         * Lock booking
         * --------------------------------------------------------
         */

        Booking booking =
                bookingRepository.findByIdForUpdate(
                                bookingId
                        )
                        .orElseThrow(() ->
                                new BookingException(
                                        "Booking not found"
                                )
                        );


        /*
         * --------------------------------------------------------
         * Ownership
         * --------------------------------------------------------
         */

        if (!booking.getUser()
                .getUserId()
                .equals(userId)) {

            throw new BookingException(
                    "You are not allowed to pay for this booking"
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
                        .orElseThrow(() ->
                                new BookingException(
                                        "Hotel reservation not found"
                                )
                        );


        /*
         * --------------------------------------------------------
         * Idempotency
         * --------------------------------------------------------
         *
         * If the payment has already been verified, don't process
         * it again.
         */

        if (booking.getBookingStatus()
                == BookingStatus.CONFIRMED
                && booking.getPaymentStatus()
                == PaymentStatus.PAID) {

            return toResponse(
                    booking,
                    reservation,
                    booking.getGuestCount(),
                    reservation.getRoomsBooked(),
                    numberOfNights(reservation)
            );
        }


        /*
         * --------------------------------------------------------
         * Booking must still be payable
         * --------------------------------------------------------
         */

        if (booking.getBookingStatus()
                != BookingStatus.PENDING
                || booking.getPaymentStatus()
                != PaymentStatus.UNPAID) {

            throw new BookingException(
                    "This booking can no longer be paid"
            );
        }


        /*
         * --------------------------------------------------------
         * Check hold
         * --------------------------------------------------------
         */

        if (booking.getHoldExpiresAt() == null
                || !booking.getHoldExpiresAt()
                .isAfter(LocalDateTime.now())) {

            releaseInventory(
                    reservation
            );

            booking.setBookingStatus(
                    BookingStatus.CANCELLED
            );

            booking.setHoldExpiresAt(
                    null
            );

            bookingRepository.save(
                    booking
            );

            throw new PaymentExpiredException(
                    "Your room hold has expired. Inventory has been released."
            );
        }


        /*
         * --------------------------------------------------------
         * Razorpay order must exist
         * --------------------------------------------------------
         */

        if (booking.getRazorpayOrderId() == null
                || booking.getRazorpayOrderId()
                .isBlank()) {

            throw new BookingException(
                    "Payment order has not been created"
            );
        }


        /*
         * --------------------------------------------------------
         * Validate order ID
         * --------------------------------------------------------
         */

        if (!booking.getRazorpayOrderId()
                .equals(
                        request.razorpayOrderId()
                )) {

            throw new BookingException(
                    "Razorpay order does not match this booking"
            );
        }


        /*
         * --------------------------------------------------------
         * Verify Razorpay signature
         * --------------------------------------------------------
         */

        paymentService.verifySignature(
                request.razorpayOrderId(),
                request.razorpayPaymentId(),
                request.razorpaySignature()
        );


        /*
         * --------------------------------------------------------
         * Store payment information
         * --------------------------------------------------------
         */

        booking.setRazorpayPaymentId(
                request.razorpayPaymentId()
        );

        booking.setPaymentStatus(
                PaymentStatus.PAID
        );

        booking.setBookingStatus(
                BookingStatus.CONFIRMED
        );


        /*
         * Payment is complete.
         *
         * The temporary hold is no longer required.
         */

        booking.setHoldExpiresAt(
                null
        );


        /*
         * --------------------------------------------------------
         * Save confirmed booking
         * --------------------------------------------------------
         */

        bookingRepository.save(
                booking
        );


        notificationEvents.bookingConfirmed(
                booking
        );


        /*
         * --------------------------------------------------------
         * Return confirmed booking
         * --------------------------------------------------------
         */

        return toResponse(
                booking,
                reservation,
                booking.getGuestCount(),
                reservation.getRoomsBooked(),
                numberOfNights(reservation)
        );
    }


    /*
     * ============================================================
     * DEVELOPMENT MOCK PAYMENT
     * ============================================================
     *
     * Used while Razorpay is not being used for development
     * testing.
     */

    @Transactional(
            noRollbackFor = PaymentExpiredException.class
    )
    public BookingResponse verifyMockPayment(
            Long userId,
            Long bookingId,
            boolean paymentSuccessful
    ) {

        /*
         * --------------------------------------------------------
         * Lock booking
         * --------------------------------------------------------
         */

        Booking booking =
                bookingRepository.findByIdForUpdate(
                                bookingId
                        )
                        .orElseThrow(() ->
                                new BookingException(
                                        "Booking not found"
                                )
                        );


        /*
         * --------------------------------------------------------
         * Ownership
         * --------------------------------------------------------
         */

        if (!booking.getUser()
                .getUserId()
                .equals(userId)) {

            throw new BookingException(
                    "You are not allowed to pay for this booking"
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
                        .orElseThrow(() ->
                                new BookingException(
                                        "Hotel reservation not found"
                                )
                        );


        /*
         * --------------------------------------------------------
         * Idempotent success
         * --------------------------------------------------------
         */

        if (booking.getBookingStatus()
                == BookingStatus.CONFIRMED
                && booking.getPaymentStatus()
                == PaymentStatus.PAID) {

            return toResponse(
                    booking,
                    reservation,
                    booking.getGuestCount(),
                    reservation.getRoomsBooked(),
                    numberOfNights(reservation)
            );
        }


        /*
         * --------------------------------------------------------
         * Booking must be payable
         * --------------------------------------------------------
         */

        if (booking.getBookingStatus()
                != BookingStatus.PENDING
                || booking.getPaymentStatus()
                != PaymentStatus.UNPAID) {

            throw new BookingException(
                    "This booking can no longer be paid"
            );
        }


        /*
         * --------------------------------------------------------
         * Hold expired
         * --------------------------------------------------------
         */

        if (booking.getHoldExpiresAt() == null
                || !booking.getHoldExpiresAt()
                .isAfter(LocalDateTime.now())) {

            /*
             * Release inventory.
             */
            releaseInventory(
                    reservation
            );


            /*
             * Cancel booking.
             */
            booking.setBookingStatus(
                    BookingStatus.CANCELLED
            );


            /*
             * Remove hold.
             */
            booking.setHoldExpiresAt(
                    null
            );


            /*
             * Persist the expired state before throwing the
             * expected exception.
             */
            bookingRepository.save(
                    booking
            );


            throw new PaymentExpiredException(
                    "Your room hold has expired. Inventory has been released."
            );
        }


        /*
         * --------------------------------------------------------
         * Successful mock payment
         * --------------------------------------------------------
         */

        if (paymentSuccessful) {

            booking.setPaymentStatus(
                    PaymentStatus.PAID
            );

            booking.setBookingStatus(
                    BookingStatus.CONFIRMED
            );

            booking.setHoldExpiresAt(
                    null
            );

        } else {

            /*
             * ----------------------------------------------------
             * Mock payment failed
             * ----------------------------------------------------
             *
             * Release the temporary room hold.
             */

            releaseInventory(
                    reservation
            );

            booking.setBookingStatus(
                    BookingStatus.CANCELLED
            );

            booking.setHoldExpiresAt(
                    null
            );
        }


        /*
         * --------------------------------------------------------
         * Save final state
         * --------------------------------------------------------
         */

        bookingRepository.save(
                booking
        );


        /*
         * Confirmed bookings and failed mock payments are both
         * news the traveller needs, and the dedupe key on each
         * makes a retried request safe.
         */

        if (paymentSuccessful) {

            notificationEvents.bookingConfirmed(
                    booking
            );

        } else {

            notificationEvents.paymentFailed(
                    booking
            );
        }


        /*
         * --------------------------------------------------------
         * Return response
         * --------------------------------------------------------
         */

        return toResponse(
                booking,
                reservation,
                booking.getGuestCount(),
                reservation.getRoomsBooked(),
                numberOfNights(reservation)
        );
    }


    /*
     * ============================================================
     * AUTOMATIC HOLD EXPIRATION
     * ============================================================
     *
     * Runs automatically.
     *
     * Expired booking:
     *
     * PENDING + UNPAID
     *       ↓
     * release inventory
     *       ↓
     * CANCELLED
     *       ↓
     * holdExpiresAt = null
     */

    @Scheduled(
            fixedDelayString =
                    "${app.booking-hold.cleanup-interval-ms:60000}"
    )
    @Transactional
    public void expireUnpaidHolds() {

        LocalDateTime now =
                LocalDateTime.now();


        /*
         * --------------------------------------------------------
         * Find expired bookings
         * --------------------------------------------------------
         */

        List<Booking> expiredBookings =
                bookingRepository
                        .findExpiredUnpaidHoldsForUpdate(
                                BookingStatus.PENDING,
                                PaymentStatus.UNPAID,
                                now
                        );


        /*
         * --------------------------------------------------------
         * Process each expired booking
         * --------------------------------------------------------
         */

        for (Booking booking :
                expiredBookings) {

            /*
             * Safety check.
             */

            if (booking.getBookingStatus()
                    != BookingStatus.PENDING
                    || booking.getPaymentStatus()
                    != PaymentStatus.UNPAID) {

                continue;
            }


            /*
             * ----------------------------------------------------
             * Find reservation
             * ----------------------------------------------------
             */

            HotelReservation reservation =
                    hotelReservationRepository
                            .findByBooking_BookingId(
                                    booking.getBookingId()
                            )
                            .orElseThrow(() ->
                                    new BookingException(
                                            "Hotel reservation not found for expired hold"
                                    )
                            );


            /*
             * ----------------------------------------------------
             * Release inventory
             * ----------------------------------------------------
             */

            releaseInventory(
                    reservation
            );


            /*
             * ----------------------------------------------------
             * Cancel booking
             * ----------------------------------------------------
             */

            booking.setBookingStatus(
                    BookingStatus.CANCELLED
            );


            /*
             * ----------------------------------------------------
             * Remove hold
             * ----------------------------------------------------
             */

            booking.setHoldExpiresAt(
                    null
            );


            /*
             * ----------------------------------------------------
             * Save
             * ----------------------------------------------------
             */

            bookingRepository.save(
                    booking
            );
        }
    }


    /*
     * ============================================================
     * GET USER BOOKINGS
     * ============================================================
     */

    @Transactional(readOnly = true)
    public List<BookingResponse> getUserBookings(
            Long userId
    ) {

        List<Booking> bookings =
                bookingRepository
                        .findByUser_UserIdOrderByCreatedAtDesc(
                                userId
                        );


        return bookings.stream()
                .map(booking -> {

                    HotelReservation reservation =
                            hotelReservationRepository
                                    .findByBooking_BookingId(
                                            booking.getBookingId()
                                    )
                                    .orElse(null);

                    /*
                     * Guide, ride and activity bookings have no
                     * room reservation. Throwing here blanked
                     * the whole bookings page for a traveller
                     * whose first booking was a trip extra.
                     */
                    if (reservation == null) {
                        return withoutStay(booking);
                    }

                    return toResponse(
                            booking,
                            reservation,
                            booking.getGuestCount(),
                            reservation.getRoomsBooked(),
                            numberOfNights(
                                    reservation
                            )
                    );
                })
                .toList();
    }


    /*
     * ============================================================
     * GET SINGLE USER BOOKING
     * ============================================================
     */

    @Transactional(readOnly = true)
    public BookingResponse getUserBooking(
            Long userId,
            Long bookingId
    ) {

        Booking booking =
                bookingRepository.findById(
                                bookingId
                        )
                        .orElseThrow(() ->
                                new BookingException(
                                        "Booking not found"
                                )
                        );


        /*
         * Ownership
         */

        if (!booking.getUser()
                .getUserId()
                .equals(userId)) {

            throw new BookingException(
                    "You are not allowed to view this booking"
            );
        }


        /*
         * Find reservation
         */

        HotelReservation reservation =
                hotelReservationRepository
                        .findByBooking_BookingId(
                                bookingId
                        )
                        .orElse(null);

        /*
         * Stay-less bookings (guides, rides, activities):
         * see getUserBookings.
         */
        if (reservation == null) {
            return withoutStay(booking);
        }


        return toResponse(
                booking,
                reservation,
                booking.getGuestCount(),
                reservation.getRoomsBooked(),
                numberOfNights(
                        reservation
                )
        );
    }


    /*
     * ============================================================
     * RELEASE INVENTORY
     * ============================================================
     *
     * Important:
     *
     * This method uses the same pessimistic inventory locking
     * mechanism used during booking.
     */

    private void releaseInventory(
            HotelReservation reservation
    ) {

        List<RoomInventoryDaily> inventory =
                roomInventoryRepository.findForAvailability(
                        reservation
                                .getRoomType()
                                .getRoomTypeId(),

                        reservation.getCheckIn(),

                        reservation.getCheckOut()
                );


        /*
         * Verify all required nights exist.
         */

        if (inventory.size()
                != numberOfNights(
                reservation
        )) {

            throw new BookingException(
                    "Inventory is incomplete for reservation "
                            + reservation.getReservationId()
            );
        }


        /*
         * Release rooms.
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
     * DATE VALIDATION
     * ============================================================
     */

    private void validateDates(
            LocalDate checkIn,
            LocalDate checkOut
    ) {

        if (checkIn == null
                || checkOut == null) {

            throw new BookingException(
                    "Check-in and check-out dates are required"
            );
        }


        if (!checkIn.isBefore(checkOut)) {

            throw new BookingException(
                    "Check-out must be after check-in"
            );
        }


        if (checkIn.isBefore(
                LocalDate.now()
        )) {

            throw new BookingException(
                    "Check-in cannot be in the past"
            );
        }
    }


    /*
     * ============================================================
     * BOOKING REFERENCE
     * ============================================================
     */

    private String generateBookingReference() {

        String reference;

        do {

            reference =
                    "TB-"
                            + UUID.randomUUID()
                            .toString()
                            .replace(
                                    "-",
                                    ""
                            )
                            .substring(
                                    0,
                                    12
                            )
                            .toUpperCase();

        } while (
                bookingRepository
                        .existsByBookingReference(
                                reference
                        )
        );

        return reference;
    }


    /*
     * ============================================================
     * RESPONSE MAPPER
     * ============================================================
     */

    private BookingResponse toResponse(
            Booking booking,
            HotelReservation reservation,
            Integer guests,
            Integer rooms,
            Integer nights
    ) {

        RoomType roomType =
                reservation.getRoomType();


        Long propertyId =
                roomType
                        .getProperty()
                        .getPropertyId();


        String propertyName =
                roomType
                        .getProperty()
                        .getName();


        return new BookingResponse(

                booking.getBookingId(),

                booking.getBookingReference(),

                booking.getUser()
                        .getUserId(),

                propertyId,

                propertyName,

                roomType.getRoomTypeId(),

                roomType.getCategoryName(),

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

    /*
     * ============================================================
     * RESPONSE WITHOUT STAY
     * ============================================================
     *
     * A guide tour, a paid ride and an activity are bookings
     * too, but none of them has a room reservation. The bookings
     * page asks for every booking the traveller owns, so mapping
     * through toResponse() and throwing "Hotel reservation not
     * found" took the whole page down the moment a trip extra
     * was the first thing on it.
     *
     * Stay-only fields are null here; BookingResponse.bookingType
     * says what the booking actually is so the client can label
     * the card.
     */
    private BookingResponse withoutStay(
            Booking booking
    ) {
        return new BookingResponse(

                booking.getBookingId(),

                booking.getBookingReference(),

                booking.getUser()
                        .getUserId(),

                null,

                null,

                null,

                null,

                null,

                null,

                booking.getGuestCount(),

                null,

                null,

                booking.getTotalAmount(),

                booking.getCurrency(),

                booking.getBookingStatus(),

                booking.getPaymentStatus(),

                booking.getHoldExpiresAt(),

                booking.getCreatedAt(),

                booking.getBookingType()
        );
    }
}