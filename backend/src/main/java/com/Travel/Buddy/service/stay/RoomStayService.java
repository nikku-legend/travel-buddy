package com.Travel.Buddy.service.stay;

import com.Travel.Buddy.dto.stay.PhysicalRoomRequest;
import com.Travel.Buddy.dto.stay.PhysicalRoomResponse;
import com.Travel.Buddy.dto.stay.RoomStayResponse;
import com.Travel.Buddy.entity.*;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Front-desk operations: room assignment and check-in/out.
 * (FR-22, FR-23)
 *
 * <p>Booking reserves a room CATEGORY. This service is where a real
 * numbered room is attached to the reservation and where the guest is
 * actually checked in and out.
 *
 * <p>The one hard rule: <strong>two guests can never be given the
 * same physical room for overlapping dates.</strong> MySQL has no
 * exclusion constraint, so this is enforced by locking the physical
 * room row with {@code PESSIMISTIC_WRITE} before checking overlaps.
 * Locking the room, not the stay, is what makes two concurrent
 * front-desk terminals serialise instead of both seeing a free room.
 */
@Service
public class RoomStayService {

    /**
     * Stay states that still hold a room. A cancelled or completed
     * stay releases it, so those are excluded from overlap checks.
     */
    private static final Set<RoomStayStatus> OCCUPYING =
            Set.of(
                    RoomStayStatus.ASSIGNED,
                    RoomStayStatus.CHECKED_IN
            );

    private final PhysicalRoomRepository physicalRoomRepository;

    private final RoomStayRepository roomStayRepository;

    private final HotelReservationRepository hotelReservationRepository;

    private final BookingRepository bookingRepository;

    private final RoomTypeRepository roomTypeRepository;

    private final PropertyRepository propertyRepository;

    private final com.Travel.Buddy.repository.TripSelectionRepository
            selectionRepository;
    private final com.Travel.Buddy.service.trip.TripMilestoneService
            milestoneService;
    private final com.Travel.Buddy.service.voucher.VoucherService
            voucherService;

    public RoomStayService(
            PhysicalRoomRepository physicalRoomRepository,
            RoomStayRepository roomStayRepository,
            HotelReservationRepository hotelReservationRepository,
            BookingRepository bookingRepository,
            RoomTypeRepository roomTypeRepository,
            PropertyRepository propertyRepository,
            com.Travel.Buddy.service.voucher.VoucherService voucherService,
            com.Travel.Buddy.repository.TripSelectionRepository selectionRepository,
            com.Travel.Buddy.service.trip.TripMilestoneService milestoneService
    ) {
        this.physicalRoomRepository = physicalRoomRepository;
        this.roomStayRepository = roomStayRepository;
        this.hotelReservationRepository = hotelReservationRepository;
        this.bookingRepository = bookingRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.propertyRepository = propertyRepository;
        this.voucherService = voucherService;
        this.selectionRepository = selectionRepository;
        this.milestoneService = milestoneService;
    }

    /* ============================================================
     * PHYSICAL ROOMS (FR-22)
     * ============================================================ */

    @Transactional(readOnly = true)
    public List<PhysicalRoomResponse> listRooms(
            Long partnerId,
            Long propertyId,
            Long roomTypeId,
            LocalDate from,
            LocalDate to
    ) {
        requireOwnedProperty(partnerId, propertyId);

        List<PhysicalRoom> rooms =
                roomTypeId == null
                        ? physicalRoomRepository
                        .findByProperty_PropertyIdOrderByRoomNumberAsc(
                        propertyId
                )
                        : physicalRoomRepository
                        .findByProperty_PropertyIdAndRoomType_RoomTypeIdOrderByRoomNumberAsc(
                        propertyId,
                        roomTypeId
                );

        LocalDate start =
                from == null
                        ? LocalDate.now()
                        : from;

        LocalDate end =
                to == null
                        ? start
                        : to;

        return rooms.stream()
                .map(room -> toRoomResponse(room, start, end))
                .toList();
    }

