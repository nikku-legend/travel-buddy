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
import java.time.LocalDateTime;

/**
 * A traveller's formal complaint about a paid booking. (FR-28)
 *
 * <p>All state changes go through the methods at the bottom of
 * this class rather than through setters, so the invariants that
 * make a dispute trustworthy are enforced in one place:
 *
 * <ol>
 *   <li>A closed dispute always carries a ruling and a timestamp.</li>
 *   <li>A ruling never moves more money than was requested.</li>
 *   <li>Terminal states are final.</li>
 * </ol>
 */
@Entity
@Table(
        name = "disputes",
        indexes = {
                @Index(
                        name = "idx_disputes_booking",
                        columnList = "booking_id"
                ),
                @Index(
                        name = "idx_disputes_raised_by",
                        columnList = "raised_by_user_id"
                ),
                @Index(
                        name = "idx_disputes_partner",
                        columnList = "partner_user_id"
                ),
                @Index(
                        name = "idx_disputes_queue",
                        columnList = "status, created_at"
                )
        }
)
public class Dispute {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long disputeId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "booking_id",
            nullable = false
    )
    private Booking booking;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "raised_by_user_id",
            nullable = false
    )
    private User raisedBy;

    /**
     * Nullable because a property can outlive its owner account.
     * The complaint is still valid even once the partner is gone.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_user_id")
    private User partner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeStatus status = DisputeStatus.OPEN;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(
            name = "requested_amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal requestedAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    private User assignedTo;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Enumerated(EnumType.STRING)
    private DisputeResolution resolution;

    @Column(
            name = "resolved_amount",
            precision = 12,
            scale = 2
    )
    private BigDecimal resolvedAmount;

    @Column(
            name = "resolution_notes",
            columnDefinition = "TEXT"
    )
    private String resolutionNotes;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

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

    /*
     * Optimistic locking. Two admins working the same dispute is
     * routine, and without this the second write silently
     * overwrites the first one's ruling.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    protected Dispute() {
    }

    public Dispute(
            Booking booking,
            User raisedBy,
            User partner,
            DisputeCategory category,
            String subject,
            String description,
            BigDecimal requestedAmount
    ) {
        this.booking = booking;
        this.raisedBy = raisedBy;
        this.partner = partner;
        this.category = category;
        this.subject = subject;
        this.description = description;
        this.requestedAmount = requestedAmount;
        this.status = DisputeStatus.OPEN;
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

    /**
     * Microsecond precision, matching the column and the rest of
     * the notification code. See {@link Notification} for why
     * this is not simply LocalDateTime.now().
     */
    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(
                java.time.temporal.ChronoUnit.MICROS
        );
    }

    /* ============================================================
     * BEHAVIOUR
     * ============================================================ */

    public void assignTo(User moderator) {
        this.assignedTo = moderator;
        this.assignedAt = now();
    }

    public void changeStatus(DisputeStatus next) {
        if (this.status.isTerminal()) {
            throw new IllegalStateException(
                    "This dispute is " + this.status
                            + " and can no longer change"
            );
        }
        this.status = next;
    }

    /**
     * Records the ruling and closes the dispute.
     *
     * @param amount money actually returned; zero is a valid
     *               outcome for NO_REFUND and CREDIT_NOTE
     */
    public void resolve(
            DisputeResolution ruling,
            BigDecimal amount,
            String notes
    ) {
        if (this.status.isTerminal()) {
            throw new IllegalStateException(
                    "This dispute is already closed"
            );
        }

        if (amount == null) {
            amount = BigDecimal.ZERO;
        }

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "A resolved amount cannot be negative"
            );
        }

        if (amount.compareTo(this.requestedAmount) > 0) {
            throw new IllegalArgumentException(
                    "A dispute cannot award more than the "
                            + "requested amount"
            );
        }

        if (ruling == null) {
            throw new IllegalArgumentException(
                    "A closing decision is required"
            );
        }

        if (ruling.involvesRefund() && amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "A refund decision must state an amount"
            );
        }

        if (ruling == DisputeResolution.NO_REFUND
                && amount.signum() != 0) {
            throw new IllegalArgumentException(
                    "A NO_REFUND decision must not move money"
            );
        }

        this.resolution = ruling;
        this.resolvedAmount = amount;
        this.resolutionNotes = notes;
        this.resolvedAt = now();
        this.status = DisputeStatus.RESOLVED;
    }

    /**
     * Closes a dispute without a payout. Still a ruling, still
     * recorded, but flagged REJECTED so both sides can tell the
     * difference between "denied" and "resolved with nothing due".
     */
    public void reject(String notes) {
        if (this.status.isTerminal()) {
            throw new IllegalStateException(
                    "This dispute is already closed"
            );
        }
        this.resolution = DisputeResolution.NO_REFUND;
        this.resolvedAmount = BigDecimal.ZERO;
        this.resolutionNotes = notes;
        this.resolvedAt = now();
        this.status = DisputeStatus.REJECTED;
    }

    public void withdraw() {
        if (!this.status.isWithdrawableByClaimant()) {
            throw new IllegalStateException(
                    "This dispute can no longer be withdrawn once "
                            + "it is " + this.status
            );
        }
        this.withdrawnAt = now();
        this.status = DisputeStatus.WITHDRAWN;
    }

    public boolean isOpen() {
        return this.status.isOpen();
    }

    /* ============================================================
     * ACCESSORS
     * ============================================================ */

    public Long getDisputeId() {
        return disputeId;
    }

    public Booking getBooking() {
        return booking;
    }

    public User getRaisedBy() {
        return raisedBy;
    }

    public User getPartner() {
        return partner;
    }

    public void setPartner(User partner) {
        this.partner = partner;
    }

    public DisputeCategory getCategory() {
        return category;
    }

    public DisputeStatus getStatus() {
        return status;
    }

    public String getSubject() {
        return subject;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public User getAssignedTo() {
        return assignedTo;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public DisputeResolution getResolution() {
        return resolution;
    }

    public BigDecimal getResolvedAmount() {
        return resolvedAmount;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public LocalDateTime getWithdrawnAt() {
        return withdrawnAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}