CREATE TABLE booking_cancellations (
                                       cancellation_id BIGINT NOT NULL AUTO_INCREMENT,

                                       booking_id BIGINT NOT NULL,

                                       cancelled_by_user_id BIGINT NOT NULL,

                                       cancellation_reason VARCHAR(100) NOT NULL,

                                       cancellation_note TEXT NULL,

                                       cancelled_at DATETIME NOT NULL,

                                       refund_status VARCHAR(30) NOT NULL DEFAULT 'NOT_APPLICABLE',

                                       refund_amount DECIMAL(12, 2) NULL,

                                       refund_currency VARCHAR(3) NULL,

                                       refund_reference VARCHAR(100) NULL,

                                       refund_processed_at DATETIME NULL,

                                       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                       PRIMARY KEY (cancellation_id),

                                       CONSTRAINT fk_booking_cancellations_booking
                                           FOREIGN KEY (booking_id)
                                               REFERENCES bookings (booking_id),

                                       CONSTRAINT fk_booking_cancellations_user
                                           FOREIGN KEY (cancelled_by_user_id)
                                               REFERENCES users (user_id),

                                       CONSTRAINT uq_booking_cancellations_booking
                                           UNIQUE (booking_id),

                                       INDEX idx_booking_cancellations_booking (
                                                                                booking_id
                                           ),

                                       INDEX idx_booking_cancellations_user (
                                                                             cancelled_by_user_id
                                           ),

                                       INDEX idx_booking_cancellations_refund_status (
                                                                                      refund_status
                                           ),

                                       INDEX idx_booking_cancellations_cancelled_at (
                                                                                     cancelled_at
                                           )
);