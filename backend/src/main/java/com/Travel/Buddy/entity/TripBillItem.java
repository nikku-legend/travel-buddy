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
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * One immutable line on the trip bill. (SRS 2.2 section 5.1)
 *
 * <p>Written once per checkout attempt, before payment, and never
 * updated. If the bill were a view over current prices then a
 * price change after confirmation would silently rewrite a
 * receipt. A retried checkout writes <em>new</em> rows, so the
 * difference between two attempts stays visible.
 */
@Entity
@Table(
        name = "trip_bill_items",
        indexes = {
                @Index(
                        name = "idx_trip_bill_items_checkout",
                        columnList = "checkout_id"
                )
        }
)
public class TripBillItem {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long billItemId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "checkout_id",
            nullable = false
    )
    private TripCheckout checkout;

    /**
     * Not a foreign key with a cascade. The line must outlive
     * the selection it was quoted from.
     */
    @Column(name = "selection_id")
    private Long selectionId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "line_type",
            nullable = false
    )
    private TripBillLineType lineType;

    @Column(nullable = false, length = 200)
    private String label;

    /**
     * Secondary grouping the traveller reads, e.g.
     * "Puri, 12-14 Mar".
     */
    @Column(length = 200)
    private String detail;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(
            name = "unit_amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal unitAmount = BigDecimal.ZERO;

    @Column(
            name = "amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    protected TripBillItem() {
    }

    public TripBillItem(
            TripCheckout checkout,
            Long selectionId,
            TripBillLineType lineType,
            String label,
            String detail,
            int quantity,
            BigDecimal unitAmount,
            BigDecimal amount,
            String currency
    ) {
        this.checkout = checkout;
        this.selectionId = selectionId;
        this.lineType = lineType;
        this.label = label;
        this.detail = detail;
        this.quantity = quantity;
        this.unitAmount = unitAmount;
        this.amount = amount;
        this.currency = currency;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now()
                .truncatedTo(ChronoUnit.MICROS);
    }

    public Long getBillItemId() {
        return billItemId;
    }

    public TripCheckout getCheckout() {
        return checkout;
    }

    public Long getSelectionId() {
        return selectionId;
    }

    public TripBillLineType getLineType() {
        return lineType;
    }

    public String getLabel() {
        return label;
    }

    public String getDetail() {
        return detail;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitAmount() {
        return unitAmount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}