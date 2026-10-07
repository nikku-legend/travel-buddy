-- ============================================================
-- ADMIN AUDIT LOG  (FR-30, SRS auditability requirement)
--
-- "All sensitive admin and financial actions must be auditable"
-- -- KYC decisions, listing decisions, moderation, suspensions,
-- refund approvals and destination edits. Until now the only
-- audit trail in the system was the dispute timeline, which
-- covers disputes and nothing else.
--
-- Append-only by design: no updated_at column and no status to
-- rewrite. An entry is written once and read forever.
-- ============================================================

CREATE TABLE admin_audit_log (
    audit_id       BIGINT       NOT NULL AUTO_INCREMENT,
    actor_user_id  BIGINT       NOT NULL,
    action         VARCHAR(60)  NOT NULL,
    entity_type    VARCHAR(40)  NOT NULL,
    entity_id      BIGINT       NULL,
    detail         VARCHAR(1000) NULL,
    created_at     DATETIME(6)  NOT NULL,

    PRIMARY KEY (audit_id),

    CONSTRAINT fk_audit_log_actor
        FOREIGN KEY (actor_user_id)
        REFERENCES users (user_id)
        ON DELETE RESTRICT,

    INDEX idx_audit_log_created (created_at),
    INDEX idx_audit_log_entity (entity_type, entity_id)
);
