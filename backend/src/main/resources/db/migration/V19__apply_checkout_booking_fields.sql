-- V18 was recorded in the existing database before these checkout fields
-- were introduced. MySQL 8 does not support ADD COLUMN IF NOT EXISTS here,
-- so each guarded statement keeps an existing database and a clean install
-- on the same schema.
SET @hold_expires_at_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bookings' AND column_name = 'hold_expires_at'),
    'SELECT 1',
    'ALTER TABLE bookings ADD COLUMN hold_expires_at TIMESTAMP NULL'
);
PREPARE hold_expires_at_statement FROM @hold_expires_at_sql;
EXECUTE hold_expires_at_statement;
DEALLOCATE PREPARE hold_expires_at_statement;

SET @guest_name_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bookings' AND column_name = 'guest_name'),
    'SELECT 1',
    'ALTER TABLE bookings ADD COLUMN guest_name VARCHAR(150) NULL'
);
PREPARE guest_name_statement FROM @guest_name_sql;
EXECUTE guest_name_statement;
DEALLOCATE PREPARE guest_name_statement;

SET @guest_email_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bookings' AND column_name = 'guest_email'),
    'SELECT 1',
    'ALTER TABLE bookings ADD COLUMN guest_email VARCHAR(150) NULL'
);
PREPARE guest_email_statement FROM @guest_email_sql;
EXECUTE guest_email_statement;
DEALLOCATE PREPARE guest_email_statement;

SET @guest_phone_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bookings' AND column_name = 'guest_phone'),
    'SELECT 1',
    'ALTER TABLE bookings ADD COLUMN guest_phone VARCHAR(50) NULL'
);
PREPARE guest_phone_statement FROM @guest_phone_sql;
EXECUTE guest_phone_statement;
DEALLOCATE PREPARE guest_phone_statement;

SET @special_requests_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bookings' AND column_name = 'special_requests'),
    'SELECT 1',
    'ALTER TABLE bookings ADD COLUMN special_requests TEXT NULL'
);
PREPARE special_requests_statement FROM @special_requests_sql;
EXECUTE special_requests_statement;
DEALLOCATE PREPARE special_requests_statement;
