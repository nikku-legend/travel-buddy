-- ============================================================
-- V18 - Booking hold and lead guest details
--
-- BUG FIXED HERE:
--
-- This migration previously used:
--
--     ADD COLUMN IF NOT EXISTS hold_expires_at ...
--
-- That is MariaDB syntax. MySQL 8 does NOT support
-- IF NOT EXISTS on ALTER TABLE ... ADD COLUMN, so this
-- migration failed with a syntax error on any database
-- built from scratch.
--
-- It went unnoticed because the development database already had
-- V1-V26 applied, so V18 was never executed. A fresh deployment
-- of Travel Buddy failed at V18.
--
-- Flyway runs each version exactly once, tracked in
-- flyway_schema_history, so the existence guard is unnecessary
-- and is now removed.
-- ============================================================

ALTER TABLE bookings
    ADD COLUMN hold_expires_at TIMESTAMP NULL,
    ADD COLUMN guest_name VARCHAR(150) NULL,
    ADD COLUMN guest_email VARCHAR(150) NULL,
    ADD COLUMN guest_phone VARCHAR(50) NULL,
    ADD COLUMN special_requests TEXT NULL;