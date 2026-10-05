-- Tracks the Razorpay order/payment identifiers used to verify a
-- checkout attempt server-side. Guarded with information_schema checks
-- so this migration is safe to run against a fresh database or one
-- that already has these columns from manual testing.
SET @razorpay_order_id_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bookings' AND column_name = 'razorpay_order_id'),
    'SELECT 1',
    'ALTER TABLE bookings ADD COLUMN razorpay_order_id VARCHAR(100) NULL'
);
PREPARE razorpay_order_id_statement FROM @razorpay_order_id_sql;
EXECUTE razorpay_order_id_statement;
DEALLOCATE PREPARE razorpay_order_id_statement;

SET @razorpay_payment_id_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bookings' AND column_name = 'razorpay_payment_id'),
    'SELECT 1',
    'ALTER TABLE bookings ADD COLUMN razorpay_payment_id VARCHAR(100) NULL'
);
PREPARE razorpay_payment_id_statement FROM @razorpay_payment_id_sql;
EXECUTE razorpay_payment_id_statement;
DEALLOCATE PREPARE razorpay_payment_id_statement;

SET @idx_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'bookings' AND index_name = 'idx_bookings_razorpay_order_id'),
    'SELECT 1',
    'CREATE INDEX idx_bookings_razorpay_order_id ON bookings (razorpay_order_id)'
);
PREPARE idx_statement FROM @idx_sql;
EXECUTE idx_statement;
DEALLOCATE PREPARE idx_statement;
