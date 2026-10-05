-- ============================================================
-- PARTNER APPLICATIONS
--
-- Becoming a Travel Buddy partner NEVER creates a new account.
--
-- The flow is:
--
--   Existing traveler account
--          |
--          v
--   Choose partner type (HOTEL / GUIDE / CAB)
--          |
--          v
--   Fill application + upload KYC documents
--          |
--          v
--   status = PENDING_REVIEW
--          |
--          v
--   Admin review
--      /            \
--  REJECTED        APPROVED
--      |                |
--      v                v
-- PENDING_CORRECTION   ROLE_<TYPE>_PARTNER granted in user_roles
--      |                |
--      +---> resubmit    v
--                  Partner dashboard unlocks
--
-- NOTE: application status is deliberately SEPARATE from the
-- user role. A suspended partner keeps ROLE_HOTEL_PARTNER but
-- has status SUSPENDED, so the role alone never grants access.
-- ============================================================

CREATE TABLE partner_applications (
                                  application_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                  user_id BIGINT NOT NULL,

                                  partner_type ENUM(
                                      'HOTEL',
                                      'GUIDE',
                                      'CAB'
                                      ) NOT NULL,

                                  status ENUM(
                                      'DRAFT',
                                      'PENDING_REVIEW',
                                      'UNDER_REVIEW',
                                      'APPROVED',
                                      'REJECTED',
                                      'PENDING_CORRECTION',
                                      'WITHDRAWN'
                                      ) NOT NULL DEFAULT 'DRAFT',

                                  -- ----------------------------------------------------
                                  -- Business / service information
                                  -- ----------------------------------------------------

                                  business_name VARCHAR(150) NULL,

                                  contact_phone VARCHAR(20) NULL,

                                  address_line VARCHAR(255) NULL,

                                  city VARCHAR(100) NULL,

                                  state VARCHAR(100) NULL,

                                  postal_code VARCHAR(20) NULL,

                                  -- GST / PAN / trade licence, where applicable.
                                  tax_identifier VARCHAR(50) NULL,

                                  -- Free-form partner specific description.
                                  -- Structured detail lives in the partner tables
                                  -- (hotel_partners / guide_partners / cab_partners).
                                  additional_info VARCHAR(1000) NULL,

                                  -- ----------------------------------------------------
                                  -- Bank details for settlement
                                  -- ----------------------------------------------------

                                  bank_account_name VARCHAR(150) NULL,

                                  bank_account_number VARCHAR(50) NULL,

                                  bank_ifsc VARCHAR(20) NULL,

                                  -- ----------------------------------------------------
                                  -- Workflow timestamps
                                  -- ----------------------------------------------------

                                  submitted_at TIMESTAMP NULL,

                                  reviewed_at TIMESTAMP NULL,

                                  reviewed_by_user_id BIGINT NULL,

                                  -- Populated only when status is REJECTED or
                                  -- PENDING_CORRECTION. Always shown to the applicant.
                                  rejection_reason VARCHAR(500) NULL,

                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

                                  CONSTRAINT fk_partner_applications_user
                                      FOREIGN KEY (user_id)
                                          REFERENCES users(user_id)
                                          ON DELETE CASCADE,

                                  CONSTRAINT fk_partner_applications_reviewed_by
                                      FOREIGN KEY (reviewed_by_user_id)
                                          REFERENCES users(user_id)
                                          ON DELETE SET NULL,

                                  -- One user may apply for each partner type, but
                                  -- may hold several partner types overall.
                                  UNIQUE KEY uq_partner_application_user_type (user_id, partner_type),

                                  INDEX idx_partner_applications_status (status),
                                  INDEX idx_partner_applications_type (partner_type),
                                  INDEX idx_partner_applications_user (user_id)
);
