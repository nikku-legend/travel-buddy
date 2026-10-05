package com.Travel.Buddy.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "bookings",
        indexes = {
                @Index(
                        name = "idx_bookings_user",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_bookings_status",
                        columnList = "booking_status"
                ),
                @Index(
                        name = "idx_bookings_payment_status",
                        columnList = "payment_status"
                ),
                @Index(
                        name = "idx_bookings_hold_expires",
                        columnList = "hold_expires_at"
                )
        }
)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Long bookingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @Column(
            name = "booking_reference",
            nullable = false,
            unique = true,
            length = 50
    )
    private String bookingReference;

    /*
     * What this booking is for. FR-34 cannot compute commission
     * from a booking that does not say whether it is a room, a guide
     * or a ride, because each pays a different rate.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "booking_type", nullable = false)
    private BookingType bookingType = BookingType.HOTEL;

    @Column(
            name = "total_amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal totalAmount;

    @Column(
            name = "currency",
            nullable = false,
            length = 3
    )
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "booking_status",
            nullable = false
    )
    private BookingStatus bookingStatus;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "payment_status",
            nullable = false
    )
    private PaymentStatus paymentStatus;

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /*
     * ------------------------------------------------------------
     * GUEST INFORMATION
     * ------------------------------------------------------------
     */

    @Column(
            name = "guest_count",
            nullable = false
    )
    private Integer guestCount;

    @Column(
            name = "guest_name",
            length = 150
    )
    private String guestName;

    @Column(
            name = "guest_email",
            length = 150
    )
    private String guestEmail;

    @Column(
            name = "guest_phone",
            length = 50
    )
    private String guestPhone;

    @Column(
            name = "special_requests",
            columnDefinition = "TEXT"
    )
    private String specialRequests;

    /*
     * ------------------------------------------------------------
     * PAYMENT / HOLD INFORMATION
     * ------------------------------------------------------------
     */

    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    @Column(
            name = "razorpay_order_id",
            length = 100
    )
    private String razorpayOrderId;

    @Column(
            name = "razorpay_payment_id",
            length = 100
    )
    private String razorpayPaymentId;

    /*
     * ------------------------------------------------------------
     * CONSTRUCTOR
     * ------------------------------------------------------------
     */

    public Booking() {
    }

    /*
     * ------------------------------------------------------------
     * JPA LIFECYCLE
     * ------------------------------------------------------------
     */

    @PrePersist
    protected void onCreate() {

        LocalDateTime now =
                LocalDateTime.now();

        createdAt = now;
        updatedAt = now;

        if (bookingStatus == null) {
            bookingStatus =
                    BookingStatus.PENDING;
        }

        if (paymentStatus == null) {
            paymentStatus =
                    PaymentStatus.UNPAID;
        }

        if (guestCount == null) {
            guestCount = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {

        updatedAt =
                LocalDateTime.now();
    }

    /*
     * ------------------------------------------------------------
     * BOOKING ID
     * ------------------------------------------------------------
     */

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(
            Long bookingId
    ) {
        this.bookingId = bookingId;
    }

    /*
     * ------------------------------------------------------------
     * USER
     * ------------------------------------------------------------
     */

    public User getUser() {
        return user;
    }

    public void setUser(
            User user
    ) {
        this.user = user;
    }

    /*
     * ------------------------------------------------------------
     * BOOKING REFERENCE
     * ------------------------------------------------------------
     */

    public BookingType getBookingType() {
        return bookingType;
    }

    public void setBookingType(BookingType bookingType) {
        this.bookingType = bookingType;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(
            String bookingReference
    ) {
        this.bookingReference =
                bookingReference;
    }

    /*
     * ------------------------------------------------------------
     * TOTAL AMOUNT
     * ------------------------------------------------------------
     */

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(
            BigDecimal totalAmount
    ) {
        this.totalAmount =
                totalAmount;
    }

    /*
     * ------------------------------------------------------------
     * CURRENCY
     * ------------------------------------------------------------
     */

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(
            String currency
    ) {
        this.currency =
                currency;
    }

    /*
     * ------------------------------------------------------------
     * BOOKING STATUS
     * ------------------------------------------------------------
     */

    public BookingStatus getBookingStatus() {
        return bookingStatus;
    }

    public void setBookingStatus(
            BookingStatus bookingStatus
    ) {
        this.bookingStatus =
                bookingStatus;
    }

    /*
     * ------------------------------------------------------------
     * PAYMENT STATUS
     * ------------------------------------------------------------
     */

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(
            PaymentStatus paymentStatus
    ) {
        this.paymentStatus =
                paymentStatus;
    }

    /*
     * ------------------------------------------------------------
     * CREATED AT
     * ------------------------------------------------------------
     */

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /*
     * ------------------------------------------------------------
     * UPDATED AT
     * ------------------------------------------------------------
     */

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /*
     * ------------------------------------------------------------
     * GUEST COUNT
     * ------------------------------------------------------------
     */

    public Integer getGuestCount() {
        return guestCount;
    }

    public void setGuestCount(
            Integer guestCount
    ) {
        this.guestCount =
                guestCount;
    }

    /*
     * ------------------------------------------------------------
     * GUEST NAME
     * ------------------------------------------------------------
     */

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(
            String guestName
    ) {
        this.guestName =
                guestName;
    }

    /*
     * ------------------------------------------------------------
     * GUEST EMAIL
     * ------------------------------------------------------------
     */

    public String getGuestEmail() {
        return guestEmail;
    }

    public void setGuestEmail(
            String guestEmail
    ) {
        this.guestEmail =
                guestEmail;
    }

    /*
     * ------------------------------------------------------------
     * GUEST PHONE
     * ------------------------------------------------------------
     */

    public String getGuestPhone() {
        return guestPhone;
    }

    public void setGuestPhone(
            String guestPhone
    ) {
        this.guestPhone =
                guestPhone;
    }

    /*
     * ------------------------------------------------------------
     * SPECIAL REQUESTS
     * ------------------------------------------------------------
     */

    public String getSpecialRequests() {
        return specialRequests;
    }

    public void setSpecialRequests(
            String specialRequests
    ) {
        this.specialRequests =
                specialRequests;
    }

    /*
     * ------------------------------------------------------------
     * HOLD EXPIRATION
     * ------------------------------------------------------------
     */

    public LocalDateTime getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public void setHoldExpiresAt(
            LocalDateTime holdExpiresAt
    ) {
        this.holdExpiresAt =
                holdExpiresAt;
    }

    /*
     * ------------------------------------------------------------
     * RAZORPAY ORDER ID
     * ------------------------------------------------------------
     */

    public String getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public void setRazorpayOrderId(
            String razorpayOrderId
    ) {
        this.razorpayOrderId =
                razorpayOrderId;
    }

    /*
     * ------------------------------------------------------------
     * RAZORPAY PAYMENT ID
     * ------------------------------------------------------------
     */

    public String getRazorpayPaymentId() {
        return razorpayPaymentId;
    }

    public void setRazorpayPaymentId(
            String razorpayPaymentId
    ) {
        this.razorpayPaymentId =
                razorpayPaymentId;
    }
}