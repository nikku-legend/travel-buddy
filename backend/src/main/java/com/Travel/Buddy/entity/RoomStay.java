package com.Travel.Buddy.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One physical room occupied by one booking across a date range.
 * (FR-22, FR-23)
 *
 * <p>The stay dates are copied from the reservation rather than
 * derived through a join, so the "is this room free?" check on every
 * assignment does not have to traverse bookings and
 * hotel_reservations.
 *
 * <p>MySQL has no exclusion constraint, so overlapping stays cannot be
 * prevented declaratively. {@link RoomStayService} checks under a
 * {@code PESSIMISTIC_WRITE} lock on the physical room row, which
 * serialises competing assignments to the same room.
 */
@Entity
@Table(
        name = "room_stays",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_room_stays_booking_room",
                        columnNames = {"booking_id", "physical_room_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_room_stays_room_dates",
                        columnList = "physical_room_id, check_in_date, check_out_date"
                ),
                @Index(
                        name = "idx_room_stays_booking",
                        columnList = "booking_id"
                ),
                @Index(
                        name = "idx_room_stays_status",
                        columnList = "status"
                )
        }
)
public class RoomStay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stay_id")
    private Long stayId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "booking_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_room_stays_booking")
    )
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "physical_room_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_room_stays_physical_room")
    )
    private PhysicalRoom physicalRoom;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RoomStayStatus status = RoomStayStatus.ASSIGNED;

    @Column(name = "guest_name", length = 150)
    private String guestName;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @Column(name = "checked_out_at")
    private LocalDateTime checkedOutAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "assigned_by_user_id",
            foreignKey = @ForeignKey(name = "fk_room_stays_assigned_by")
    )
    private User assignedBy;

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

    public Long getStayId() {
        return stayId;
    }

    public void setStayId(Long stayId) {
        this.stayId = stayId;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public PhysicalRoom getPhysicalRoom() {
        return physicalRoom;
    }

    public void setPhysicalRoom(PhysicalRoom physicalRoom) {
        this.physicalRoom = physicalRoom;
    }

    public LocalDate getCheckInDate() {
        return checkInDate;
    }

    public void setCheckInDate(LocalDate checkInDate) {
        this.checkInDate = checkInDate;
    }

    public LocalDate getCheckOutDate() {
        return checkOutDate;
    }

    public void setCheckOutDate(LocalDate checkOutDate) {
        this.checkOutDate = checkOutDate;
    }

    public RoomStayStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStayStatus status) {
        this.status = status;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(LocalDateTime checkedInAt) {
        this.checkedInAt = checkedInAt;
    }

    public LocalDateTime getCheckedOutAt() {
        return checkedOutAt;
    }

    public void setCheckedOutAt(LocalDateTime checkedOutAt) {
        this.checkedOutAt = checkedOutAt;
    }

    public User getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(User assignedBy) {
        this.assignedBy = assignedBy;
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
