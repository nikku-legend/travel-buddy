package com.Travel.Buddy.entity;

/**
 * Review lifecycle, including moderation. (FR-26, FR-29)
 *
 * <pre>
 *   PENDING   submitted, awaiting a moderator
 *   PUBLISHED visible to everyone
 *   REJECTED  refused by a moderator, with a reason
 *   FLAGGED   was published, then reported by travellers
 * </pre>
 *
 * <p>Nothing is visible to other users until PUBLISHED, so a
 * review cannot be used to harass a partner before a human has
 * looked at it.
 */
public enum ReviewStatus {

    PENDING,

    PUBLISHED,

    REJECTED,

    FLAGGED;

    public boolean isVisible() {
        return this == PUBLISHED;
    }
}