    @Transactional
    public PhysicalRoomResponse addRoom(
            Long partnerId,
            Long propertyId,
            Long roomTypeId,
            PhysicalRoomRequest request
    ) {
        requireOwnedProperty(partnerId, propertyId);

        RoomType roomType = roomTypeRepository
                .findByRoomTypeIdAndProperty_PropertyId(
                        roomTypeId,
                        propertyId
                )
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Room type not found on this property"
                        )
                );

        String roomNumber = request.roomNumber().trim();

        if (physicalRoomRepository
                .findByProperty_PropertyIdAndRoomNumberIgnoreCase(
                        propertyId,
                        roomNumber
                ).isPresent()) {

            throw PartnerApplicationException.conflict(
                    "Room '" + roomNumber
                            + "' already exists on this property"
            );
        }

        PhysicalRoom room = new PhysicalRoom();

        room.setProperty(
                propertyRepository.findById(propertyId).orElseThrow()
        );
        room.setRoomType(roomType);
        room.setRoomNumber(roomNumber);
        room.setFloorLabel(trim(request.floorLabel()));
        room.setNotes(trim(request.notes()));
        room.setStatus(PhysicalRoomStatus.AVAILABLE);

        return toRoomResponse(
                physicalRoomRepository.save(room),
                LocalDate.now(),
                LocalDate.now()
        );
    }

    /**
     * Takes a room out of service.
     *
     * <p>Refused while the room holds a live stay: marking an occupied
     * room unavailable would strand the guest with no record of where
     * they are.
     */
    @Transactional
    public PhysicalRoomResponse setRoomStatus(
            Long partnerId,
            Long propertyId,
            Long physicalRoomId,
            PhysicalRoomStatus status
    ) {
        PhysicalRoom room = lockOwnedRoom(
                partnerId,
                propertyId,
                physicalRoomId
        );

        if (status == PhysicalRoomStatus.OUT_OF_SERVICE
                && !roomStayRepository
                .findOverlapping(
                        physicalRoomId,
                        OCCUPYING,
                        LocalDate.now(),
                        LocalDate.now().plusYears(1)
                ).isEmpty()) {

            throw PartnerApplicationException.conflict(
                    "This room has a live stay and cannot be taken out of service"
            );
        }

        room.setStatus(status);

        return toRoomResponse(
                physicalRoomRepository.save(room),
                LocalDate.now(),
                LocalDate.now()
        );
    }

    /* ============================================================
     * ASSIGNMENT (FR-22)
     * ============================================================ */

    /**
     * Allocates a physical room to a booking.
     *
     * <p>One room is assigned per room booked, so a three-room booking
     * yields three stays. Each assignment is a separate call, letting
     * the front desk allocate rooms one at a time.
     *
     * <p><b>READ_COMMITTED is load-bearing, not a preference.</b>
     *
     * <p>Under MySQL's default REPEATABLE READ this method was racy
     * even with the PESSIMISTIC_WRITE room lock in place, because of
     * how the two interact:
     *
     * <ol>
     *   <li>Desk A takes the lock on the room row and inserts its
     *       stay, then commits.</li>
     *   <li>Desk B was already blocked waiting for that same lock, so
     *       its transaction -- and therefore its snapshot -- was opened
     *       <em>before</em> A committed.</li>
     *   <li>B acquires the lock, but the overlap check that follows is
     *       a plain consistent read, so it reads its own stale snapshot
     *       and does not see A's stay.</li>
     *   <li>B concludes the room is free and inserts a second one.</li>
     * </ol>
     *
     * <p>The lock was doing its job; the decision made under it was not
     * reading current data. READ_COMMITTED closes that gap: every read
     * in this transaction sees the latest committed state, so B always
     * observes A's stay and is refused.
     *
     * <p>Proven by PartnerMySqlE2ETest, which reproduced the
     * double-booking on real MySQL 8 until this was changed. H2 could
     * never have caught it: its MVCC is not InnoDB's.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomStayResponse assign(
            Long partnerId,
            Long bookingId,
            Long physicalRoomId
    ) {
        HotelReservation reservation =
                requireOwnedReservation(partnerId, bookingId);

        Booking booking = reservation.getBooking();

        if (booking.getBookingStatus() == BookingStatus.CANCELLED
                || booking.getBookingStatus() == BookingStatus.COMPLETED) {

            throw PartnerApplicationException.conflict(
                    "Rooms cannot be assigned to a "
                            + booking.getBookingStatus()
                            + " booking"
            );
        }

        PhysicalRoom room = lockOwnedRoom(
                partnerId,
                reservation.getRoomType()
                        .getProperty()
                        .getPropertyId(),
                physicalRoomId
        );

        requireMatchingRoomType(room, reservation);

        if (!room.isAssignable()) {

            throw PartnerApplicationException.conflict(
                    "Room " + room.getRoomNumber()
                            + " is out of service"
            );
        }

        assertNoOverlap(
                room.getPhysicalRoomId(),
                reservation.getCheckIn(),
                reservation.getCheckOut()
        );

        RoomStay stay = new RoomStay();

        stay.setBooking(booking);
        stay.setPhysicalRoom(room);
        stay.setCheckInDate(reservation.getCheckIn());
        stay.setCheckOutDate(reservation.getCheckOut());
        stay.setStatus(RoomStayStatus.ASSIGNED);
        stay.setGuestName(booking.getGuestName());
        stay.setAssignedBy(booking.getUser());

        RoomStay saved = roomStayRepository.save(stay);

        syncAssignedRoomNumbers(reservation, saved);

        return toStayResponse(saved);
    }

    /**
     * Withdraws an assignment that never became a stay.
     *
     * <p>Refused once the guest has checked in: releasing the room then
     * would lose the record of where they are.
     */
    @Transactional
    public RoomStayResponse unassign(
            Long partnerId,
            Long stayId
    ) {
        RoomStay stay = roomStayRepository
                .findByIdForUpdate(stayId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Stay not found"
                        )
                );

        requireStayOwnership(partnerId, stay);

        if (stay.getStatus() == RoomStayStatus.CHECKED_IN) {

            throw PartnerApplicationException.conflict(
                    "The guest has already checked in. Check them out instead."
            );
        }

        if (!stay.getStatus().isOpen()) {

            throw PartnerApplicationException.conflict(
                    "This stay is already closed"
            );
        }

        stay.setStatus(RoomStayStatus.CANCELLED);
        roomStayRepository.save(stay);

        HotelReservation reservation =
                hotelReservationRepository.findByBooking_BookingId(
                        stay.getBooking().getBookingId()
                ).orElse(null);

        if (reservation != null) {
            syncAssignedRoomNumbers(reservation, stay);
        }

        return toStayResponse(stay);
    }

    @Transactional(readOnly = true)
    public List<RoomStayResponse> staysForBooking(
            Long partnerId,
            Long bookingId
    ) {
        requireOwnedReservation(partnerId, bookingId);

        return roomStayRepository
                .findByBooking_BookingIdOrderByStayIdAsc(bookingId)
                .stream()
                .map(this::toStayResponse)
                .toList();
    }

    /**
     * Rooms the front desk must act on: assigned but not yet checked
     * in, and currently checked in.
     */
    @Transactional(readOnly = true)
    public List<RoomStayResponse> frontDeskQueue(
            Long partnerId
    ) {
        return roomStayRepository
                .findByPartnerAndStatuses(partnerId, OCCUPYING)
                .stream()
                .map(this::toStayResponse)
                .toList();
    }


    /* ============================================================
     * CHECK-IN / CHECK-OUT (FR-23)
     * ============================================================ */

    /**
     * Checks a guest in.
     *
     * <p>Also promotes the booking to CHECKED_IN the first time any of
     * its rooms is checked in, so the traveler-facing "My Trips" view
     * reflects the real state rather than only a stay row.
     */
    @Transactional
    public RoomStayResponse checkIn(
            Long partnerId,
            Long stayId
    ) {
        RoomStay stay = roomStayRepository
                .findByIdForUpdate(stayId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Stay not found"
                        )
                );

        requireStayOwnership(partnerId, stay);

        Booking booking = stay.getBooking();

        if (stay.getStatus() == RoomStayStatus.CHECKED_IN) {

            throw PartnerApplicationException.conflict(
                    "This guest is already checked in"
            );
        }

        if (stay.getStatus() != RoomStayStatus.ASSIGNED) {

            throw PartnerApplicationException.conflict(
                    "Only an assigned room can be checked in, not a "
                            + stay.getStatus() + " stay"
            );
        }

        /*
         * Payment gates arrival. Without this, an unpaid or
         * payment-pending booking could be checked in and the guest
         * would occupy a room nobody has paid for.
         */
        if (booking.getBookingStatus() == BookingStatus.PENDING) {

            throw PartnerApplicationException.conflict(
                    "This booking has not been paid yet, so the guest cannot be checked in"
            );
        }

        if (booking.getBookingStatus() == BookingStatus.CANCELLED
                || booking.getBookingStatus() == BookingStatus.COMPLETED
                || booking.getBookingStatus() == BookingStatus.NO_SHOW) {

            throw PartnerApplicationException.conflict(
                    "A " + booking.getBookingStatus()
                            + " booking cannot be checked in"
            );
        }

        stay.setStatus(RoomStayStatus.CHECKED_IN);
        stay.setCheckedInAt(LocalDateTime.now());

        roomStayRepository.save(stay);

        booking.setBookingStatus(BookingStatus.CHECKED_IN);
        bookingRepository.save(booking);

        advanceTripMap(booking, TripMilestoneType.CHECKED_IN);

        /*
         * Consume the voucher so the same QR cannot admit a second
         * guest. A booking without a voucher (cancelled, or issued
         * before vouchers existed) still checks in normally, so this
         * is additive rather than a new gate.
         */
        voucherService.consumeForStay(booking.getBookingId(), stay);

        return toStayResponse(stay);
    }

    /**
     * Checks a guest out.
     *
     * <p>The booking is only completed once every room for it has been
     * checked out, so a multi-room booking cannot be marked finished
     * while the second guest is still in house.
     */
    @Transactional
    public RoomStayResponse checkOut(
            Long partnerId,
            Long stayId
    ) {
        RoomStay stay = roomStayRepository
                .findByIdForUpdate(stayId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Stay not found"
                        )
                );

        requireStayOwnership(partnerId, stay);

        if (stay.getStatus() != RoomStayStatus.CHECKED_IN) {

            throw PartnerApplicationException.conflict(
                    "Only a checked-in guest can be checked out"
            );
        }

        stay.setStatus(RoomStayStatus.CHECKED_OUT);
        stay.setCheckedOutAt(LocalDateTime.now());

        roomStayRepository.save(stay);

        Booking booking = stay.getBooking();

        boolean anyStillOpen =
                !roomStayRepository.findByBooking_BookingIdAndStatusIn(
                                booking.getBookingId(),
                                OCCUPYING
                        ).isEmpty();

        if (!anyStillOpen
                && (booking.getBookingStatus()
                == BookingStatus.CHECKED_IN
                || booking.getBookingStatus()
                == BookingStatus.CONFIRMED)) {

            booking.setBookingStatus(BookingStatus.COMPLETED);
            bookingRepository.save(booking);
        advanceTripMap(booking, TripMilestoneType.CHECKED_OUT);
        }

        return toStayResponse(stay);
    }

    /**
     * Records that the guest never arrived.
     *
     * <p>This is the single most expensive mistake a hotel makes, so
     * it is an explicit, recorded action rather than something that
     * happens by silence.
     */
    @Transactional
    public RoomStayResponse markNoShow(
            Long partnerId,
            Long stayId
    ) {
        RoomStay stay = roomStayRepository
                .findByIdForUpdate(stayId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Stay not found"
                        )
                );

        requireStayOwnership(partnerId, stay);

        if (stay.getStatus() != RoomStayStatus.ASSIGNED) {

            throw PartnerApplicationException.conflict(
                    "Only an assigned room can be marked as a no-show, not a "
                            + stay.getStatus() + " stay"
            );
        }

        stay.setStatus(RoomStayStatus.NO_SHOW);
        roomStayRepository.save(stay);

        Booking booking = stay.getBooking();

        boolean anyStillOpen =
                !roomStayRepository.findByBooking_BookingIdAndStatusIn(
                                booking.getBookingId(),
                                OCCUPYING
                        ).isEmpty();

        if (!anyStillOpen) {

            booking.setBookingStatus(BookingStatus.NO_SHOW);
            bookingRepository.save(booking);
        }

        return toStayResponse(stay);
    }


    /* ============================================================
     * GUARDS
     * ============================================================ */

    /**
     * The core safety check: refuses a room that already hosts a stay
     * on any of these dates.
     *
     * <p>Must be called while holding the physical room's
     * {@code PESSIMISTIC_WRITE} lock, otherwise two terminals can both
     * read "free" and both assign.
     */
    private void assertNoOverlap(
            Long physicalRoomId,
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        List<RoomStay> conflicts =
                roomStayRepository.findOverlapping(
                        physicalRoomId,
                        OCCUPYING,
                        checkIn,
                        checkOut
                );

        if (conflicts.isEmpty()) {
            return;
        }

        RoomStay conflict = conflicts.get(0);

        throw PartnerApplicationException.conflict(
                "Room " + conflict.getPhysicalRoom()
                        .getRoomNumber()
                        + " is already occupied from "
                        + conflict.getCheckInDate()
                        + " to " + conflict.getCheckOutDate()
        );
    }

    /**
     * A booking for two Deluxe rooms must not be given a Standard
     * room: the guest paid for a category.
     */
    private void requireMatchingRoomType(
            PhysicalRoom room,
            HotelReservation reservation
    ) {

        if (!room.getRoomType()
                .getRoomTypeId()
                .equals(reservation.getRoomType()
                        .getRoomTypeId())) {

            throw PartnerApplicationException.badRequest(
                    "Room " + room.getRoomNumber()
                            + " is a "
                            + room.getRoomType()
                            .getCategoryName()
                            + " but this booking is for a "
                            + reservation.getRoomType()
                            .getCategoryName()
            );
        }
    }

    private Property requireOwnedProperty(
            Long partnerId,
            Long propertyId
    ) {
        Property property = propertyRepository
                .findById(propertyId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Property not found"
                        )
                );

        if (!property.getPartner()
                .getUserId()
                .equals(partnerId)) {

            throw PartnerApplicationException.forbidden(
                    "You do not own this property"
            );
        }

        return property;
    }

    private PhysicalRoom lockOwnedRoom(
            Long partnerId,
            Long propertyId,
            Long physicalRoomId
    ) {
        PhysicalRoom room =
                physicalRoomRepository.findByIdForUpdate(
                        physicalRoomId
                ).orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Physical room not found"
                        )
                );

        if (!room.getProperty()
                .getPropertyId()
                .equals(propertyId)
                || !room.getProperty()
                .getPartner()
                .getUserId()
                .equals(partnerId)) {

            throw PartnerApplicationException.forbidden(
                    "You do not own this room"
            );
        }

        return room;
    }

    private HotelReservation requireOwnedReservation(
            Long partnerId,
            Long bookingId
    ) {
        HotelReservation reservation =
                hotelReservationRepository
                        .findByBooking_BookingId(bookingId)
                        .orElseThrow(() ->
                                PartnerApplicationException.notFound(
                                        "Reservation not found for this booking"
                                )
                        );

        Long ownerId = reservation.getRoomType()
                .getProperty()
                .getPartner()
                .getUserId();

        if (!ownerId.equals(partnerId)) {

            throw PartnerApplicationException.forbidden(
                    "This booking belongs to another property"
            );
        }

        return reservation;
    }

    private void requireStayOwnership(
            Long partnerId,
            RoomStay stay
    ) {
        Long ownerId = stay.getPhysicalRoom()
                .getProperty()
                .getPartner()
                .getUserId();

        if (!ownerId.equals(partnerId)) {

            throw PartnerApplicationException.forbidden(
                    "This stay belongs to another property"
            );
        }
    }

    /**
     * Mirrors live room numbers onto the reservation.
     *
     * <p>{@code hotel_reservations.assigned_room_numbers} is a
     * pre-existing free-text column that the traveler-facing booking
     * view already reads, so it is kept in step rather than left to
     * drift.
     */
    private void syncAssignedRoomNumbers(
            HotelReservation reservation,
            RoomStay justChanged
    ) {
        String numbers = roomStayRepository
                .findByBooking_BookingIdOrderByStayIdAsc(
                        reservation.getBooking()
                                .getBookingId()
                )
                .stream()
                .filter(stay -> stay.getStatus().occupiesRoom())
                .map(stay ->
                        stay.getPhysicalRoom().getRoomNumber())
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);

        reservation.setAssignedRoomNumbers(numbers);
        hotelReservationRepository.save(reservation);
    }


    /* ============================================================
     * MAPPERS
     * ============================================================ */

    private PhysicalRoomResponse toRoomResponse(
            PhysicalRoom room,
            LocalDate from,
            LocalDate to
    ) {
        boolean occupied =
                !roomStayRepository.findOverlapping(
                                room.getPhysicalRoomId(),
                                OCCUPYING,
                                from,
                                to
                        ).isEmpty();

        return new PhysicalRoomResponse(
                room.getPhysicalRoomId(),
                room.getProperty().getPropertyId(),
                room.getRoomType().getRoomTypeId(),
                room.getRoomType().getCategoryName(),
                room.getRoomNumber(),
                room.getFloorLabel(),
                room.getStatus(),
                room.getNotes(),
                room.isAssignable() && !occupied,
                occupied
        );
    }

    private RoomStayResponse toStayResponse(RoomStay stay) {
        RoomStayStatus status = stay.getStatus();

        String blocked =
                switch (status) {
                    case CHECKED_IN ->
                            "Already checked in";
                    case CHECKED_OUT ->
                            "Stay is complete";
                    case CANCELLED ->
                            "Assignment was withdrawn";
                    case NO_SHOW ->
                            "Guest was marked as a no-show";
                    default ->
                            null;
                };

        return new RoomStayResponse(
                stay.getStayId(),
                stay.getBooking().getBookingId(),
                stay.getBooking().getBookingReference(),
                stay.getPhysicalRoom().getPhysicalRoomId(),
                stay.getPhysicalRoom().getRoomNumber(),
                stay.getPhysicalRoom().getFloorLabel(),
                stay.getPhysicalRoom()
                        .getRoomType()
                        .getRoomTypeId(),
                stay.getPhysicalRoom()
                        .getRoomType()
                        .getCategoryName(),
                stay.getCheckInDate(),
                stay.getCheckOutDate(),
                status,
                stay.getGuestName(),
                stay.getCheckedInAt(),
                stay.getCheckedOutAt(),
                stay.getNotes(),
                status == RoomStayStatus.ASSIGNED,
                status == RoomStayStatus.CHECKED_IN,
                blocked
        );
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
    /**
     * Ticks the matching checkpoint on the journey this booking
     * belongs to, if it belongs to one.
     *
     * <p>Most hotel bookings are made directly from a property page
     * and belong to no trip at all. That is not a failure: the
     * map is a record of a journey, and there is no journey to
     * record.
     *
     * <p>The checkpoint is marked only after the stay has already
     * been persisted, so the map can never tick ahead of reality.
     */
    private void advanceTripMap(
            Booking booking,
            TripMilestoneType type
    ) {
        if (booking == null || booking.getBookingId() == null) {
            return;
        }

        selectionRepository
                .findByBooking_BookingId(booking.getBookingId())
                .ifPresent(selection -> {
                    Trip trip = selection.getTrip();

                    if (trip != null) {
                        milestoneService.complete(
                                trip, type, selection.getTripCity()
                        );
                    }
                });
    }
}
