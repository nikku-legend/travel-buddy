package com.Travel.Buddy.entity;

/**
 * Whether the account may still be used. (FR-31)
 *
 * <p>SUSPENDED is deliberately separate from a role: suspension
 * is a temporary enforcement action taken against the account,
 * not a change of what the account is. The role stays so the
 * user's history and permissions are intact when (not if) the
 * suspension is lifted.
 */
public enum UserStatus {

    ACTIVE,

    SUSPENDED
}