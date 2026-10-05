package com.Travel.Buddy.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "guide_reservations",
        indexes = {
                @Index(name = "idx_guide_reservations_booking", columnList = "booking_id"),
                @Index(name = "idx_guide_reservations_guide", columnList = "guide_id"),
                @Index(name = "idx_guide_reservations_date", columnList = "tour_date")
        }
)
public class GuideReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guide_reservation_id")
    private Long guideReservationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guide_id", nullable = false)
    private Guide guide;

    @Column(name = "tour_date", nullable = false)
    private LocalDate tourDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getGuideReservationId() {
        return guideReservationId;
    }

    public void setGuideReservationId(Long guideReservationId) {
        this.guideReservationId = guideReservationId;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public Guide getGuide() {
        return guide;
    }

    public void setGuide(Guide guide) {
        this.guide = guide;
    }

    public LocalDate getTourDate() {
        return tourDate;
    }

    public void setTourDate(LocalDate tourDate) {
        this.tourDate = tourDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
