package com.Travel.Buddy.entity;

/**
 * Lifecycle of a digital voucher. (FR-24)
 *
 * <pre>
 *   ACTIVE   -> valid, not yet used
 *   USED     -> consumed by check-in; a replay will be refused
 *   VOID     -> booking cancelled or changed; permanently refused
 *   EXPIRED  -> stayed past valid_until
 * </pre>
 *
 * <p>Vouchers are voided rather than deleted so that a cancelled
 * booking still leaves an auditable record that a voucher existed.
 */
public enum VoucherStatus {

    ACTIVE,

    USED,

    VOID,

    EXPIRED;

    public boolean isUsable() {
        return this == ACTIVE;
    }
}