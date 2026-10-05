package com.Travel.Buddy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
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
 * A recommendation the engine made, with its reasoning. (SRS 2.2
 * section 4, FR-40)
 *
 * <p>Two decisions are encoded here:
 *
 * <ol>
 *   <li><strong>Recommendations are persisted, not computed on
 *       read.</strong> A reason that changes between renders is a
 *       lie the traveller will notice.</li>
 *   <li><strong>Rejections are stored too.</strong> Keeping only
 *       what was shown would make the engine a black box; the
 *       traveller can see what was considered and why it lost.</li>
 * </ol>
 */
@Entity
@Table(
        name = "trip_recommendations",
        indexes = {
                @Index(
                        name = "idx_trip_recommendations_trip",
                        columnList = "trip_id, recommendation_type, rank_position"
                )
        }
)
public class TripRecommendation {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long recommendationId;

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
    @Column(
            name = "recommendation_type",
            nullable = false
    )
    private TripRecommendationType recommendationType;

    @Column(
            name = "target_id",
            nullable = false
    )
    private Long targetId;
    /**
     * The room this offer refers to. (SRS 2.2 TP-08)
     *
     * <p>The recommender scores and prices a hotel against one
     * specific room and its explanation names that room, so the
     * id has to travel with the offer. Without it a client can only
     * add the property, and the cart prices a hotel from its room
     * type, so the stay would quote zero and checkout would then
     * refuse the trip as an empty cart.
     *
     * <p>Null for a rejected candidate (rejected precisely because
     * no room was available) and for GUIDE and CAB offers, which
     * have no room.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "room_type_id",
            foreignKey = @ForeignKey(
                    name = "fk_trip_rec_room_type"
            )
    )
    private RoomType roomType;

    /**
     * Snapshotted so an abandoned recommendation can still be
     * explained after the listing is renamed or withdrawn.
     */
    @Column(length = 200)
    private String title;

    @Column(
            name = "quoted_amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal quotedAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    /**
     * 1-based. Null means the engine could not score this offer,
     * in which case the row is not written at all.
     */
    @Column(name = "rank_position")
    private Integer rankPosition;

    /**
     * Distance from the trip's selected places, the primary input
     * of section 4.1. Null when the trip has no places and the
     * figure was measured from the city centre instead.
     */
    @Column(
            name = "distance_km",
            precision = 8,
            scale = 2
    )
    private BigDecimal distanceKm;

    /**
     * The justification shown verbatim in the UI, e.g.
     * "2.1 km from your selected place".
     */
    @Column(length = 500)
    private String reason;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(
            name = "was_shown",
            nullable = false
    )
    private boolean shown = true;

    @Column(name = "was_accepted")
    private Boolean accepted;

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    protected TripRecommendation() {
    }

    public TripRecommendation(
            Trip trip,
            TripCity tripCity,
            TripRecommendationType recommendationType,
            Long targetId,
            String title
    ) {
        this.trip = trip;
        this.tripCity = tripCity;
        this.recommendationType = recommendationType;
        this.targetId = targetId;
        this.title = title;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now()
                .truncatedTo(ChronoUnit.MICROS);
    }

    public void rank(
            int position,
            BigDecimal amount,
            String currency,
            BigDecimal distanceKm,
            String reason,
            RoomType roomType
    ) {
        this.rankPosition = position;
        this.quotedAmount = amount;
        this.currency = currency;
        this.distanceKm = distanceKm;
        this.reason = reason;

        /*
         * The room travels with the offer. A ranked hotel is the
         * one case where a recommendation is meant to be acted on,
         * and the cart cannot price a stay without it.
         */
        this.roomType = roomType;
    }

    public void rejectAs(String reason) {
        this.rejectionReason = reason;
        this.shown = false;
    }

    /**
     * The acceptance signal. Null while undecided, so "shown but
     * not acted on" is distinguishable from "never shown".
     */
    public void accept() {
        this.accepted = Boolean.TRUE;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    public Long getRecommendationId() {
        return recommendationId;
    }

    public Trip getTrip() {
        return trip;
    }

    public TripCity getTripCity() {
        return tripCity;
    }

    public TripRecommendationType getRecommendationType() {
        return recommendationType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getQuotedAmount() {
        return quotedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public Integer getRankPosition() {
        return rankPosition;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public String getReason() {
        return reason;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public boolean isShown() {
        return shown;
    }

    public Boolean getAccepted() {
        return accepted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}