package com.Travel.Buddy.entity;

/**
 * What a review is about. (FR-26)
 *
 * <p>Each constant names the table its {@code targetId} points at.
 * There is deliberately no foreign key: one column cannot reference
 * four tables, and four nullable columns would allow a review to
 * claim to be about a hotel while referencing a guide.
 * Integrity is enforced in ReviewService, which resolves the
 * target on every write.
 */
public enum ReviewTargetType {

    /** targetId -> properties.property_id */
    HOTEL,

    /** targetId -> guides.guide_id */
    GUIDE,

    /** targetId -> cabs.cab_id */
    CAB,

    /** targetId -> tourist_places.place_id */
    DESTINATION
}