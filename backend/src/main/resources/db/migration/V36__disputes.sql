-- ============================================================
-- DISPUTES  (FR-28)
--
-- A dispute is what a traveller raises when a paid trip did not
-- happen as sold, and what an admin rules on.
--
-- The design goal is auditability. Money can move and statuses
-- can change, but dispute_timeline is append-only: every state
-- change is a row that can never be edited or deleted. When a
-- partner asks six months later why a refund was paid, this is
-- the record that answers it.
-- ============================================================

CREATE TABLE disputes (
                       dispute_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                       -- The booking being disputed. NOT NULL and
                       -- indexed because "show me the open disputes
                       -- for this booking" is the check that stops a
                       -- traveller opening five claims on one trip.
                       booking_id BIGINT NOT NULL,

                       -- Who raised it. The claimant, always a
                       -- traveller who actually paid.
                       raised_by_user_id BIGINT NOT NULL,

                       -- The partner being complained about. Denormalised
                       -- from the property so the partner portal can
                       -- list their disputes without joining through
                       -- booking -> reservation -> room type -> property.
                       partner_user_id BIGINT NULL,

                       category ENUM(
                           'SERVICE_QUALITY',
                           'PROPERTY_NOT_AS_DESCRIBED',
                           'OVERCHARGING',
                           'SAFETY_CONCERN',
                           'CLEANLINESS',
                           'BOOKING_NOT_HONOURED',
                           'LATE_CHECK_IN',
                           'UNAUTHORISED_CANCELLATION',
                           'OTHER'
                           ) NOT NULL,

                       status ENUM(
                           'OPEN',
                           'UNDER_REVIEW',
                           'AWAITING_PARTNER_RESPONSE',
                           'RESOLVED',
                           'REJECTED',
                           'WITHDRAWN'
                           ) NOT NULL DEFAULT 'OPEN',

                       subject VARCHAR(200) NOT NULL,

                       description TEXT NOT NULL,

                       -- What the traveller is asking for. Capped at the
                       -- amount actually paid, checked in the service
                       -- and again by a CHECK below so no code path can
                       -- promise more money than was taken.
                       requested_amount DECIMAL(12, 2) NOT NULL,

                       assigned_to_user_id BIGINT NULL,

                       assigned_at TIMESTAMP(6) NULL,

                       -- The ruling. NULL until the dispute is closed.
                       resolution ENUM(
                           'FULL_REFUND',
                           'PARTIAL_REFUND',
                           'NO_REFUND',
                           'CREDIT_NOTE'
                           ) NULL,

                       -- How much is actually returned. Zero is a legal
                       -- outcome: a claim can be upheld with no money
                       -- moving, and the column records that honestly
                       -- rather than being left null.
                       resolved_amount DECIMAL(12, 2) NULL,

                       resolution_notes TEXT NULL,

                       resolved_at TIMESTAMP(6) NULL,

                       -- Claimants may add context while the dispute is
                       -- still open. It is cleared, not overwritten,
                       -- when the dispute is resolved.
                       withdrawn_at TIMESTAMP(6) NULL,

                       created_at TIMESTAMP(6)
                           NOT NULL
                           DEFAULT CURRENT_TIMESTAMP(6),

                       updated_at TIMESTAMP(6)
                           NOT NULL
                           DEFAULT CURRENT_TIMESTAMP(6)
                           ON UPDATE CURRENT_TIMESTAMP(6),

                       -- Optimistic locking counter for the Dispute
                       -- entity's @Version field.
                       --
                       -- Two moderators ruling on the same dispute is
                       -- routine. Without this the second UPDATE would
                       -- silently overwrite the first ruling, and the
                       -- audit trail would show one decision while the
                       -- money reflected another. Hibernate bumps the
                       -- value on every write and rejects a stale one.
                       version BIGINT NOT NULL DEFAULT 0,

                       CONSTRAINT fk_disputes_booking
                           FOREIGN KEY (booking_id)
                               REFERENCES bookings(booking_id)
                               ON DELETE RESTRICT,

                       -- A cascade would destroy the audit trail of a
                       -- payment that may still be contested.
                       CONSTRAINT fk_disputes_raised_by
                           FOREIGN KEY (raised_by_user_id)
                               REFERENCES users(user_id)
                               ON DELETE RESTRICT,

                       CONSTRAINT fk_disputes_partner
                           FOREIGN KEY (partner_user_id)
                               REFERENCES users(user_id)
                               ON DELETE SET NULL,

                       CONSTRAINT fk_disputes_assigned_to
                           FOREIGN KEY (assigned_to_user_id)
                               REFERENCES users(user_id)
                               ON DELETE SET NULL,

                       CONSTRAINT chk_disputes_requested_non_negative
                           CHECK (requested_amount >= 0),

                       -- The invariant that actually matters: a dispute
                       -- can never award more than the claimant asked
                       -- for, whatever a future code change does.
                       CONSTRAINT chk_disputes_resolved_not_over_requested
                           CHECK (
                               resolved_amount IS NULL
                                   OR resolved_amount <= requested_amount
                               ),

                       -- A closed dispute must say how it was decided.
                       -- A resolution without a ruling is exactly the
                       -- state a dispute must never rest in.
                       CONSTRAINT chk_disputes_closed_has_ruling
                           CHECK (
                               status NOT IN ('RESOLVED', 'REJECTED')
                                   OR (
                                       resolution IS NOT NULL
                                           AND resolved_at IS NOT NULL
                                       )
                               ),

                       INDEX idx_disputes_booking (booking_id),
                       INDEX idx_disputes_raised_by (raised_by_user_id),
                       INDEX idx_disputes_partner (partner_user_id),
                       INDEX idx_disputes_status (status),

                       -- The admin work queue. Narrow, and ordered the
                       -- way an admin works: oldest first.
                       INDEX idx_disputes_queue (status, created_at)
) ENGINE = InnoDB;