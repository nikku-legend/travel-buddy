package com.Travel.Buddy.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A real, numbered room belonging to a property. (FR-22)
 *
 * <p>Distinct from {@link RoomType}, which is a bookable CATEGORY
 * ("Deluxe Room"). A property has one room type and many physical
 * rooms; the front desk allocates the physical room on arrival.
 *
 * <p>{@code roomNumber} is free text because hotels number rooms
 * however they like ("101", "A-12", "Villa 3") and the platform must
 * not fight that.
 */
@Entity
@Table(
        name = "physical_rooms",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_physical_rooms_number",
                        columnNames = {"property_id", "room_number"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_physical_rooms_property_type",
                        columnList = "property_id, room_type_id"
                ),
                @Index(
                        name = "idx_physical_rooms_status",
                        columnList = "status"
                )
        }
)
public class PhysicalRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "physical_room_id")
    private Long physicalRoomId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "property_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_physical_rooms_property")
    )
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "room_type_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_physical_rooms_room_type")
    )
    private RoomType roomType;

    @Column(name = "room_number", nullable = false, length = 30)
    private String roomNumber;

    @Column(name = "floor_label", length = 30)
    private String floorLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PhysicalRoomStatus status = PhysicalRoomStatus.AVAILABLE;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isAssignable() {
        return status == PhysicalRoomStatus.AVAILABLE;
    }

    public Long getPhysicalRoomId() {
        return physicalRoomId;
    }

    public void setPhysicalRoomId(Long physicalRoomId) {
        this.physicalRoomId = physicalRoomId;
    }

    public Property getProperty() {
        return property;
    }

    public void setProperty(Property property) {
        this.property = property;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    public void setRoomType(RoomType roomType) {
        this.roomType = roomType;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public String getFloorLabel() {
        return floorLabel;
    }

    public void setFloorLabel(String floorLabel) {
        this.floorLabel = floorLabel;
    }

    public PhysicalRoomStatus getStatus() {
        return status;
    }

    public void setStatus(PhysicalRoomStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
