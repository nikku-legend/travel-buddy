-- ============================================================
-- DIGITAL VOUCHER AND QR VERIFICATION  (FR-24)
--
-- After a booking is confirmed the guest needs something the front
-- desk can actually check. This is that:
--
--     Booking -> Voucher -> QR -> hotel scans -> verified
--
-- Design decisions that matter:
--
-- 1. The QR does NOT contain guest data. It carries an opaque
--    signed token. A photographed voucher therefore leaks nothing,
--    and the hotel only learns who the guest is AFTER the token
--    verifies.
--
-- 2. The token is HMAC-signed and compared in constant time, so a
--    voucher number cannot be guessed or forged.
--
-- 3. A voucher is single use. Check-in consumes it, so replaying
--    the same QR will not admit a second guest.
--
-- 4. Vouchers are voided, never deleted, so a cancelled booking
--    leaves an auditable trail.
-- ============================================================

CREATE TABLE booking_vouchers (
                        voucher_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                        -- One voucher per booking.
                        booking_id BIGINT NOT NULL,

                        -- Short human readable code, for the phone
                        -- fallback when a camera will not scan.
                        voucher_code VARCHAR(24) NOT NULL,

                        status ENUM(
                            'ACTIVE',
                            'USED',
                            'VOID',
                            'EXPIRED'
                            ) NOT NULL DEFAULT 'ACTIVE',

                        -- Denormalised so the front-desk view stays
                        -- readable even if the booking is archived.
                        guest_name VARCHAR(150) NULL,

                        valid_from DATE NOT NULL,

                        valid_until DATE NOT NULL,

                        issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        -- Single-use bookkeeping.
                        consumed_at TIMESTAMP NULL,

                        consumed_by_stay_id BIGINT NULL,

                        voided_at TIMESTAMP NULL,

                        void_reason VARCHAR(500) NULL,

                        -- How often staff scanned it. A voucher being
                        -- verified repeatedly but never consumed is a
                        -- signal worth surfacing to risk monitoring
                        -- (FR-36).
                        verification_count INT NOT NULL DEFAULT 0,

                        last_verified_at TIMESTAMP NULL,

                        CONSTRAINT chk_voucher_dates
                            CHECK (valid_until >= valid_from),

                        CONSTRAINT chk_voucher_verification_count
                            CHECK (verification_count >= 0),

                        CONSTRAINT fk_vouchers_booking
                            FOREIGN KEY (booking_id)
                                REFERENCES bookings(booking_id)
                                ON DELETE CASCADE,

                        CONSTRAINT fk_vouchers_consumed_by_stay
                            FOREIGN KEY (consumed_by_stay_id)
                                REFERENCES room_stays(stay_id)
                                ON DELETE SET NULL,

                        -- Re-issuing a voucher must replace the old
                        -- one, so the booking is unique.
                        UNIQUE KEY uq_vouchers_booking (booking_id),

                        -- The code is the phone fallback, so it must be
                        -- unique and guessable only by brute force.
                        UNIQUE KEY uq_vouchers_code (voucher_code),

                        INDEX idx_vouchers_status (status),
                        INDEX idx_vouchers_valid_until (valid_until)
);