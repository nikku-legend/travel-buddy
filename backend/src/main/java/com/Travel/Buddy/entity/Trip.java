package com.Travel.Buddy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * A traveller's journey, from first input to post-trip review.
 * (SRS 2.2, extended by SRS 2.3)
 *
 * <p>The aggregate root of the planner. Everything else in the
 * trip module hangs off this: stops, places, selections, the
 * bill, checkout and the map milestones.
 */
@Entity
@Table(
        name = "trips",
        indexes = {
                @Index(
                        name = "idx_trips_user_created",
                        columnList = "user_id, created_at"
                ),
                @Index(
                        name = "idx_trips_user_status",
                        columnList = "user_id, status"
                )
        }
)
public class Trip {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long tripId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(
            name = "start_date",
            nullable = false
    )
    private LocalDate startDate;

    @Column(
            name = "end_date",
            nullable = false
    )
    private LocalDate endDate;

    @Column(
            name = "planned_city_count",
            nullable = false
    )
    private int plannedCityCount = 1;

    /* ============================================================
     * SRS 2.3 WIZARD INPUTS
     *
     * Nullable throughout, because the wizard collects them one
     * step at a time and a trip must be creatable before the
     * traveller has answered everything. Null means "not chosen
     * yet", not "invalid".
     * ============================================================ */

    /**
     * TP-02. Drives room occupancy, cab capacity and whether a
     * guide is viable for the group.
     */
    @Column(name = "traveler_count")
    private Integer travelerCount;

    @Column(name = "adult_count")
    private Integer adultCount;

    @Column(name = "child_count")
    private Integer childCount;

    /**
     * TP-03. The first geographic constraint: nothing outside the
     * chosen zone can be recommended.
     */
    @Column(name = "zone", length = 30)
    private String zone;

    /**
     * Stored as a name rather than a foreign key because the
     * hierarchy has no region table yet. The state that resolves
     * from it is what actually narrows recommendations.
     */
    @Column(name = "region_name", length = 120)
    private String regionName;

    /**
     * TP-05. A ranking preference, never a filter: budget still
     * applies the quality floor and premium still respects the
     * budget.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "travel_style", length = 20)
    private TravelStyle travelStyle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status = TripStatus.DRAFT;

    @Column(
            name = "budget_amount",
            precision = 12,
            scale = 2
    )
    private BigDecimal budgetAmount;

    @Column(name = "budget_currency", length = 3)
    private String budgetCurrency;

    @Column(
            name = "estimated_total",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal estimatedTotal = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Trip() {
    }

    public Trip(
            User user,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            int plannedCityCount,
            BigDecimal budget,
            String currency
    ) {
        this.user = user;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.plannedCityCount = plannedCityCount;
        this.budgetAmount = budget;
        this.budgetCurrency = budget == null ? null : currency;
        this.currency = currency;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = now();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(
                ChronoUnit.MICROS
        );
    }

    /* ============================================================
     * BEHAVIOUR
     * ============================================================ */

    /**
     * Nights rather than days. A trip from the 1st to the 3rd is
     * two nights, and pricing every leg by an inclusive day count
     * would overcharge by a day per hotel.
     */
    public int nights() {
        return (int) ChronoUnit.DAYS.between(startDate, endDate);
    }

    public boolean spansMultipleNights() {
        return nights() > 0;
    }

    public void requireEditable() {
        if (!status.isEditable()) {
            throw new IllegalStateException(
                    "This trip is " + status
                            + " and can no longer be edited"
            );
        }
    }

    public void moveTo(TripStatus next) {
        this.status = next;
        if (next == TripStatus.CONFIRMED && confirmedAt == null) {
            this.confirmedAt = now();
        }
        if (next == TripStatus.COMPLETED && completedAt == null) {
            this.completedAt = now();
        }
    }

    public void beginPlanningIfDraft() {
        if (status == TripStatus.DRAFT) {
            this.status = TripStatus.PLANNING;
        }
    }

    /* ============================================================
     * 2.3 INPUT BEHAVIOUR
     * ============================================================ */

    /**
     * Records how many people are travelling.
     *
     * <p>The split is optional because the SRS says "if the UI
     * collects it". What is not optional is the total: occupancy
     * and cab sizing both depend on it.
     */
    public void setParty(
            Integer travelers,
            Integer adults,
            Integer children
    ) {
        if (adults != null && children != null
                && travelers != null
                && adults + children != travelers) {
            throw new IllegalArgumentException(
                    "Adults plus children must equal the total "
                            + "travellers"
            );
        }

        if (adults != null && adults < 0
                || children != null && children < 0) {
            throw new IllegalArgumentException(
                    "Party sizes cannot be negative"
            );
        }

        this.travelerCount = travelers;
        this.adultCount = adults;
        this.childCount = children;
    }

    public void setScope(
            String zone,
            String regionName
    ) {
        this.zone = zone;
        this.regionName = regionName;
    }

    public void setTravelStyle(TravelStyle travelStyle) {
        this.travelStyle = travelStyle;
    }

    /**
     * Rooms needed for the party.
     *
     * <p>Two per room, floored at one, because a single traveller
     * still needs somewhere to sleep. This is what the hotel
     * recommender reads for occupancy.
     */
    public int roomsRequired() {
        int people = travelerCount == null
                ? 2
                : travelerCount;
        return Math.max(1, (int) Math.ceil(people / 2.0));
    }

    /**
     * Budget headroom, or null when no budget was set. Section 4.5
     * says exceeding the budget warns and never blocks, so this is
     * advisory throughout.
     */
    public BigDecimal budgetRemaining() {
        if (budgetAmount == null) {
            return null;
        }
        return budgetAmount.subtract(estimatedTotal);
    }

    public boolean isOverBudget() {
        BigDecimal remaining = budgetRemaining();
        return remaining != null && remaining.signum() < 0;
    }

    /* ============================================================
     * ACCESSORS
     * ============================================================ */

    public Long getTripId() {
        return tripId;
    }

    public User getUser() {
        return user;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public int getPlannedCityCount() {
        return plannedCityCount;
    }

    public void setPlannedCityCount(int plannedCityCount) {
        this.plannedCityCount = plannedCityCount;
    }

    public Integer getTravelerCount() {
        return travelerCount;
    }

    public Integer getAdultCount() {
        return adultCount;
    }

    public Integer getChildCount() {
        return childCount;
    }

    public String getZone() {
        return zone;
    }

    public String getRegionName() {
        return regionName;
    }

    public TravelStyle getTravelStyle() {
        return travelStyle;
    }

    public TripStatus getStatus() {
        return status;
    }

    public BigDecimal getBudgetAmount() {
        return budgetAmount;
    }

    public void setBudgetAmount(BigDecimal budgetAmount) {
        this.budgetAmount = budgetAmount;
    }

    public String getBudgetCurrency() {
        return budgetCurrency;
    }

    public void setBudgetCurrency(String budgetCurrency) {
        this.budgetCurrency = budgetCurrency;
    }

    public BigDecimal getEstimatedTotal() {
        return estimatedTotal;
    }

    public void setEstimatedTotal(BigDecimal estimatedTotal) {
        this.estimatedTotal = estimatedTotal;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}