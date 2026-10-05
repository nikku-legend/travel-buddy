package com.Travel.Buddy.entity;

/**
 * Review state of a property.
 *
 * <pre>
 *   DRAFT            partner is still editing, invisible to travelers
 *   PENDING_APPROVAL waiting for an admin decision
 *   APPROVED         live and bookable
 *   REJECTED         admin declined, reason shown to the partner
 *   SUSPENDED        previously live, pulled by an admin
 * </pre>
 *
 * <p>This is the workflow source of truth. {@code is_verified} is a
 * read-optimised projection of {@code APPROVED} and must be kept in
 * sync by the service, never set independently.
 */
public enum PropertyStatus {

    DRAFT,

    PENDING_APPROVAL,

    APPROVED,

    REJECTED,

    SUSPENDED;

    /**
     * Only these states may be edited by the owning partner.
     */
    public boolean isEditableByPartner() {
        return this == DRAFT || this == REJECTED;
    }

    /**
     * Only APPROVED properties are publicly visible and bookable.
     */
    public boolean isLive() {
        return this == APPROVED;
    }
}