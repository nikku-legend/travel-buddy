package com.Travel.Buddy.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The digital voucher a guest presents at check-in. (FR-24)
 *
 * <p>Deliberately carries no guest contact details. The front desk
 * only learns who the guest is after the voucher verifies, so a
 * photographed voucher cannot be used to harvest names, phone
 * numbers or booking references.
 *
 * <p>Single use: {@link VoucherStatus#USED} after check-in, so a
 * replayed QR will not admit a second guest.
 */
@Entity
@Table(
        name = "booking_vouchers",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_vouchers_booking",
                        columnNames = "booking_id"
                ),
                @UniqueConstraint(
                        name = "uq_vouchers_code",
                        columnNames = "voucher_code"
                )
        },
        indexes = {
                @Index(
                        name = "idx_vouchers_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_vouchers_valid_until",
                        columnList = "valid_until"
                )
        }
)
public class BookingVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voucher_id")
    private Long voucherId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "booking_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_vouchers_booking")
    )
    private Booking booking;

    /**
     * Short code for the phone fallback when a camera will not scan.
     */
    @Column(name = "voucher_code", nullable = false, length = 24)
    private String voucherCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VoucherStatus status = VoucherStatus.ACTIVE;

    @Column(name = "guest_name", length = 150)
    private String guestName;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "consumed_by_stay_id",
            foreignKey = @ForeignKey(name = "fk_vouchers_consumed_by_stay")
    )
    private RoomStay consumedByStay;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    /**
     * How often staff have scanned this voucher. Repeated
     * verification without consumption is a signal worth surfacing to
     * risk monitoring (FR-36).
     */
    @Column(name = "verification_count", nullable = false)
    private Integer verificationCount = 0;

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @PrePersist
    protected void onCreate() {
        if (issuedAt == null) {
            issuedAt = LocalDateTime.now();
        }
    }

    /**
     * A voucher is only usable on its own dates and while ACTIVE.
     */
    public boolean isUsableOn(LocalDate date) {
        return status.isUsable()
                && !date.isBefore(validFrom)
                && !date.isAfter(validUntil);
    }
    public Long getVoucherId() {
        return voucherId;
    }

    public void setVoucherId(Long voucherId) {
        this.voucherId = voucherId;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public String getVoucherCode() {
        return voucherCode;
    }

    public void setVoucherCode(String voucherCode) {
        this.voucherCode = voucherCode;
    }

    public VoucherStatus getStatus() {
        return status;
    }

    public void setStatus(VoucherStatus status) {
        this.status = status;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public LocalDateTime getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(LocalDateTime consumedAt) {
        this.consumedAt = consumedAt;
    }

    public RoomStay getConsumedByStay() {
        return consumedByStay;
    }

    public void setConsumedByStay(RoomStay consumedByStay) {
        this.consumedByStay = consumedByStay;
    }

    public LocalDateTime getVoidedAt() {
        return voidedAt;
    }

    public void setVoidedAt(LocalDateTime voidedAt) {
        this.voidedAt = voidedAt;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public void setVoidReason(String voidReason) {
        this.voidReason = voidReason;
    }

    public Integer getVerificationCount() {
        return verificationCount;
    }

    public void setVerificationCount(Integer verificationCount) {
        this.verificationCount = verificationCount;
    }

    public LocalDateTime getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public void setLastVerifiedAt(LocalDateTime lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }
}