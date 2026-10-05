package com.Travel.Buddy.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A partner's record of why rooms are out of service. (FR-21)
 *
 * <p>{@code room_inventory_daily} holds the running totals; this table
 * is the explanation. A nightly number with no reason attached is not
 * something a partner can act on six months later, and it is what
 * settlement disputes are settled from.
 *
 * <p>Blocks are released, never deleted, so the history of every
 * closure survives.
 */
@Entity
@Table(
        name = "room_blocks",
        indexes = {
                @Index(
                        name = "idx_room_blocks_date",
                        columnList = "blocked_date"
                ),
                @Index(
                        name = "idx_room_blocks_room_date",
                        columnList = "room_type_id, blocked_date"
                )
        }
)
public class RoomBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "block_id")
    private Long blockId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "room_type_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_room_blocks_room_type")
    )
    private RoomType roomType;

    @Column(name = "blocked_date", nullable = false)
    private LocalDate blockedDate;

    @Column(name = "rooms_blocked", nullable = false)
    private Integer roomsBlocked = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private RoomBlockReason reason = RoomBlockReason.MAINTENANCE;

    @Column(name = "notes", length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "created_by_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_room_blocks_created_by")
    )
    private User createdBy;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "released_by_user_id",
            foreignKey = @ForeignKey(name = "fk_room_blocks_released_by")
    )
    private User releasedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public boolean isActive() {
        return releasedAt == null;
    }

    public Long getBlockId() {
        return blockId;
    }

    public void setBlockId(Long blockId) {
        this.blockId = blockId;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    public void setRoomType(RoomType roomType) {
        this.roomType = roomType;
    }

    public LocalDate getBlockedDate() {
        return blockedDate;
    }

    public void setBlockedDate(LocalDate blockedDate) {
        this.blockedDate = blockedDate;
    }

    public Integer getRoomsBlocked() {
        return roomsBlocked;
    }

    public void setRoomsBlocked(Integer roomsBlocked) {
        this.roomsBlocked = roomsBlocked;
    }

    public RoomBlockReason getReason() {
        return reason;
    }

    public void setReason(RoomBlockReason reason) {
        this.reason = reason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(LocalDateTime releasedAt) {
        this.releasedAt = releasedAt;
    }

    public User getReleasedBy() {
        return releasedBy;
    }

    public void setReleasedBy(User releasedBy) {
        this.releasedBy = releasedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
