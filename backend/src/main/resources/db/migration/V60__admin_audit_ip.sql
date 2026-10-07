-- ============================================================
-- AUDIT LOG: SOURCE IP  (FR-30 / SRS auditability requirement)
--
-- The audit viewer promises "admin id, IP address, timestamp".
-- actor_user_id and created_at already exist; this adds the
-- remaining third so a privileged action can be traced back to
-- where it came from, not only to who issued it.
--
-- VARCHAR(45): the longest possible textual IPv6 address.
-- Nullable because records written outside a web request
-- (background jobs, tests) have no caller IP to capture.
-- ============================================================

ALTER TABLE admin_audit_log
    ADD COLUMN ip_address VARCHAR(45) NULL;