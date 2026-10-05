package com.Travel.Buddy.service.stay;

import com.Travel.Buddy.dto.stay.PartnerReservationResponse;
import com.Travel.Buddy.entity.Booking;
import com.Travel.Buddy.entity.BookingStatus;
import com.Travel.Buddy.entity.HotelReservation;
import com.Travel.Buddy.entity.PaymentStatus;
import com.Travel.Buddy.entity.RoomStay;
import com.Travel.Buddy.entity.RoomStayStatus;
import com.Travel.Buddy.repository.BookingVoucherRepository;
import com.Travel.Buddy.repository.HotelReservationRepository;
import com.Travel.Buddy.repository.RoomStayRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The partner's view of its own arrivals. (FR-22)
 *
 * <p>Complements {@link RoomStayService}, which answers "who already has
 * a room". This answers "who is still owed one", which was the gap:
 * {@code GET /front-desk} only returns stays that already exist, so a
 * partner could never see a confirmed booking that had not yet been
 * assigned a physical room.
 *
 * <p>Counts every room as one assignment. A stay in two rooms is two
 * assignments, not one booking, and treating it as one would let the
 * front desk mark a half-arranged booking complete.
 */
@Service
public class PartnerReservationService {

    /**
     * Bookings still in play. Cancelled, completed and no-show rows are
     * excluded: they need no action, and leaving them in would mean the
     * pending count never returned to zero.
     */
    private static final Set<BookingStatus> OPEN_STATUSES =
            Set.of(
                    BookingStatus.PENDING,
                    BookingStatus.CONFIRMED,
                    BookingStatus.CHECKED_IN
            );

    /**
     * Statuses that still occupy a room. A cancelled stay releases its
     * room, so it must not count towards what this guest was given.
     */
    private static final Set<RoomStayStatus> OCCUPYING =
            Set.of(
                    RoomStayStatus.ASSIGNED,
                    RoomStayStatus.CHECKED_IN
            );

    private final HotelReservationRepository reservationRepository;

    private final RoomStayRepository roomStayRepository;

    private final BookingVoucherRepository voucherRepository;

    public PartnerReservationService(
            HotelReservationRepository reservationRepository,
            RoomStayRepository roomStayRepository,
            BookingVoucherRepository voucherRepository
    ) {
        this.reservationRepository = reservationRepository;
        this.roomStayRepository = roomStayRepository;
        this.voucherRepository = voucherRepository;
    }

    /**
     * Reservations the partner still has to act on, soonest first.
     */
    @Transactional(readOnly = true)
    public List<PartnerReservationResponse> listOpenReservations(
            Long partnerId
    ) {
        List<HotelReservation> reservations =
                reservationRepository.findForPartner(
                        partnerId,
                        OPEN_STATUSES
                );

        if (reservations.isEmpty()) {
            return List.of();
        }

        return summarise(reservations);
    }

