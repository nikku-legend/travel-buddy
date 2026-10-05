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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * The single centralized payment lifecycle for a whole trip.
 * (SRS 2.2 section 6, TP-10)
 *
 * <p>One checkout covers every selected service. That is the
 * change from 2.0, where payment sat on each individual booking.
 *
 * <p>{@code quotedTotal} is what the traveller saw in the cart;
 * {@code totalAmount} is what revalidation found. A difference
 * between them is exactly the "if something changed, show the
 * affected line item and require confirmation" case, and keeping
 * both is what makes that comparison possible.
 */
@Entity
@Table(
        name = "trip_checkout",
        indexes = {
                @Index(
                        name = "idx_trip_checkout_trip",
                        columnList = "trip_id, status"
                ),
                @Index(
                        name = "idx_trip_checkout_status",
                        columnList = "status"
                )
        }
)
public class TripCheckout {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long checkoutId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "trip_id",
            nullable = false
    )
    private Trip trip;

    @Column(
            name = "checkout_reference",
            nullable = false,
            length = 40,
            unique = true
    )
    private String checkoutReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripCheckoutStatus status = TripCheckoutStatus.CREATED;

    @Column(
            name = "total_amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(
            name = "tax_amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(
            name = "fee_amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(
            name = "discount_amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(
            name = "quoted_total",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal quotedTotal = BigDecimal.ZERO;

    @Column(name = "razorpay_order_id", length = 100)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    @Column(name = "recovery_reason", length = 500)
    private String recoveryReason;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

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

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    protected TripCheckout() {
    }

    public TripCheckout(
            Trip trip,
            String checkoutReference
    ) {
        this.trip = trip;
        this.checkoutReference = checkoutReference;
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

    public void beginRevalidation() {
        this.status = TripCheckoutStatus.REVALIDATING;
    }

    /**
     * Freezes the revalidated figures. Everything the payment
     * will be for is decided here, once.
     */
    public void lockBill(
            BigDecimal total,
            BigDecimal tax,
            BigDecimal fee,
            BigDecimal discount,
            BigDecimal quoted,
            String currency
    ) {
        this.totalAmount = total;
        this.taxAmount = tax;
        this.feeAmount = fee;
        this.discountAmount = discount;
        this.quotedTotal = quoted;
        this.currency = currency;
    }

    public void awaitingPayment(String razorpayOrderId) {
        this.razorpayOrderId = razorpayOrderId;
        this.status = TripCheckoutStatus.PAYMENT_PENDING;
    }

    public void markPaid(String razorpayPaymentId) {
        this.razorpayPaymentId = razorpayPaymentId;
        this.status = TripCheckoutStatus.PAID;
    }

    public void confirm() {
        this.status = TripCheckoutStatus.CONFIRMED;
        this.confirmedAt = now();
        this.recoveryReason = null;
    }

    /**
     * Payment cleared but confirmation did not complete.
     *
     * <p>SRS 2.2 section 6 requires this to be reconciled from
     * persisted payment events, never by charging again. The
     * method exists so that state is a deliberate transition
     * rather than something reached by accident.
     */
    public void requireRecovery(String reason) {
        this.status = TripCheckoutStatus.RECOVERY_REQUIRED;
        this.recoveryReason = reason;
    }

    public void fail(String reason) {
        this.status = TripCheckoutStatus.FAILED;
        this.failureReason = reason;
    }

    /**
     * Whether the traveller's figure changed during revalidation.
     * Section 6 requires showing the affected line item and
     * getting explicit confirmation before charging the new total.
     */
    public boolean priceChanged() {
        return quotedTotal.compareTo(totalAmount) != 0;
    }

    public Long getCheckoutId() {
        return checkoutId;
    }

    public Trip getTrip() {
        return trip;
    }

    public String getCheckoutReference() {
        return checkoutReference;
    }

    public TripCheckoutStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getQuotedTotal() {
        return quotedTotal;
    }

    public String getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public String getRazorpayPaymentId() {
        return razorpayPaymentId;
    }

    public String getRecoveryReason() {
        return recoveryReason;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }
}