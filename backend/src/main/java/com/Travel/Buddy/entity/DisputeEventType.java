package com.Travel.Buddy.entity;

/**
 * The kind of entry on a dispute's audit trail. (FR-28)
 */
public enum DisputeEventType {

    RAISED,

    ASSIGNED,

    STATUS_CHANGED,

    EVIDENCE_ADDED,

    COMMENT_ADDED,

    /*
     * Distinct from STATUS_CHANGED because "we decided" and "we
     * moved it along" mean different things when a partner reads
     * the trail six months later.
     */
    RESOLVED
}