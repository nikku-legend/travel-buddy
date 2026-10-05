-- ============================================================
-- PHYSICAL ROOM ASSIGNMENT AND CHECK-IN/OUT  (FR-22, FR-23)
--
-- Booking a "Deluxe Room" is a promise of a CATEGORY, not a
-- specific room. The front desk still has to decide Room 101 vs
-- 102, and the guest still has to be checked in and out.
--
-- Two new tables:
--
--   physical_rooms - the real, numbered rooms of a property
--   room_stays     - one physical room occupied by one booking
--                    across a date range
--
-- hotel_reservations.assigned_room_numbers already existed but was
-- free text, so it could neither prevent two guests being put in
-- the same room nor record who checked in when.
-- ============================================================

-- ------------------------------------------------------------
-- 1. Bookings gain in-house states
-- ------------------------------------------------------------

ALTER TABLE bookings
    MODIFY COLUMN booking_status ENUM(
        'PENDING',
        'CONFIRMED',
        'CHECKED_IN',
        'CANCELLED',
        'COMPLETED',
        'NO_SHOW'
        ) NOT NULL DEFAULT 'PENDING';

-- ------------------------------------------------------------
-- 2. The real rooms
-- ------------------------------------------------------------

CREATE TABLE physical_rooms (
                        physical_room_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                        property_id BIGINT NOT NULL,

                        room_type_id BIGINT NOT NULL,

                        -- "101", "A-12", "Villa 3". Free form on
                        -- purpose: hotels number rooms however they
                        -- like and we must not fight that.
                        room_number VARCHAR(30) NOT NULL,

                        floor_label VARCHAR(30) NULL,

                        status ENUM(
                            'AVAILABLE',
                            'OUT_OF_SERVICE'
                            ) NOT NULL DEFAULT 'AVAILABLE',

                        notes VARCHAR(500) NULL,

                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                            ON UPDATE CURRENT_TIMESTAMP,

                        CONSTRAINT fk_physical_rooms_property
                            FOREIGN KEY (property_id)
                                REFERENCES properties(property_id)
                                ON DELETE CASCADE,

                        CONSTRAINT fk_physical_rooms_room_type
                            FOREIGN KEY (room_type_id)
                                REFERENCES room_types(room_type_id)
                                ON DELETE CASCADE,

                        -- A room number must be unique per property.
                        -- Room 101 on one hotel says nothing about
                        -- room 101 on another.
                        UNIQUE KEY uq_physical_rooms_number (
                            property_id, room_number
                        ),

                        -- The room type must actually belong to the
                        -- same property, so a stay can never link a
                        -- room to a category from another hotel.
                        INDEX idx_physical_rooms_property_type (
                            property_id, room_type_id
                        ),

                        INDEX idx_physical_rooms_status (status)
);

-- ------------------------------------------------------------
-- 3. Stays: one physical room, one booking, one date range
-- ------------------------------------------------------------

CREATE TABLE room_stays (
                    stay_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                    booking_id BIGINT NOT NULL,

                    physical_room_id BIGINT NOT NULL,

                    -- Copied from the reservation rather than joined,
                    -- so an overlap check does not need to traverse
                    -- bookings and hotel_reservations on every insert.
                    check_in_date DATE NOT NULL,

                    check_out_date DATE NOT NULL,

                    status ENUM(
                        'ASSIGNED',
                        'CHECKED_IN',
                        'CHECKED_OUT',
                        'CANCELLED',
                        'NO_SHOW'
                        ) NOT NULL DEFAULT 'ASSIGNED',

                    guest_name VARCHAR(150) NULL,

                    checked_in_at TIMESTAMP NULL,

                    checked_out_at TIMESTAMP NULL,

                    assigned_by_user_id BIGINT NULL,

                    notes VARCHAR(500) NULL,

                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP,

                    CONSTRAINT chk_room_stays_dates
                        CHECK (check_out_date > check_in_date),

                    CONSTRAINT fk_room_stays_booking
                        FOREIGN KEY (booking_id)
                            REFERENCES bookings(booking_id)
                            ON DELETE CASCADE,

                    CONSTRAINT fk_room_stays_physical_room
                        FOREIGN KEY (physical_room_id)
                            REFERENCES physical_rooms(physical_room_id)
                            ON DELETE CASCADE,

                    CONSTRAINT fk_room_stays_assigned_by
                        FOREIGN KEY (assigned_by_user_id)
                            REFERENCES users(user_id)
                            ON DELETE SET NULL,

                    -- One booking can never hold the same physical
                    -- room twice.
                    UNIQUE KEY uq_room_stays_booking_room (
                        booking_id, physical_room_id
                    ),

                    INDEX idx_room_stays_room_dates (
                        physical_room_id, check_in_date, check_out_date
                    ),

                    INDEX idx_room_stays_booking (booking_id),
                    INDEX idx_room_stays_status (status)
);

-- ------------------------------------------------------------
-- 4. Overlap lookup
--
-- MySQL has no exclusion constraint, so "this room is free for
-- these dates" cannot be expressed declaratively. It is enforced
-- in RoomStayService under a PESSIMISTIC_WRITE lock on the
-- physical_rooms row, which serialises competing assignments.
-- This index exists purely to make that check cheap.
-- ------------------------------------------------------------

CREATE INDEX idx_room_stays_active_room
    ON room_stays (physical_room_id, status, check_in_date, check_out_date);

-- ------------------------------------------------------------
-- 5. Backfill physical rooms
--
-- Existing room_types declare a total_inventory but no real rooms
-- existed, so front-desk assignment had nothing to assign. One
-- physical room is generated per declared unit, numbered
-- "<Category>-<n>" so the numbering is obviously generated and
-- can be renamed by the partner.
-- ------------------------------------------------------------

INSERT INTO physical_rooms (
    property_id,
    room_type_id,
    room_number,
    floor_label,
    status
)
WITH RECURSIVE numbers(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1
    FROM numbers
    WHERE n < 500
)
SELECT rt.property_id,
       rt.room_type_id,
       CONCAT(rt.category_name, '-', numbers.n),
       CONCAT('Floor ', FLOOR((numbers.n - 1) / 10) + 1),
       'AVAILABLE'
FROM room_types rt
         JOIN numbers
              ON numbers.n <= rt.total_inventory;