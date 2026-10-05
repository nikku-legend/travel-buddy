package com.Travel.Buddy.service.trip;

import com.Travel.Buddy.dto.trip.AddTripSelectionRequest;
import com.Travel.Buddy.dto.trip.CustomiseTripSelectionRequest;
import com.Travel.Buddy.dto.trip.SwapTripSelectionRequest;
import com.Travel.Buddy.dto.trip.TripDetailResponse;
import com.Travel.Buddy.dto.trip.TripSelectionResponse;
import com.Travel.Buddy.entity.Cab;
import com.Travel.Buddy.entity.Guide;
import com.Travel.Buddy.entity.Property;
import com.Travel.Buddy.entity.RoomType;
import com.Travel.Buddy.entity.Trip;
import com.Travel.Buddy.entity.TripCity;
import com.Travel.Buddy.entity.TripSelection;
import com.Travel.Buddy.entity.TripSelectionStatus;
import com.Travel.Buddy.entity.TripSelectionType;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.CabRepository;
import com.Travel.Buddy.repository.GuideRepository;
import com.Travel.Buddy.repository.PropertyRepository;
import com.Travel.Buddy.repository.RoomTypeRepository;
import com.Travel.Buddy.repository.TripCityRepository;
import com.Travel.Buddy.repository.TripSelectionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The trip cart. (SRS 2.2 TP-05 .. TP-08)
 *
 * <p><strong>Nothing selected here is reserved.</strong> That is
 * the defining constraint of the whole planner: two travellers
 * can select the same room and only one of them gets it at
 * checkout. The cart holds intent and a quoted price, nothing
 * more.
 *
 * <p>Quoting is done here so the traveller sees a realistic
 * number while planning, but it is never treated as agreed. The
 * figure the payment is for is recalculated at checkout.
 */
@Service
public class TripCartService {

    private final TripSelectionRepository selectionRepository;
    private final TripCityRepository tripCityRepository;
    private final PropertyRepository propertyRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final GuideRepository guideRepository;
    private final CabRepository cabRepository;
    private final TripService tripService;
    private final TripBillService billService;
    private final TripMilestoneService milestoneService;

    public TripCartService(
            TripSelectionRepository selectionRepository,
            TripCityRepository tripCityRepository,
            PropertyRepository propertyRepository,
            RoomTypeRepository roomTypeRepository,
            GuideRepository guideRepository,
            CabRepository cabRepository,
            TripService tripService,
            TripBillService billService,
            TripMilestoneService milestoneService
    ) {
        this.selectionRepository = selectionRepository;
        this.tripCityRepository = tripCityRepository;
        this.propertyRepository = propertyRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.guideRepository = guideRepository;
        this.cabRepository = cabRepository;
        this.tripService = tripService;
        this.billService = billService;
        this.milestoneService = milestoneService;
    }

    @Transactional
    public TripDetailResponse addSelection(
            Long userId,
            Long tripId,
            AddTripSelectionRequest request
    ) {
        Trip trip = tripService.requireOwned(userId, tripId);
        trip.requireEditable();

        validateDates(trip, request);

        TripCity tripCity = null;
        if (request.tripCityId() != null) {
            tripCity = tripCityRepository
                    .findById(request.tripCityId())
                    .orElseThrow(() ->
                            PartnerApplicationException.notFound(
                                    "Trip stop not found"
                            )
                    );
            if (!tripCity.getTrip()
                    .getTripId()
                    .equals(tripId)) {
                throw PartnerApplicationException.badRequest(
                        "That stop belongs to a different trip"
                );
            }
        }

        /*
         * Re-selecting the same target updates the existing row
         * rather than adding a second. The database also carries
         * a unique key for this, because renumbering first and
         * inserting second can still race under concurrency.
         */
        TripSelection existing = selectionRepository
                .findByTrip_TripIdAndSelectionTypeAndTargetId(
                        tripId,
                        request.selectionType(),
                        request.targetId()
                )
                .orElse(null);

        TripSelection selection = existing == null
                ? new TripSelection(
                trip, tripCity, request.selectionType(),
                request.targetId()
        )
                : existing;

        if (existing != null) {
            existing.setTripCity(tripCity);
        }

        selection.stay(
                request.checkIn(),
                request.checkOut(),
                request.guests()
        );

        RoomType roomType = attachRoomType(
                selection, request
        );

        BigDecimal quote = request.quotedAmount();

        if (quote == null) {
            quote = quoteFor(
                    trip, request.selectionType(),
                    roomType, request
            );
        }

        /*
         * The resolved room count is stored on the selection, not
         * merely used for the quote, so the figure the traveller
         * agreed to survives a later change to the party size.
         */
        if (request.selectionType() == TripSelectionType.HOTEL) {
            selection.setRooms(
                    request.rooms() != null
                            ? request.rooms()
                            : trip.roomsRequired()
            );
        }

        selection.quote(
                quote,
                request.currency() == null
                        ? trip.getCurrency()
                        : request.currency().toUpperCase()
        );

        TripSelection saved = selectionRepository.save(selection);


        /*
         * A room selected in a stop means the traveller will
         * actually sleep there, so that stop earns the stronger
         * check-in and check-out pins. A stop they merely pass
         * through keeps only the plain arrival and departure.
         */
        if (saved.getSelectionType()
                == TripSelectionType.HOTEL) {
            milestoneService.seedStayCheckpoints(
                    trip, saved.getTripCity()
            );
        }
        billService.recalculate(trip);

        return tripService.detailFor(trip);
    }

