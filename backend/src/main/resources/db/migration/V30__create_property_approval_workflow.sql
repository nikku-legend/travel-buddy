-- ============================================================
-- PROPERTY APPROVAL WORKFLOW  (FR-20)
--
-- A hotel partner must NOT be able to publish a property
-- immediately. The flow is:
--
--   Partner creates property
--          |
--          v
--   status = DRAFT            (editable, invisible to travelers)
--          |
--          | partner submits
--          v
--   PENDING_APPROVAL          (invisible to travelers)
--          |
--          +---- admin approves ----> APPROVED  (goes live)
--          |
--          +---- admin rejects -----> REJECTED  (reason required,
--          |                              partner corrects and
--          |                              resubmits)
--          |
--          +---- admin suspends ----> SUSPENDED (was live, pulled)
--
-- `is_verified` is KEPT and kept in sync with this state so the
-- existing read queries (which filter on is_verified) keep working.
-- Having both is deliberate: status is the workflow truth,
-- is_verified is the read-optimised projection of it.
-- ============================================================

ALTER TABLE properties
    ADD COLUMN status ENUM(
        'DRAFT',
        'PENDING_APPROVAL',
        'APPROVED',
        'REJECTED',
        'SUSPENDED'
    ) NOT NULL DEFAULT 'DRAFT' AFTER property_type,

    ADD COLUMN submitted_at TIMESTAMP NULL AFTER status,

    ADD COLUMN reviewed_at TIMESTAMP NULL AFTER submitted_at,

    ADD COLUMN reviewed_by_user_id BIGINT NULL AFTER reviewed_at,

    ADD COLUMN rejection_reason VARCHAR(500) NULL AFTER reviewed_by_user_id,

    ADD CONSTRAINT fk_properties_reviewed_by
        FOREIGN KEY (reviewed_by_user_id)
            REFERENCES users(user_id)
            ON DELETE SET NULL;

-- ============================================================
-- BACKFILL
--
-- Properties that are already verified and active were live before
-- this migration, so they must stay live. Everything else becomes an
-- explicitly pending item rather than silently vanishing.
-- ============================================================

UPDATE properties
SET status = 'APPROVED',
    is_verified = TRUE
WHERE is_verified = TRUE
  AND is_active = TRUE;

UPDATE properties
SET status = 'PENDING_APPROVAL'
WHERE status = 'DRAFT'
  AND is_verified = FALSE;

-- Only APPROVED properties are bookable and publicly visible.
CREATE INDEX idx_properties_status ON properties (status);

CREATE INDEX idx_properties_status_state
    ON properties (status, state_id);