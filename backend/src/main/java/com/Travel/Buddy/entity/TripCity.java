package com.Travel.Buddy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * An ordered city stop on a trip. (SRS 2.2 TP-02, TP-03)
 *
 * <p>{@code sequence} is 1-based and contiguous. Reordering
 * renumbers the whole route rather than swapping two values,
 * because a sparse sequence is ambiguous when new stops are
 * inserted between two existing ones.
 */
@Entity
@Table(
        name = "trip_cities",
        indexes = {
                @Index(
                        name = "idx_trip_cities_trip",
                        columnList = "trip_id, sequence"
                )
        }
)
public class TripCity {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long tripCityId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "trip_id",
            nullable = false
    )
    private Trip trip;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "city_id",
            nullable = false
    )
    private City city;

    @Column(nullable = false)
    private int sequence;

    @Column(name = "arrival_date")
    private LocalDate arrivalDate;

    @Column(name = "departure_date")
    private LocalDate departureDate;

    /**
     * Why the engine put this city here. Cleared when the
     * traveller reorders, because after that it would be a
     * statement the engine no longer believes.
     */
    @Column(name = "sequence_reason", length = 500)
    private String sequenceReason;

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    protected TripCity() {
    }

    public TripCity(
            Trip trip,
            City city,
            int sequence
    ) {
        this.trip = trip;
        this.city = city;
        this.sequence = sequence;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now()
                .truncatedTo(ChronoUnit.MICROS);
    }

    public void stayDates(
            LocalDate arrival,
            LocalDate departure
    ) {
        this.arrivalDate = arrival;
        this.departureDate = departure;
    }

    public void reorderTo(int newSequence) {
        this.sequence = newSequence;
    }

    public void clearSequenceReason() {
        this.sequenceReason = null;
    }

    public void setSequenceReason(String sequenceReason) {
        this.sequenceReason = sequenceReason;
    }

    public boolean hasCoordinates() {
        return city != null && city.hasCoordinates();
    }

    public Long getTripCityId() {
        return tripCityId;
    }

    public Trip getTrip() {
        return trip;
    }

    public City getCity() {
        return city;
    }

    public int getSequence() {
        return sequence;
    }

    public LocalDate getArrivalDate() {
        return arrivalDate;
    }

    public void setArrivalDate(LocalDate arrivalDate) {
        this.arrivalDate = arrivalDate;
    }

    public LocalDate getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(LocalDate departureDate) {
        this.departureDate = departureDate;
    }

    public String getSequenceReason() {
        return sequenceReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}