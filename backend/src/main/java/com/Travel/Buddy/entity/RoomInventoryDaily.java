package com.Travel.Buddy.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(
        name = "room_inventory_daily",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_room_inventory_room_date",
                        columnNames = {
                                "room_type_id",
                                "inventory_date"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_room_inventory_room_date",
                        columnList = "room_type_id, inventory_date"
                ),
                @Index(
                        name = "idx_room_inventory_date",
                        columnList = "inventory_date"
                )
        }
)
public class RoomInventoryDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_id")
    private Long inventoryId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "room_type_id",
            nullable = false
    )
    private RoomType roomType;

    @Column(
            name = "inventory_date",
            nullable = false
    )
    private LocalDate inventoryDate;

    @Column(
            name = "total_inventory",
            nullable = false
    )
    private Integer totalInventory;

    @Column(
            name = "reserved_inventory",
            nullable = false
    )
    private Integer reservedRooms = 0;

    /**
     * Rooms deliberately withheld from sale by the partner. (FR-21)
     *
     * <p>Kept separate from {@code reservedRooms} because reserved
     * means a guest has paid, while blocked means nobody may book.
     * Conflating them would make a maintenance closure look like an
     * occupancy figure and corrupt commission reporting.
     *
     * <p>Enforced by the database:
     * {@code available + reserved + blocked = total}.
     */
    @Column(
            name = "blocked_inventory",
            nullable = false
    )
    private Integer blockedRooms = 0;

    @Column(
            name = "available_inventory",
            nullable = false
    )
    private Integer availableInventory;

    public RoomInventoryDaily() {
    }

    public Long getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(
            Long inventoryId
    ) {
        this.inventoryId = inventoryId;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    public void setRoomType(
            RoomType roomType
    ) {
        this.roomType = roomType;
    }

    public LocalDate getInventoryDate() {
        return inventoryDate;
    }

    public void setInventoryDate(
            LocalDate inventoryDate
    ) {
        this.inventoryDate = inventoryDate;
    }

    public Integer getTotalInventory() {
        return totalInventory;
    }

    public void setTotalInventory(
            Integer totalInventory
    ) {
        this.totalInventory = totalInventory;
    }

    public Integer getReservedRooms() {
        return reservedRooms;
    }

    public void setReservedRooms(
            Integer reservedRooms
    ) {
        this.reservedRooms = reservedRooms;
    }

    public Integer getAvailableInventory() {
        return availableInventory;
    }

    public void setAvailableInventory(
            Integer availableInventory
    ) {
        this.availableInventory = availableInventory;
    }

    /**
     * Keeps the nightly figures consistent.
     *
     * <p>The database enforces
     * {@code available + reserved + blocked = total}, so this is the
     * only place that relationship is derived. (FR-21)
     */
    public void recalculateAvailableInventory() {

        int total =
                totalInventory == null
                        ? 0
                        : totalInventory;

        int reserved =
                reservedRooms == null
                        ? 0
                        : reservedRooms;

        int blocked =
                blockedRooms == null
                        ? 0
                        : blockedRooms;

        /*
         * An over-committed night (reserved or blocked beyond what the
         * partner physically owns) clamps to zero rather than going
         * negative, which would violate chk_inventory_available.
         */
        availableInventory =
                Math.max(
                        total - reserved - blocked,
                        0
                );
    }

    /**
     * Rooms that may still be blocked on this night.
     */
    public int blockableRooms() {
        return availableInventory == null
                ? 0
                : Math.max(availableInventory, 0);
    }

    /*
     * Reserve rooms for this date.
     */
    public void reserveRooms(
            int rooms
    ) {

        if (rooms <= 0) {
            throw new IllegalArgumentException(
                    "Rooms to reserve must be greater than zero"
            );
        }

        int available =
                availableInventory == null
                        ? 0
                        : availableInventory;

        if (available < rooms) {
            throw new IllegalStateException(
                    "Not enough room inventory available"
            );
        }

        reservedRooms =
                (reservedRooms == null
                        ? 0
                        : reservedRooms)
                        + rooms;

        recalculateAvailableInventory();
    }

    /*
     * Release previously reserved rooms.
     */
    public void releaseRooms(
            int rooms
    ) {

        if (rooms <= 0) {
            throw new IllegalArgumentException(
                    "Rooms to release must be greater than zero"
            );
        }

        int reserved =
                reservedRooms == null
                        ? 0
                        : reservedRooms;

        if (reserved < rooms) {
            throw new IllegalStateException(
                    "Cannot release more rooms than reserved"
            );
        }

        reservedRooms =
                reserved - rooms;

        recalculateAvailableInventory();
    }

    /**
     * Withdraws rooms from sale. (FR-21)
     *
     * <p>Refuses to block more than is genuinely unsold, so a partner
     * cannot block away rooms that guests have already booked.
     */
    public void blockRooms(int rooms) {

        if (rooms <= 0) {
            throw new IllegalArgumentException(
                    "Rooms to block must be greater than zero"
            );
        }

        int blocked =
                blockedRooms == null
                        ? 0
                        : blockedRooms;

        int reserved =
                reservedRooms == null
                        ? 0
                        : reservedRooms;

        int total =
                totalInventory == null
                        ? 0
                        : totalInventory;

        if (reserved + blocked + rooms > total) {
            throw new IllegalStateException(
                    "Cannot block " + rooms + " rooms: only "
                            + Math.max(total - reserved - blocked, 0)
                            + " are free on this date"
            );
        }

        blockedRooms = blocked + rooms;

        recalculateAvailableInventory();
    }

    /**
     * Puts blocked rooms back on sale.
     */
    public void unblockRooms(int rooms) {

        if (rooms <= 0) {
            throw new IllegalArgumentException(
                    "Rooms to unblock must be greater than zero"
            );
        }

        int blocked =
                blockedRooms == null
                        ? 0
                        : blockedRooms;

        if (blocked < rooms) {
            throw new IllegalStateException(
                    "Cannot unblock " + rooms
                            + " rooms: only " + blocked
                            + " are blocked on this date"
            );
        }

        blockedRooms = blocked - rooms;

        recalculateAvailableInventory();
    }

    public Integer getBlockedRooms() {
        return blockedRooms;
    }

    public void setBlockedRooms(Integer blockedRooms) {
        this.blockedRooms = blockedRooms;
    }
}
