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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * A place the traveller picked in a city. (SRS 2.2 TP-04)
 *
 * <p>Optional by design: a trip with no places is valid, and the
 * hotel recommender falls back to city-centre distance rather
 * than refusing to suggest anything.
 */
@Entity
@Table(
        name = "trip_places",
        indexes = {
                @Index(
                        name = "idx_trip_places_trip_city",
                        columnList = "trip_city_id"
                )
        }
)
public class TripPlace {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long tripPlaceId;

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
            name = "trip_city_id",
            nullable = false
    )
    private TripCity tripCity;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "place_id",
            nullable = false
    )
    private TouristPlace place;

    /**
     * The traveller's own working note. Deliberately not a
     * review: it is private to the plan.
     */
    @Column(length = 500)
    private String note;

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    protected TripPlace() {
    }

    public TripPlace(
            Trip trip,
            TripCity tripCity,
            TouristPlace place
    ) {
        this.trip = trip;
        this.tripCity = tripCity;
        this.place = place;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now()
                .truncatedTo(ChronoUnit.MICROS);
    }

    public boolean hasCoordinates() {
        return place != null
                && place.getLatitude() != null
                && place.getLongitude() != null;
    }

    public Long getTripPlaceId() {
        return tripPlaceId;
    }

    public Trip getTrip() {
        return trip;
    }

    public TripCity getTripCity() {
        return tripCity;
    }

    public TouristPlace getPlace() {
        return place;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}