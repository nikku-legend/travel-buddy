package com.Travel.Buddy.entity;

/**
 * Verification state of a single uploaded KYC document.
 *
 * <pre>
 *   NOT_UPLOADED -> UPLOADED -> UNDER_REVIEW -> VERIFIED
 *                                             |
 *                                             +-> REJECTED
 * </pre>
 *
 * <p>A rejected document can be replaced, which returns it to UPLOADED.
 */
public enum KycDocumentStatus {

    NOT_UPLOADED,

    UPLOADED,

    UNDER_REVIEW,

    VERIFIED,

    REJECTED;

    public boolean isResolved() {
        return this == VERIFIED;
    }
}
