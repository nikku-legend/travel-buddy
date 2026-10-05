package com.Travel.Buddy.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "booking_cancellations",
        indexes = {
                @Index(
                        name = "idx_booking_cancellations_booking",
                        columnList = "booking_id"
                ),
                @Index(
                        name = "idx_booking_cancellations_user",
                        columnList = "cancelled_by_user_id"
                ),
                @Index(
                        name = "idx_booking_cancellations_refund_status",
                        columnList = "refund_status"
                ),
                @Index(
                        name = "idx_booking_cancellations_cancelled_at",
                        columnList = "cancelled_at"
                )
        }
)
public class BookingCancellation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cancellation_id")
    private Long cancellationId;


    /**
     * The booking that was cancelled.
     *
     * One booking can have only one cancellation record.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "booking_id",
            nullable = false,
            unique = true
    )
    private Booking booking;


    /**
     * The authenticated traveler who cancelled the booking.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "cancelled_by_user_id",
            nullable = false
    )
    private User cancelledByUser;


    /**
     * Structured cancellation reason selected by the user.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "cancellation_reason",
            nullable = false,
            length = 100
    )
    private CancellationReason cancellationReason;


    /**
     * Optional free-text explanation.
     */
    @Column(
            name = "cancellation_note",
            columnDefinition = "TEXT"
    )
    private String cancellationNote;


    /**
     * Exact date/time when the cancellation happened.
     */
    @Column(
            name = "cancelled_at",
            nullable = false
    )
    private LocalDateTime cancelledAt;


    /**
     * Current refund lifecycle state.
     *
     * Razorpay refund processing will be connected later.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "refund_status",
            nullable = false,
            length = 30
    )
    private RefundStatus refundStatus;


    /**
     * Amount that should eventually be refunded.
     */
    @Column(
            name = "refund_amount",
            precision = 12,
            scale = 2
    )
    private BigDecimal refundAmount;


    /**
     * Currency used for the refund.
     */
    @Column(
            name = "refund_currency",
            length = 3
    )
    private String refundCurrency;


    /**
     * Payment gateway refund reference.
     *
     * This will be populated during the final Razorpay
     * integration stage.
     */
    @Column(
            name = "refund_reference",
            length = 100
    )
    private String refundReference;


    /**
     * Date/time when the refund was successfully processed.
     */
    @Column(name = "refund_processed_at")
    private LocalDateTime refundProcessedAt;


    /**
     * Entity creation timestamp.
     */
    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;


    public BookingCancellation() {
    }


    /**
     * Automatically initialize timestamps and default
     * refund status.
     */
    @PrePersist
    protected void onCreate() {

        LocalDateTime now = LocalDateTime.now();

        if (cancelledAt == null) {
            cancelledAt = now;
        }

        if (createdAt == null) {
            createdAt = now;
        }

        if (refundStatus == null) {
            refundStatus = RefundStatus.NOT_APPLICABLE;
        }
    }


    // =========================================================
    // GETTERS / SETTERS
    // =========================================================

    public Long getCancellationId() {
        return cancellationId;
    }


    public void setCancellationId(
            Long cancellationId
    ) {
        this.cancellationId = cancellationId;
    }


    public Booking getBooking() {
        return booking;
    }


    public void setBooking(
            Booking booking
    ) {
        this.booking = booking;
    }


    public User getCancelledByUser() {
        return cancelledByUser;
    }


    public void setCancelledByUser(
            User cancelledByUser
    ) {
        this.cancelledByUser = cancelledByUser;
    }


    public CancellationReason getCancellationReason() {
        return cancellationReason;
    }


    public void setCancellationReason(
            CancellationReason cancellationReason
    ) {
        this.cancellationReason = cancellationReason;
    }


    public String getCancellationNote() {
        return cancellationNote;
    }


    public void setCancellationNote(
            String cancellationNote
    ) {
        this.cancellationNote = cancellationNote;
    }


    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }


    public void setCancelledAt(
            LocalDateTime cancelledAt
    ) {
        this.cancelledAt = cancelledAt;
    }


    public RefundStatus getRefundStatus() {
        return refundStatus;
    }


    public void setRefundStatus(
            RefundStatus refundStatus
    ) {
        this.refundStatus = refundStatus;
    }


    public BigDecimal getRefundAmount() {
        return refundAmount;
    }


    public void setRefundAmount(
            BigDecimal refundAmount
    ) {
        this.refundAmount = refundAmount;
    }


    public String getRefundCurrency() {
        return refundCurrency;
    }


    public void setRefundCurrency(
            String refundCurrency
    ) {
        this.refundCurrency = refundCurrency;
    }


    public String getRefundReference() {
        return refundReference;
    }


    public void setRefundReference(
            String refundReference
    ) {
        this.refundReference = refundReference;
    }


    public LocalDateTime getRefundProcessedAt() {
        return refundProcessedAt;
    }


    public void setRefundProcessedAt(
            LocalDateTime refundProcessedAt
    ) {
        this.refundProcessedAt = refundProcessedAt;
    }


    public LocalDateTime getCreatedAt() {
        return createdAt;
    }


    public void setCreatedAt(
            LocalDateTime createdAt
    ) {
        this.createdAt = createdAt;
    }
}