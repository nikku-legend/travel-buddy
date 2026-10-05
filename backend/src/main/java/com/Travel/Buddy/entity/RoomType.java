package com.Travel.Buddy.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "room_types",
        indexes = {
                @Index(
                        name = "idx_room_types_property",
                        columnList = "property_id"
                ),
                @Index(
                        name = "idx_room_types_active",
                        columnList = "is_active"
                )
        }
)
public class RoomType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_type_id")
    private Long roomTypeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "property_id",
            nullable = false
    )
    private Property property;

    @Column(
            name = "category_name",
            nullable = false,
            length = 150
    )
    private String categoryName;

    @Column(
            name = "max_occupancy",
            nullable = false
    )
    private Integer maxOccupancy = 4;

    @Column(
            name = "base_price_per_night",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal basePrice;

    @Column(
            name = "currency_code",
            nullable = false,
            length = 3
    )
    private String currency = "INR";

    @Column(
            name = "total_inventory",
            nullable = false
    )
    private Integer totalInventory;

    @Column(
            name = "is_active",
            nullable = false
    )
    private Boolean active = true;

    public RoomType() {
    }

    public Long getRoomTypeId() {
        return roomTypeId;
    }

    public void setRoomTypeId(Long roomTypeId) {
        this.roomTypeId = roomTypeId;
    }

    public Property getProperty() {
        return property;
    }

    public void setProperty(Property property) {
        this.property = property;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Integer getMaxOccupancy() {
        return maxOccupancy;
    }

    public void setMaxOccupancy(Integer maxOccupancy) {
        this.maxOccupancy = maxOccupancy;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Integer getTotalInventory() {
        return totalInventory;
    }

    public void setTotalInventory(Integer totalInventory) {
        this.totalInventory = totalInventory;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