    /**
     * Shared by the list and the per-property list, so both read
     * inventory the same way.
     */
    private List<PartnerReservationResponse> summarise(
            List<HotelReservation> reservations
    ) {
        List<Long> bookingIds = reservations.stream()
                .map((r) -> r.getBooking().getBookingId())
                .distinct()
                .toList();

        /*
         * One query for every booking's stays rather than one lookup per
         * reservation. The obvious implementation is a per-row call,
         * which is an N+1 that degrades exactly as the partner's business
         * grows — the wrong direction for the screen meant to be the
         * fastest one in the hotel's day.
         */
        Map<Long, List<RoomStay>> staysByBooking =
                roomStayRepository
                        .findByBooking_BookingIdIn(bookingIds)
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        (s) -> s.getBooking()
                                                .getBookingId()
                                )
                        );

        Map<Long, String> vouchers = voucherRepository
                .findByBooking_BookingIdIn(bookingIds)
                .stream()
                .collect(
                        Collectors.toMap(
                                (v) -> v.getBooking().getBookingId(),
                                (v) -> v.getVoucherCode(),
                                (first, later) -> first
                        )
                );

        return reservations.stream()
                .map((r) -> toResponse(
                        r,
                        staysByBooking.getOrDefault(
                                r.getBooking().getBookingId(),
                                List.of()
                        ),
                        vouchers.get(r.getBooking().getBookingId())
                ))
                .toList();
    }


    private PartnerReservationResponse toResponse(
            HotelReservation reservation,
            List<RoomStay> allStays,
            String voucherCode
    ) {
        Booking booking = reservation.getBooking();

        LocalDate checkIn = reservation.getCheckIn();
        LocalDate checkOut = reservation.getCheckOut();

        int roomsBooked =
                reservation.getRoomsBooked() == null
                        ? 1
                        : reservation.getRoomsBooked();

        List<RoomStay> live = allStays.stream()
                .filter((s) -> OCCUPYING.contains(s.getStatus()))
                .toList();

        int outstanding = Math.max(0, roomsBooked - live.size());

        boolean checkedIn = booking.getBookingStatus()
                == BookingStatus.CHECKED_IN;

        /*
         * Payment gates arrival. Without this, an unpaid or payment-pending
         * booking could be checked in and the guest would occupy a room
         * nobody has paid for.
         */
        boolean paid =
                booking.getPaymentStatus() == PaymentStatus.PAID;

        /*
         * Check-in is offered only while there is still something to do:
         * a room that is assigned but not yet occupied.
         *
         * <p>This previously required the booking to be CHECKED_IN and every
         * stay to be CHECKED_IN — which made the flag true only once arrival
         * had already happened, i.e. exactly when RoomStayService.checkIn
         * rejects with "This guest is already checked in". The portal
         * offered an action that could not succeed, and hid the one that
         * could.
         *
         * <p>{@code outstanding == 0} mirrors checkIn's requirement that the
         * guest actually has a room: checking in half a booking would
         * strand the un-arrived rooms against the guest's departure.
         *
         * <p>{@code paid} is stricter than RoomStayService, which gates on
         * booking status alone. Refusing an action the backend would allow
         * costs the partner one extra click; offering one it would refuse
         * costs them their trust in the screen.
         */
        boolean canCheckIn =
                !checkedIn
                        && paid
                        && outstanding == 0
                        && live.stream().anyMatch(
                                (s) -> s.getStatus() == RoomStayStatus.ASSIGNED
                        );

        return new PartnerReservationResponse(
                booking.getBookingId(),
                booking.getBookingReference(),
                reservation.getRoomType()
                        .getProperty().getPropertyId(),
                reservation.getRoomType()
                        .getProperty().getName(),
                reservation.getRoomType().getRoomTypeId(),
                reservation.getRoomType().getCategoryName(),
                checkIn,
                checkOut,
                nightsBetween(checkIn, checkOut),
                roomsBooked,
                live.size(),
                roomNumbersOf(live),
                booking.getGuestName(),
                booking.getGuestCount(),
                booking.getGuestPhone(),
                booking.getSpecialRequests(),
                booking.getBookingStatus(),
                booking.getPaymentStatus(),
                booking.getTotalAmount(),
                booking.getCurrency(),
                voucherCode,
                outstanding,
                outstanding > 0,
                canCheckIn,
                live.stream().anyMatch(
                        (s) -> s.getStatus() == RoomStayStatus.CHECKED_IN
                ),
                live.stream().anyMatch(
                        (s) -> s.getStatus() == RoomStayStatus.ASSIGNED
                ),
                outstanding == 0
        );
    }

    /**
     * Nights actually slept in.
     *
     * <p>A same-day booking is one night, not zero: the guest still
     * occupies the room and the front desk still has to prepare it.
     * Counting it as zero would print "0 nights" on the one booking most
     * likely to be a same-day arrival.
     */
    private long nightsBetween(
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        if (checkIn == null || checkOut == null) {
            return 0L;
        }

        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);

        return nights < 1 ? 1L : nights;
    }

    private String roomNumbersOf(List<RoomStay> stays) {
        List<String> numbers = stays.stream()
                .map((s) -> s.getPhysicalRoom().getRoomNumber())
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        return numbers.isEmpty() ? null : String.join(", ", numbers);
    }
}
