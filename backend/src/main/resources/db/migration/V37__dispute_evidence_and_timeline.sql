-- ============================================================
-- DISPUTE EVIDENCE AND TIMELINE  (FR-28)
--
-- Two supporting tables, deliberately append-only.
-- ============================================================

-- ------------------------------------------------------------
-- EVIDENCE
--
-- Photos, receipts and screenshots. Stored as a path rather than
-- a blob for the same reason the KYC documents are: the evidence
-- for a dispute can be large, and a disputed trip may be the
-- subject of a chargeback months later, so the bytes must not
-- live in the transactional database.
--
-- The file is written under a random name and the original is
-- kept only for display. A name derived from user input would let
-- one claimant overwrite another's evidence or escape the
-- upload directory.
-- ------------------------------------------------------------

CREATE TABLE dispute_evidence (
                           evidence_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                           dispute_id BIGINT NOT NULL,

                           uploaded_by_user_id BIGINT NOT NULL,

                           -- What the file is, as a coarse label. Free text
                           -- rather than a strict enum: a claimant
                           -- photographing a cracked wall should not be
                           -- forced into a wrong bucket to be allowed to
                           -- upload at all.
                           description VARCHAR(200) NULL,

                           stored_path VARCHAR(400) NOT NULL,

                           original_filename VARCHAR(255) NULL,

                           content_type VARCHAR(120) NULL,

                           file_size_bytes BIGINT NULL,

                           created_at TIMESTAMP(6)
                               NOT NULL
                               DEFAULT CURRENT_TIMESTAMP(6),

                           -- A dispute that is closed must keep its evidence.
                           -- CASCADE here would let deleting a rejected
                           -- claim erase the reason it was rejected.
                           CONSTRAINT fk_dispute_evidence_dispute
                               FOREIGN KEY (dispute_id)
                                   REFERENCES disputes(dispute_id)
                                   ON DELETE CASCADE,

                           CONSTRAINT fk_dispute_evidence_user
                               FOREIGN KEY (uploaded_by_user_id)
                                   REFERENCES users(user_id)
                                   ON DELETE RESTRICT,

                           CONSTRAINT chk_dispute_evidence_size
                               CHECK (
                                   file_size_bytes IS NULL
                                       OR file_size_bytes > 0
                               ),

                           INDEX idx_dispute_evidence_dispute (dispute_id)
) ENGINE = InnoDB;


-- ------------------------------------------------------------
-- TIMELINE
--
-- The audit trail. Every state change, assignment, evidence
-- upload and comment appends one row.
--
-- There are deliberately no UPDATE or DELETE paths in the
-- service for this table, and no cascade from disputes: a
-- dispute row is retained even if the underlying booking is
-- later removed, because the ruling explains the money.
--
-- from_status is nullable because the first event has no prior
-- state. to_status is nullable for events that do not change
-- status at all, such as evidence being uploaded.
-- ------------------------------------------------------------

CREATE TABLE dispute_timeline (
                         timeline_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                         dispute_id BIGINT NOT NULL,

                         event_type ENUM(
                             'RAISED',
                             'ASSIGNED',
                             'STATUS_CHANGED',
                             'EVIDENCE_ADDED',
                             'COMMENT_ADDED',
                             'RESOLVED'
                             ) NOT NULL,

                         actor_user_id BIGINT NULL,

                         from_status VARCHAR(30) NULL,

                         to_status VARCHAR(30) NULL,

                         note VARCHAR(1000) NULL,

                         created_at TIMESTAMP(6)
                             NOT NULL
                             DEFAULT CURRENT_TIMESTAMP(6),

                         CONSTRAINT fk_dispute_timeline_dispute
                             FOREIGN KEY (dispute_id)
                                 REFERENCES disputes(dispute_id)
                                 ON DELETE RESTRICT,

                         -- Deliberately no ON DELETE clause. If an
                         -- account is erased for GDPR the trail must
                         -- survive, and actor_user_id simply becomes
                         -- a dangling id that no longer resolves.
                         INDEX idx_dispute_timeline_dispute (
                             dispute_id,
                             created_at
                             )
) ENGINE = InnoDB;