package com.Travel.Buddy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * A shared amenity in the catalogue. (SRS 2.3 section 6.2)
 *
 * <p>A catalogue rather than free text on each property, so
 * "Wi-Fi" means the same thing everywhere. Without that, "free
 * wifi", "complimentary wifi" and "WIFI" would be three
 * different amenities and no search would ever match.
 *
 * <p>The code is the identity; the label is display copy and can
 * be reworded without a data migration.
 */
@Entity
@Table(
        name = "amenities",
        indexes = {
                @Index(
                        name = "idx_amenities_category",
                        columnList = "category, label"
                )
        }
)
public class Amenity {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Integer amenityId;

    @Column(nullable = false, length = 40, unique = true)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(name = "icon_name", length = 40)
    private String iconName;

    protected Amenity() {
    }

    /**
     * The catalogue is platform-managed reference data, seeded by
     * migration and created here only when a caller needs to
     * establish a row it will then link to. Partners link to
     * existing codes; they do not mint amenities.
     *
     * <p>Present because the entity is otherwise immutable with
     * no builder, which would leave the table unreachable from
     * Java and so untestable.
     */
    public Amenity(
            String code,
            String label,
            String category,
            String iconName
    ) {
        this.code = code;
        this.label = label;
        this.category = category;
        this.iconName = iconName;
    }

    public Integer getAmenityId() {
        return amenityId;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getCategory() {
        return category;
    }

    public String getIconName() {
        return iconName;
    }
}