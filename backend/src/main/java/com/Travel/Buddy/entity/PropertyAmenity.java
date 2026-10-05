package com.Travel.Buddy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * An amenity offered by one property. (SRS 2.3 section 6.2)
 *
 * <p>The pair of ids is the primary key, so a property cannot
 * list "Wi-Fi" twice under the same code. The join carries the
 * property-specific detail the catalogue cannot: whether it is
 * free, and whether it must be arranged in advance.
 */
@Entity
@Table(name = "property_amenities")
public class PropertyAmenity {

    @Embeddable
    public static class Id implements java.io.Serializable {

        private Long propertyId;

        private Integer amenityId;

        protected Id() {
        }

        public Id(Long propertyId, Integer amenityId) {
            this.propertyId = propertyId;
            this.amenityId = amenityId;
        }

        public Long getPropertyId() {
            return propertyId;
        }

        public Integer getAmenityId() {
            return amenityId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Id that)) {
                return false;
            }
            return java.util.Objects.equals(
                    propertyId, that.propertyId
            ) && java.util.Objects.equals(
                    amenityId, that.amenityId
            );
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(
                    propertyId, amenityId
            );
        }
    }

    @EmbeddedId
    private Id id;

    @MapsId("propertyId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @MapsId("amenityId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "amenity_id", nullable = false)
    private Amenity amenity;

    @Column(name = "is_free", nullable = false)
    private boolean free = true;

    /**
     * An amenity that must be arranged in advance rather than
     * used on arrival. Affects how a traveller reads the
     * description, so it is stored rather than implied by
     * category.
     */
    @Column(name = "requires_booking", nullable = false)
    private boolean requiresBooking = false;

    @Column(length = 200)
    private String note;

    protected PropertyAmenity() {
    }

    public PropertyAmenity(
            Property property,
            Amenity amenity,
            boolean free,
            boolean requiresBooking,
            String note
    ) {
        this.property = property;
        this.amenity = amenity;
        this.free = free;
        this.requiresBooking = requiresBooking;
        this.note = note;
        this.id = new Id(
                property.getPropertyId(),
                amenity.getAmenityId()
        );
    }

    public Id getId() {
        return id;
    }

    public Property getProperty() {
        return property;
    }

    public Amenity getAmenity() {
        return amenity;
    }

    public boolean isFree() {
        return free;
    }

    public boolean isRequiresBooking() {
        return requiresBooking;
    }

    public String getNote() {
        return note;
    }
}