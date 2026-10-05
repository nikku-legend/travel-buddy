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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * A gallery image. (SRS 2.3 section 6.2, and named in the SRS
 * schema list as property_images)
 */
@Entity
@Table(
        name = "property_images",
        indexes = {
                @Index(
                        name = "idx_property_images_property",
                        columnList = "property_id, sort_order"
                )
        }
)
public class PropertyImage {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long imageId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "property_id",
            nullable = false
    )
    private Property property;

    @Column(
            name = "image_url",
            nullable = false,
            length = 500
    )
    private String imageUrl;

    /**
     * Required for accessibility, not decoration. An image with
     * no alt text is invisible to a screen reader, and a hotel
     * gallery is exactly the kind of visual content that needs
     * describing.
     */
    @Column(name = "alt_text", length = 200)
    private String altText;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "is_cover", nullable = false)
    private boolean cover = false;

    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;

    protected PropertyImage() {
    }

    public PropertyImage(
            Property property,
            String imageUrl,
            String altText,
            int sortOrder,
            boolean cover
    ) {
        this.property = property;
        this.imageUrl = imageUrl;
        this.altText = altText;
        this.sortOrder = sortOrder;
        this.cover = cover;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now()
                .truncatedTo(ChronoUnit.MICROS);
    }


    /**
     * Demotes this image from cover, so a newly designated cover
     * is the only one the page leads with.
     */
    public void clearCoverFlag() {
        this.cover = false;
    }

    public Long getImageId() {
        return imageId;
    }

    public Property getProperty() {
        return property;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getAltText() {
        return altText;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isCover() {
        return cover;
    }
}