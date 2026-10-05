-- ============================================================
-- NOTIFICATIONS  (FR-25)
--
-- The SRS lists the events that must notify a user:
--
--   Registration, Booking confirmed, Payment success,
--   Payment failure, Cancellation, Refund, Guide booking,
--   Transport assignment, Check-in reminder, Review reminder,
--   Partner approval, KYC result
--
-- Two channels, one table. In-app is implemented now; EMAIL is
-- modelled and handed to a delivery gateway so an SMTP or
-- transactional provider can be attached without a schema change.
--
-- dedupe_key is the important column. Events are retried (a
-- payment webhook fires twice, a partner re-approves, a
-- scheduled reminder runs again) and a traveller receiving the
-- same "your booking is confirmed" email three times destroys
-- trust in the platform. A UNIQUE key makes notification
-- delivery idempotent.
-- ============================================================

CREATE TABLE notifications (
                        notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                        user_id BIGINT NOT NULL,

                        type ENUM(
                            'WELCOME',
                            'PARTNER_APPLICATION_SUBMITTED',
                            'PARTNER_APPLICATION_APPROVED',
                            'PARTNER_APPLICATION_REJECTED',
                            'KYC_DOCUMENT_REJECTED',
                            'PROPERTY_SUBMITTED',
                            'PROPERTY_APPROVED',
                            'PROPERTY_REJECTED',
                            'PROPERTY_SUSPENDED',
                            'BOOKING_CONFIRMED',
                            'PAYMENT_SUCCESS',
                            'PAYMENT_FAILED',
                            'BOOKING_CANCELLED',
                            'REFUND_PROCESSED',
                            'CHECK_IN_REMINDER',
                            'REVIEW_SUBMITTED',
                            'REVIEW_PUBLISHED',
                            'REVIEW_REJECTED',
                            'GUIDE_BOOKING',
                            'TRANSPORT_ASSIGNED',
                            'DISPUTE_RAISED',
                            'DISPUTE_RESOLVED'
                            ) NOT NULL,

                        channel ENUM(
                            'IN_APP',
                            'EMAIL'
                            ) NOT NULL DEFAULT 'IN_APP',

                        delivery_status ENUM(
                            'PENDING',
                            'SENT',
                            'FAILED'
                            ) NOT NULL DEFAULT 'PENDING',

                        title VARCHAR(150) NOT NULL,

                        body VARCHAR(1000) NULL,

                        -- Polymorphic pointer to whatever the notice is
                        -- about, so the UI can deep link. No foreign
                        -- key for the same reason reviews have none.
                        related_type VARCHAR(40) NULL,

                        related_id BIGINT NULL,

                        -- Where the UI should take the user.
                        action_url VARCHAR(300) NULL,

                        -- Idempotency key. NULL disables dedupe for
                        -- genuinely repeatable events such as a daily
                        -- check-in reminder.
                        dedupe_key VARCHAR(120) NULL,

                        -- Read state is per user and separate from
                        -- delivery: an email can be SENT while the
                        -- in-app card is still unread.
                        read_at TIMESTAMP(6) NULL,

                        sent_at TIMESTAMP(6) NULL,

                        failure_reason VARCHAR(300) NULL,

                        -- Sub-second precision is required, not decorative.
                        --
                        -- MySQL defaults TIMESTAMP to zero fractional
                        -- digits, which makes two notifications raised
                        -- in the same second tie. "Newest first" would
                        -- then return them in whatever order the index
                        -- happened to produce, and the inbox would
                        -- visibly reshuffle on refresh.
                        created_at TIMESTAMP(6)
                            NOT NULL
                            DEFAULT CURRENT_TIMESTAMP(6),

                        CONSTRAINT fk_notifications_user
                            FOREIGN KEY (user_id)
                                REFERENCES users(user_id)
                                ON DELETE CASCADE,

                        UNIQUE KEY uq_notifications_dedupe (dedupe_key),

                        INDEX idx_notifications_user_created (
                            user_id, created_at
                        ),

                        -- The unread badge query filters on exactly
                        -- this, so it gets its own index.
                        INDEX idx_notifications_unread (
                            user_id, read_at
                        ),

                        INDEX idx_notifications_type (type),
                        INDEX idx_notifications_related (
                            related_type, related_id
                        )
);