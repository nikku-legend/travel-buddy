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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * A city. Added for SRS 2.2, whose geography hierarchy is
 * Country -> Region -> State -> City -> Destination -> Attraction.
 *
 * <p>Coordinates are optional. A city without them cannot anchor
 * proximity recommendations, and the planner treats that as
 * "cannot recommend nearby" rather than guessing.
 */
@Entity
@Table(
        name = "cities",
        indexes = {
                @Index(
                        name = "idx_cities_state",
                        columnList = "state_id, name"
                )
        }
)
public class City {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long cityId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "state_id",
            nullable = false
    )
    private State state;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 120, unique = true)
    private String slug;

    @Column(name = "is_capital", nullable = false)
    private boolean capital = false;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    protected City() {
    }

    public City(
            State state,
            String name,
            String slug
    ) {
        this.state = state;
        this.name = name;
        this.slug = slug;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now()
                .truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Whether this city can anchor a proximity calculation.
     */
    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    public Long getCityId() {
        return cityId;
    }

    public State getState() {
        return state;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public boolean isCapital() {
        return capital;
    }

    public void setCapital(boolean capital) {
        this.capital = capital;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}