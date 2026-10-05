package com.Travel.Buddy.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "properties",
        indexes = {
                @Index(
                        name = "idx_properties_state",
                        columnList = "state_id"
                ),
                @Index(
                        name = "idx_properties_partner",
                        columnList = "partner_id"
                ),
                @Index(
                        name = "idx_properties_verified",
                        columnList = "is_verified"
                ),
                @Index(
                        name = "idx_properties_active",
                        columnList = "is_active"
                )
        }
)
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "property_id")
    private Long propertyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "partner_id",
            nullable = false
    )
    private User partner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "state_id",
            nullable = false
    )
    private State state;

    /**
     * Added for SRS 2.2. Nullable because the column postdates
     * existing rows: a property with no city is still addressable
     * through its state, and the planner treats "city unknown" as
     * "cannot be recommended nearby" rather than as an error.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @Column(
            name = "name",
            nullable = false,
            length = 200
    )
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "property_type",
            nullable = false
    )
    private PropertyType propertyType;

    /*
     * ============================================================
     * APPROVAL WORKFLOW
     *
     * `status` is the workflow truth. `is_verified` further down is a
     * projection of it that the existing read queries filter on, so the
     * two must always be changed together by the service.
     * ============================================================
     */

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PropertyStatus status = PropertyStatus.DRAFT;

    @Column(name = "submitted_at")
    private java.time.LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private java.time.LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "reviewed_by_user_id",
            foreignKey = @ForeignKey(name = "fk_properties_reviewed_by")
    )
    private User reviewedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(
            name = "address",
            nullable = false,
            length = 500
    )
    private String address;

    @Column(
            name = "description",
            columnDefinition = "TEXT"
    )
    private String description;

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

    /**
     * Editorial "featured" flag for Stay Discovery on Home.
     *
     * <p>Separate from quality, which is derived from the review
     * summary. Featured is editorial; quality is measured, and
     * conflating the two would let a marketing flag pass off an
     * unrated property as a trusted one.
     */
    @Column(name = "is_featured", nullable = false)
    private boolean featured = false;

    @Column(
            name = "is_verified",
            nullable = false
    )
    private Boolean verified = false;

    @Column(
            name = "is_active",
            nullable = false
    )
    private Boolean active = true;

    public Property() {
    }

    public Long getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(Long propertyId) {
        this.propertyId = propertyId;
    }

    public User getPartner() {
        return partner;
    }

    public void setPartner(User partner) {
        this.partner = partner;
    }

    public boolean isFeatured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
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

    public PropertyType getPropertyType() {
        return propertyType;
    }

    public void setPropertyType(PropertyType propertyType) {
        this.propertyType = propertyType;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public PropertyStatus getStatus() {
        return status;
    }

    public void setStatus(PropertyStatus status) {
        this.status = status;
    }

    public java.time.LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(
            java.time.LocalDateTime submittedAt
    ) {
        this.submittedAt = submittedAt;
    }

    public java.time.LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(
            java.time.LocalDateTime reviewedAt
    ) {
        this.reviewedAt = reviewedAt;
    }

    public User getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(User reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}
