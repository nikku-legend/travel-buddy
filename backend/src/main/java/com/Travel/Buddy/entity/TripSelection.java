package com.Travel.Buddy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * A service the traveller intends to buy. (SRS 2.2 TP-08)
 *
 * <p><strong>This entity reserves nothing.</strong> That is the
 * whole point of the planner: SELECTED is not BOOKED. A room can
 * be selected by two travellers and only one of them will get it
 * at checkout. Inventory is touched exclusively by
 * TripCheckoutService.
 *
 * <p>{@code quotedAmount} is frozen at selection time. Prices
 * move, but the bill must show what the traveller agreed to, so a
 * later change is written as a new figure in trip_bill_items
 * rather than mutating this.
 */
@Entity
@Table(
        name = "trip_selections",
        indexes = {
                @Index(
                        name = "idx_trip_selections_trip",
                        columnList = "trip_id, status"
                ),
                @Index(
                        name = "idx_trip_selections_type",
                        columnList = "selection_type, status"
                )
        }
)
public class TripSelection {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long selectionId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "trip_id",
            nullable = false
    )
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_city_id")
    private TripCity tripCity;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_type", nullable = false)
    private TripSelectionType selectionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripSelectionStatus status = TripSelectionStatus.SELECTED;

    /**
     * Polymorphic: a property, guide, cab or place id depending on
     * selectionType. Intentionally not a foreign key so a
     * recommendation can outlive a listing that was deactivated,
     * and the traveller can be told why it is no longer available
     * instead of watching a row disappear.
     */
    @Column(
            name = "target_id",
            nullable = false
    )
    private Long targetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id")
    private RoomType roomType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private TouristPlace place;

    @Column(name = "assigned_room_number", length = 20)
    private String assignedRoomNumber;

    @Column(name = "check_in")
    private LocalDate checkIn;

    @Column(name = "check_out")
    private LocalDate checkOut;

    @Column(name = "guests")
    private Integer guests;

    /**
     * How many rooms this stay needs. (SRS 2.3 section 6.2)
     *
     * <p>Stored rather than derived from the guest count. The
     * traveller may choose more rooms than the party strictly
     * needs, and that choice is part of what they agreed to pay
     * for, so it must not be silently recomputed later.
     */
    @Column(name = "rooms")
    private Integer rooms;

    @Column(
            name = "quoted_amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal quotedAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(
            name = "unavailability_reason",
            length = 500
    )
    private String unavailabilityReason;

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private LocalDateTime updatedAt;

    protected TripSelection() {
    }

    public TripSelection(
            Trip trip,
            TripCity tripCity,
            TripSelectionType selectionType,
            Long targetId
    ) {
        this.trip = trip;
        this.tripCity = tripCity;
        this.selectionType = selectionType;
        this.targetId = targetId;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = now();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(
                ChronoUnit.MICROS
        );
    }

    /* ============================================================
     * BEHAVIOUR
     * ============================================================ */

    public void stay(
            LocalDate checkIn,
            LocalDate checkOut,
            Integer guests
    ) {
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.guests = guests;
    }

    public void quote(
            BigDecimal amount,
            String currency
    ) {
        this.quotedAmount = amount;
        this.currency = currency;
    }

    /**
     * Re-points an existing selection at a different stop.
     *
     * <p>A plain setter rather than a behaviour method: this is
     * the traveller correcting their own cart, not a state
     * transition, and there is no invariant to protect.
     */
    public void setTripCity(TripCity tripCity) {
        this.tripCity = tripCity;
    }

    public void readyForCheckout() {
        this.status = TripSelectionStatus.READY_FOR_CHECKOUT;
        this.unavailabilityReason = null;
    }

    /**
     * Returns a prepared selection to the plain selected state,
     * used when a checkout attempt is abandoned and the traveller
     * wants to try again.
     */
    public void markSelected() {
        this.status = TripSelectionStatus.SELECTED;
        this.unavailabilityReason = null;
    }

    public void markBooked(Booking booking) {
        this.booking = booking;
        this.status = TripSelectionStatus.BOOKED;
        if (booking != null
                && booking.getBookingReference() != null
                && assignedRoomNumber == null) {
            /* Room is assigned by the partner, not here. */
        }
    }

    /**
     * Records why checkout refused. The row is kept so the
     * traveller sees the affected line item, which SRS 2.2
     * section 6 requires before they re-confirm.
     */
    public void markUnavailable(String reason) {
        this.status = TripSelectionStatus.UNAVAILABLE;
        this.unavailabilityReason = reason;
    }

    public void remove() {
        this.status = TripSelectionStatus.REMOVED;
    }

    public boolean countsTowardBill() {
        return status.countsTowardBill();
    }

    public int nights() {
        if (checkIn == null || checkOut == null) {
            return 0;
        }
        return (int) java.time.temporal.ChronoUnit.DAYS.between(
                checkIn, checkOut
        );
    }

    /* ============================================================
     * ACCESSORS
     * ============================================================ */

    public Long getSelectionId() {
        return selectionId;
    }

    public Trip getTrip() {
        return trip;
    }

    public TripCity getTripCity() {
        return tripCity;
    }

    public TripSelectionType getSelectionType() {
        return selectionType;
    }

    public TripSelectionStatus getStatus() {
        return status;
    }

    public Long getTargetId() {
        return targetId;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    public void setRoomType(RoomType roomType) {
        this.roomType = roomType;
    }

    public TouristPlace getPlace() {
        return place;
    }

    public void setPlace(TouristPlace place) {
        this.place = place;
    }

    public String getAssignedRoomNumber() {
        return assignedRoomNumber;
    }

    public void setAssignedRoomNumber(String assignedRoomNumber) {
        this.assignedRoomNumber = assignedRoomNumber;
    }

    public LocalDate getCheckIn() {
        return checkIn;
    }

    public LocalDate getCheckOut() {
        return checkOut;
    }

    public Integer getGuests() {
        return guests;
    }

    public Integer getRooms() {
        return rooms;
    }

    /**
     * Records the room count. Null is allowed because an
     * activity or transport selection has no rooms.
     */
    public void setRooms(Integer rooms) {
        if (rooms != null && rooms < 1) {
            throw new IllegalArgumentException(
                    "A stay needs at least one room"
            );
        }
        this.rooms = rooms;
    }

    /**
     * Rooms billed for a stay. Falls back to one so a selection
     * predating the column still prices sensibly.
     */
    public int roomsBilled() {
        return rooms == null ? 1 : rooms;
    }

    public BigDecimal getQuotedAmount() {
        return quotedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public Booking getBooking() {
        return booking;
    }

    public String getUnavailabilityReason() {
        return unavailabilityReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}