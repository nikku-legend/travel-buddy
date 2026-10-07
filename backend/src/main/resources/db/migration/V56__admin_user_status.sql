-- ============================================================
-- USER SUSPENSION  (FR-31, User Management Ops)
--
-- The admin console promises suspend/reactivate, but users had
-- no state to suspend: a suspended account would have been a
-- comment, not a fact, and login would have accepted the
-- account as happily as ever.
--
-- Default ACTIVE so every existing account keeps working the
-- moment this lands.
-- ============================================================

ALTER TABLE users
    ADD COLUMN status ENUM('ACTIVE', 'SUSPENDED')
        NOT NULL DEFAULT 'ACTIVE';
