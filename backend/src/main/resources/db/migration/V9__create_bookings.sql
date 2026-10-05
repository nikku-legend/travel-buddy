CREATE TABLE bookings (
                          booking_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                          user_id BIGINT NOT NULL,

                          booking_reference VARCHAR(30) NOT NULL UNIQUE,

                          total_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,

                          currency_code CHAR(3) NOT NULL DEFAULT 'INR',

                          booking_status ENUM(
                              'PENDING',
                              'CONFIRMED',
                              'CANCELLED',
                              'COMPLETED'
                              ) NOT NULL DEFAULT 'PENDING',

                          payment_status ENUM(
                              'UNPAID',
                              'PAID',
                              'REFUNDED'
                              ) NOT NULL DEFAULT 'UNPAID',

                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP,

                          CONSTRAINT chk_booking_total
                              CHECK (total_amount >= 0),

                          CONSTRAINT fk_bookings_user
                              FOREIGN KEY (user_id)
                                  REFERENCES users(user_id)
                                  ON DELETE RESTRICT,

                          INDEX idx_bookings_user (user_id),
                          INDEX idx_bookings_status (booking_status),
                          INDEX idx_bookings_payment_status (payment_status),
                          INDEX idx_bookings_created_at (created_at)
);