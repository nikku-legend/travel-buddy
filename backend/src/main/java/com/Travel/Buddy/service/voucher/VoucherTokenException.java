package com.Travel.Buddy.service.voucher;

/**
 * A scanned voucher failed to verify.
 *
 * <p>Extends {@link RuntimeException} rather than
 * {@code IllegalArgumentException} so the global handler can map it
 * to a clean 400 without swallowing genuine bad input elsewhere.
 */
public class VoucherTokenException
        extends RuntimeException {

    public VoucherTokenException(String message) {
        super(message);
    }
}