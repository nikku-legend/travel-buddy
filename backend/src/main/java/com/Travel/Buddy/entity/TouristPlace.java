package com.Travel.Buddy.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "tourist_places",
        indexes = {
                @Index(
                        name = "idx_tourist_places_state",
                        columnList = "state_id"
                ),
                @Index(
                        name = "idx_tourist_places_active",
                        columnList = "is_active"
                )
        }
)
public class TouristPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_id")
    private Long placeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "state_id",
            nullable = false
    )
    private State state;

    /**
     * Added for SRS 2.2. Nullable for the same reason as on
     * {@link Property}: the column postdates existing rows, and an
     * attraction with no city still contributes to trip planning
     * through its state.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @Column(
            name = "name",
            nullable = false,
            length = 150
    )
    private String name;

    /**
     * Attraction type shown on the discovery card. (SRS 2.3
     * section 2.2)
     *
     * <p>Free text rather than an enum: the taxonomy is the
     * platform's to curate, and an enum would force a schema
     * migration to add a kind of place. An unrecognised value is
     * a display problem, not a data-integrity one.
     */
    @Column(length = 40)
    private String category;

    /**
     * Typical visit length in minutes. Null when there is no
     * honest estimate, because a rounded-up guess presented as
     * fact is worse than an omitted line.
     */
    @Column(name = "estimated_visit_minutes")
    private Integer estimatedVisitMinutes;

    /**
     * Editorial "featured" flag for the Home discovery sections.
     *
     * <p>Deliberately separate from popularity, which is derived
     * from review volume. Featured is a human curation decision
     * and belongs in the data; popularity is a measurement.
     */
    @Column(name = "is_featured", nullable = false)
    private boolean featured = false;

    @Column(
            name = "description",
            columnDefinition = "TEXT"
    )
    private String description;

    @Column(
            name = "entry_fee",
            precision = 12,
            scale = 2
    )
    private BigDecimal entryFee;

    @Column(
            name = "currency_code",
            nullable = false,
            length = 3
    )
    private String currency;

    @Column(
            name = "image_url",
            length = 500
    )
    private String imageUrl;

    @Column(
            name = "latitude",
            precision = 10,
            scale = 7
    )
    private BigDecimal latitude;

    @Column(
            name = "longitude",
            precision = 10,
            scale = 7
    )
    private BigDecimal longitude;

    @Column(
            name = "is_active",
            nullable = false
    )
    private Boolean active = true;

    public TouristPlace() {
    }

    public Long getPlaceId() {
        return placeId;
    }

    public void setPlaceId(Long placeId) {
        this.placeId = placeId;
    }

    public City getCity() {
        return city;
    }

    public void setCity(City city) {
        this.city = city;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getEstimatedVisitMinutes() {
        return estimatedVisitMinutes;
    }

    public void setEstimatedVisitMinutes(
            Integer estimatedVisitMinutes
    ) {
        this.estimatedVisitMinutes = estimatedVisitMinutes;
    }

    public boolean isFeatured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
    }

    /**
     * Visit duration rendered for the card, e.g. "2h" or
     * "1h 30m". Null when no estimate is held, so the card can
     * omit the line entirely rather than show a placeholder.
     */
    public String visitDurationLabel() {
        if (estimatedVisitMinutes == null) {
            return null;
        }

        int hours = estimatedVisitMinutes / 60;
        int minutes = estimatedVisitMinutes % 60;

        if (hours == 0) {
            return minutes + "m";
        }

        return minutes == 0
                ? hours + "h"
                : hours + "h " + minutes + "m";
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getEntryFee() {
        return entryFee;
    }

    public void setEntryFee(BigDecimal entryFee) {
        this.entryFee = entryFee;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
