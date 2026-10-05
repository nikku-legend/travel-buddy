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
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * One cancellation tier. (FR-11, SRS 2.3 section 6.2)
 *
 * <p>{@code refundPercent} is what the traveller <em>gets
 * back</em>, not the penalty. Storing it that way means the
 * figure shown on the page is the figure a traveller cares
 * about, and the deduction is derived rather than displayed and
 * separately reconciled.
 *
 * <p>A property with no tiers is treated as non-refundable.
 * Assuming a generous policy for a property that never stated
 * one is how a platform ends up owing refunds it never agreed
 * to.
 */
@Entity
@Table(
        name = "property_cancellation_terms",
        indexes = {
                @Index(
                        name = "idx_property_cancellation_property",
                        columnList = "property_id, days_before_check_in"
                )
        }
)
public class PropertyCancellationTerm {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long termId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "property_id",
            nullable = false
    )
    private Property property;

    /**
     * The tier applies when check-in is this many days away or
     * nearer. Zero means the day of arrival.
     */
    @Column(
            name = "days_before_check_in",
            nullable = false
    )
    private int daysBeforeCheckIn;

    @Column(
            name = "refund_percent",
            nullable = false,
            precision = 5,
            scale = 2
    )
    private BigDecimal refundPercent;

    /**
     * Nights charged when a guest departs early, where the
     * property enforces that. Null-safe in display: most
     * properties do not.
     */
    @Column(
            name = "min_nights_charge",
            nullable = false
    )
    private int minNightsCharge = 0;

    @Column(length = 200)
    private String description;

    protected PropertyCancellationTerm() {
    }

    public PropertyCancellationTerm(
            Property property,
            int daysBeforeCheckIn,
            BigDecimal refundPercent,
            int minNightsCharge,
            String description
    ) {
        this.property = property;
        this.daysBeforeCheckIn = daysBeforeCheckIn;
        this.refundPercent = refundPercent;
        this.minNightsCharge = minNightsCharge;
        this.description = description;
    }

    /**
     * The deduction percentage, derived so a caller can never
     * display the penalty and the refund as if they were two
     * independent facts.
     */
    public BigDecimal penaltyPercent() {
        return BigDecimal.valueOf(100)
                .subtract(refundPercent);
    }

    public Long getTermId() {
        return termId;
    }

    public Property getProperty() {
        return property;
    }

    public int getDaysBeforeCheckIn() {
        return daysBeforeCheckIn;
    }

    public BigDecimal getRefundPercent() {
        return refundPercent;
    }

    public int getMinNightsCharge() {
        return minNightsCharge;
    }

    public String getDescription() {
        return description;
    }
}