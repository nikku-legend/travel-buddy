-- ============================================================
-- PARTNER KYC DOCUMENTS
--
-- Every uploaded document tracks its own verification status so
-- an admin can verify, or reject, a single document without
-- discarding the whole application.
--
-- Status flow:
--
--   NOT_UPLOADED -> UPLOADED -> UNDER_REVIEW -> VERIFIED
--                                              |
--                                              +-> REJECTED (reason required)
--
-- `document_type` is stored as free text on purpose: the list of
-- required documents must be configurable by an admin rather than
-- hard-coded forever, exactly as required for the KYC phase.
-- Seeded values live in `kyc_document_types`.
-- ============================================================

CREATE TABLE kyc_document_types (
                                   document_type_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                   partner_type ENUM(
                                       'HOTEL',
                                       'GUIDE',
                                       'CAB'
                                       ) NOT NULL,

                                   document_code VARCHAR(50) NOT NULL,

                                   display_name VARCHAR(100) NOT NULL,

                                   is_mandatory BOOLEAN NOT NULL DEFAULT TRUE,

                                   display_order INT NOT NULL DEFAULT 0,

                                   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                   UNIQUE KEY uq_kyc_document_type (partner_type, document_code),

                                   INDEX idx_kyc_document_types_partner_type (partner_type)
);

INSERT INTO kyc_document_types (partner_type, document_code, display_name, is_mandatory, display_order)
VALUES ('HOTEL', 'IDENTITY_PROOF', 'Owner identity document', TRUE, 1),
       ('HOTEL', 'ADDRESS_PROOF', 'Address proof', TRUE, 2),
       ('HOTEL', 'BUSINESS_PROOF', 'Business registration / GST certificate', TRUE, 3),
       ('HOTEL', 'PROPERTY_OWNERSHIP', 'Property ownership or authorisation evidence', TRUE, 4),
       ('HOTEL', 'BANK_PROOF', 'Bank account proof', FALSE, 5),

       ('GUIDE', 'IDENTITY_PROOF', 'Identity document', TRUE, 1),
       ('GUIDE', 'ADDRESS_PROOF', 'Address proof', TRUE, 2),
       ('GUIDE', 'GUIDE_LICENSE', 'Guide licence or certification', FALSE, 3),
       ('GUIDE', 'BANK_PROOF', 'Bank account proof', FALSE, 4),

       ('CAB', 'IDENTITY_PROOF', 'Driver identity document', TRUE, 1),
       ('CAB', 'DRIVING_LICENSE', 'Driving licence', TRUE, 2),
       ('CAB', 'ADDRESS_PROOF', 'Address proof', TRUE, 3),
       ('CAB', 'VEHICLE_REGISTRATION', 'Vehicle registration certificate (RC)', TRUE, 4),
       ('CAB', 'VEHICLE_INSURANCE', 'Vehicle insurance policy', TRUE, 5),
       ('CAB', 'BANK_PROOF', 'Bank account proof', FALSE, 6);


CREATE TABLE kyc_documents (
                              document_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                              application_id BIGINT NOT NULL,

                              document_code VARCHAR(50) NOT NULL,

                              display_name VARCHAR(100) NOT NULL,

                              -- Storage location of the uploaded file.
                              file_name VARCHAR(255) NULL,

                              file_path VARCHAR(500) NULL,

                              content_type VARCHAR(100) NULL,

                              file_size_bytes BIGINT NULL,

                              status ENUM(
                                  'NOT_UPLOADED',
                                  'UPLOADED',
                                  'UNDER_REVIEW',
                                  'VERIFIED',
                                  'REJECTED'
                                  ) NOT NULL DEFAULT 'NOT_UPLOADED',

                              rejection_reason VARCHAR(500) NULL,

                              uploaded_at TIMESTAMP NULL,

                              verified_at TIMESTAMP NULL,

                              verified_by_user_id BIGINT NULL,

                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                  ON UPDATE CURRENT_TIMESTAMP,

                              CONSTRAINT fk_kyc_documents_application
                                  FOREIGN KEY (application_id)
                                      REFERENCES partner_applications(application_id)
                                      ON DELETE CASCADE,

                              CONSTRAINT fk_kyc_documents_verified_by
                                  FOREIGN KEY (verified_by_user_id)
                                      REFERENCES users(user_id)
                                      ON DELETE SET NULL,

                              UNIQUE KEY uq_kyc_document_application_code (application_id, document_code),

                              INDEX idx_kyc_documents_status (status),
                              INDEX idx_kyc_documents_application (application_id)
);