    private void validateDates(
            Trip trip,
            AddTripSelectionRequest request
    ) {
        if (request.selectionType() != TripSelectionType.HOTEL
                && request.selectionType() != TripSelectionType.CAB) {
            return;
        }

        if (request.checkIn() == null
                || request.checkOut() == null) {
            return;
        }

        if (request.checkOut()
                .isBefore(request.checkIn())) {
            throw PartnerApplicationException.badRequest(
                    "The end of a stay cannot be before its start"
            );
        }

        /*
         * A stay that begins before the trip does is nearly
         * always a typo, and it would otherwise reach checkout
         * where the whole cart is held up by one bad line.
         */
        if (request.selectionType() == TripSelectionType.HOTEL
                && request.checkIn()
                .isBefore(trip.getStartDate())) {
            throw PartnerApplicationException.badRequest(
                    "That check-in is before your trip starts on "
                            + trip.getStartDate()
            );
        }
    }

    private RoomType attachRoomType(
            TripSelection selection,
            AddTripSelectionRequest request
    ) {
        if (request.roomTypeId() == null) {
            return null;
        }

        RoomType roomType = roomTypeRepository
                .findById(request.roomTypeId())
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Room type not found"
                        )
                );

        Property property = roomType.getProperty();

        /*
         * Verifies the room type really belongs to the property
         * being selected. Without it a traveller could attach an
         * arbitrary room type to a hotel and be quoted for it.
         */
        if (property == null
                || !property.getPropertyId()
                .equals(request.targetId())) {
            throw PartnerApplicationException.badRequest(
                    "That room type is not part of this property"
            );
        }

        if (!Boolean.TRUE.equals(property.getVerified())) {
            throw PartnerApplicationException.badRequest(
                    "This property is not bookable"
            );
        }

        selection.setRoomType(roomType);
        return roomType;
    }

    /**
     * A planning-time estimate. Deliberately simple: nights
     * times base rate times rooms, so the number is predictable
     * to the traveller. Anything more elaborate would have to be
     * re-derived at checkout anyway, and a quote that cannot be
     * reproduced is worse than a rough one.
     */
    private BigDecimal quoteFor(
            Trip trip,
            TripSelectionType type,
            RoomType roomType,
            AddTripSelectionRequest request
    ) {
        if (type == TripSelectionType.HOTEL) {
            if (roomType == null
                    || roomType.getBasePrice() == null) {
                return BigDecimal.ZERO;
            }

            int nights = nightsBetween(
                    request.checkIn(), request.checkOut()
            );

            /*
             * The room count, not the guest count, is what is
             * billed. This previously evaluated to 1 for every
             * party, so a family of four was quoted for a single
             * room and the bill came out at roughly half of what
             * they would actually pay.
             */
            int rooms = request.rooms() != null
                    ? request.rooms()
                    : trip.roomsRequired();

            return roomType.getBasePrice()
                    .multiply(BigDecimal.valueOf(
                            (long) Math.max(nights, 0)
                                    * Math.max(rooms, 1)
                    ))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }

        if (type == TripSelectionType.GUIDE) {
            Guide guide = guideRepository
                    .findById(request.targetId())
                    .orElse(null);

            if (guide == null || guide.getDailyRate() == null) {
                return BigDecimal.ZERO;
            }

            /*
             * A guide is charged per day. One day minimum: a
             * same-day tour still costs a day's rate, and a
             * missing or backwards date must not become a free
             * guide.
             */
            int days = Math.max(
                    nightsBetween(
                            request.checkIn(), request.checkOut()
                    ), 1
            );

            return guide.getDailyRate()
                    .multiply(BigDecimal.valueOf(days))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }

        if (type == TripSelectionType.CAB) {
            Cab cab = cabRepository
                    .findById(request.targetId())
                    .orElse(null);

            if (cab == null) {
                return BigDecimal.ZERO;
            }

            /*
             * A price the traveller was given wins, because a cab
             * leg is priced on the route actually driven and the
             * cart has no distance of its own to work from.
             *
             * Failing that the base fare is the honest floor: it
             * under-quotes a long leg, but it never quotes less
             * than the driver will accept to start the car.
             */
            if (request.quotedAmount() != null
                    && request.quotedAmount().signum() > 0) {
                return request.quotedAmount().setScale(
                        2, java.math.RoundingMode.HALF_UP
                );
            }

            if (cab.getBaseFare() == null) {
                return BigDecimal.ZERO;
            }

            return cab.getBaseFare()
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }

    private int nightsBetween(
            LocalDate from,
            LocalDate to
    ) {
        if (from == null || to == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(from, to);
    }

    /* ============================================================
     * 6.2  HOTEL CUSTOMISATION
     *
     * The traveller can change the room type, the number of
     * rooms, or the guests after choosing a hotel. Every one of
     * these re-quotes the selection immediately, so the bill on
     * screen is never stale.
     *
     * None of them accept dates: section 6.2 requires dates to
     * change through the planner so the rest of the itinerary is
     * revalidated. Moving a stay here would silently desync the
     * transport and guide legs.
     * ============================================================ */

    @Transactional
    public TripDetailResponse customiseSelection(
            Long userId,
            Long tripId,
            Long selectionId,
            CustomiseTripSelectionRequest request
    ) {
        Trip trip = tripService.requireOwned(userId, tripId);
        trip.requireEditable();

        TripSelection selection = requireSelection(
                tripId, selectionId
        );

        if (selection.getStatus() == TripSelectionStatus.BOOKED) {
            throw PartnerApplicationException.conflict(
                    "This stay is already booked. Cancel the "
                            + "booking before changing it."
            );
        }

        if (selection.getSelectionType()
                != TripSelectionType.HOTEL) {
            throw PartnerApplicationException.badRequest(
                    "Only a hotel selection can be customised this "
                            + "way"
            );
        }

        if (request.roomTypeId() != null) {
            selection.setRoomType(
                    requireOwnedRoomType(
                            selection, request.roomTypeId()
                    )
            );
        }

        if (request.guests() != null) {
            selection.stay(
                    selection.getCheckIn(),
                    selection.getCheckOut(),
                    request.guests()
            );
        }

        /*
         * The room count is derived from the party size unless the
         * traveller set it explicitly.
         *
         * <p>Re-deriving only when rooms was not supplied is what
         * keeps both promises at once: raising the guest count
         * must not silently leave four people priced for one room,
         * and an explicit choice of rooms must never be
         * overridden by arithmetic.
         */
        if (request.rooms() != null) {
            selection.setRooms(request.rooms());
        } else if (request.guests() != null) {
            int needed = Math.max(
                    1,
                    (int) Math.ceil(
                            request.guests() / 2.0
                    )
            );
            selection.setRooms(needed);
        }

        if (selection.getRooms() == null) {
            selection.setRooms(trip.roomsRequired());
        }

        RoomType roomType = selection.getRoomType();

        if (roomType == null) {
            throw PartnerApplicationException.badRequest(
                    "This stay has no room type to price"
            );
        }

        /*
         * The new room type must still hold the party. A traveller
         * upgrading a stay for more guests into a smaller room
         * would otherwise get a quote for something that cannot
         * sleep them.
         */
        if (selection.getGuests() != null
                && roomType.getMaxOccupancy() != null
                && roomType.getMaxOccupancy()
                < selection.getGuests()) {
            throw PartnerApplicationException.badRequest(
                    roomType.getCategoryName() + " sleeps "
                            + roomType.getMaxOccupancy()
                            + ", which is fewer than the "
                            + selection.getGuests()
                            + " guests on this stay"
            );
        }

        requote(selection, roomType);

        selection.markSelected();
        selectionRepository.save(selection);

        billService.recalculate(trip);

        return tripService.detailFor(trip);
    }

    /**
     * Recomputes a stay's price from its current room type, room
     * count and dates.
     */
    private void requote(
            TripSelection selection,
            RoomType roomType
    ) {
        int nights = selection.nights();
        int rooms = selection.roomsBilled();

        BigDecimal amount = roomType.getBasePrice() == null
                ? BigDecimal.ZERO
                : roomType.getBasePrice().multiply(
                        BigDecimal.valueOf(
                                (long) Math.max(nights, 0)
                                        * rooms
                        )
                ).setScale(2, java.math.RoundingMode.HALF_UP);

        selection.quote(amount, selection.getCurrency());
    }

    /**
     * Verifies a replacement room type really belongs to the
     * property already selected. Without this, customising a stay
     * would let the traveller attach any room type in the system
     * and be quoted for it.
     */
    private RoomType requireOwnedRoomType(
            TripSelection selection,
            Long roomTypeId
    ) {
        RoomType roomType = roomTypeRepository
                .findById(roomTypeId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Room type not found"
                        )
                );

        Long propertyId = selection.getTargetId();
        Long owner = roomType.getProperty() == null
                ? null
                : roomType.getProperty().getPropertyId();

        if (owner == null || !owner.equals(propertyId)) {
            throw PartnerApplicationException.badRequest(
                    "That room type belongs to a different property"
            );
        }

        return roomType;
    }

    private TripSelection requireSelection(
            Long tripId,
            Long selectionId
    ) {
        TripSelection selection = selectionRepository
                .findById(selectionId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Selection not found"
                        )
                );

        if (!selection.getTrip()
                .getTripId()
                .equals(tripId)) {
            throw PartnerApplicationException.forbidden(
                    "That selection is not on this trip"
            );
        }

        return selection;
    }

    /**
     * Replaces one selection with a different service. (6.2)
     *
     * <p>The old selection is marked REMOVED rather than deleted,
     * so the record shows a hotel was proposed, rejected and
     * swapped. Everything else in the trip is left alone, which
     * is what section 6.2 means by "return to the recommendation
     * list without losing the rest of the plan".
     */
    @Transactional
    public TripDetailResponse swapSelection(
            Long userId,
            Long tripId,
            Long selectionId,
            SwapTripSelectionRequest request
    ) {
        Trip trip = tripService.requireOwned(userId, tripId);
        trip.requireEditable();

        TripSelection existing = requireSelection(
                tripId, selectionId
        );

        if (existing.getStatus() == TripSelectionStatus.BOOKED) {
            throw PartnerApplicationException.conflict(
                    "This is already booked. Cancel the booking "
                            + "before replacing it."
            );
        }

        /*
         * One hotel per property per trip. If the replacement is
         * already selected elsewhere, removing that one first
         * avoids colliding with the unique key rather than
         * failing the swap outright.
         */
        selectionRepository
                .findByTrip_TripIdAndSelectionTypeAndTargetId(
                        tripId,
                        request.selectionType(),
                        request.targetId()
                )
                .filter(other -> !other.getSelectionId()
                        .equals(selectionId))
                .ifPresent(other -> {
                    other.remove();
                    selectionRepository.save(other);
                });

        existing.remove();
        selectionRepository.save(existing);

        return addSelection(
                userId, tripId,
                new AddTripSelectionRequest(
                        request.selectionType(),
                        existing.getTripCity() == null
                                ? null
                                : existing.getTripCity()
                                .getTripCityId(),
                        request.targetId(),
                        request.roomTypeId(),

                        /*
                         * Supplied dates win. A swap that silently
                         * kept the old dates would leave a stay on
                         * dates the traveller had moved on from.
                         */
                        request.checkIn() != null
                                ? request.checkIn()
                                : existing.getCheckIn(),
                        request.checkOut() != null
                                ? request.checkOut()
                                : existing.getCheckOut(),
                        request.guests() != null
                                ? request.guests()
                                : existing.getGuests(),
                        request.rooms(),
                        null,
                        request.currency()
                )
        );
    }

    @Transactional
    public TripDetailResponse removeSelection(            Long userId,
            Long tripId,
            Long selectionId
    ) {
        Trip trip = tripService.requireOwned(userId, tripId);
        trip.requireEditable();

        TripSelection selection = selectionRepository
                .findById(selectionId)
                .orElseThrow(() ->
                        PartnerApplicationException.notFound(
                                "Selection not found"
                        )
                );

        if (!selection.getTrip()
                .getTripId()
                .equals(tripId)) {
            throw PartnerApplicationException.forbidden(
                    "That selection is not on this trip"
            );
        }

        if (selection.getBooking() != null) {
            throw PartnerApplicationException.conflict(
                    "This is already booked. Cancel the booking "
                            + "before removing it from the trip."
            );
        }

        selection.remove();
        selectionRepository.save(selection);

        billService.recalculate(trip);

        return tripService.detailFor(trip);
    }
}
