-- ============================================================
-- PARTNER INVENTORY OPERATIONS  (FR-21)
--
-- A partner needs to take rooms out of service: maintenance, a
-- private block, or a temporary closure. Today the only way to do
-- that is to shrink total_inventory, which is wrong because it
-- silently rewrites history and fights with the booking flow.
--
-- The fix is a THIRD quantity alongside total and reserved:
--
--     available + reserved + BLOCKED = total
--
-- Blocks are kept separate from reservations on purpose:
--   reserved = a guest has paid for it
--   blocked  = nobody may book it, but nobody has
--
-- Conflating them would make a maintenance closure look like an
-- occupancy figure and break commission reporting.
-- ============================================================

-- ------------------------------------------------------------
-- 1. Add the blocked quantity
-- ------------------------------------------------------------

ALTER TABLE room_inventory_daily
    ADD COLUMN blocked_inventory INT NOT NULL DEFAULT 0
        AFTER reserved_inventory;

-- Existing rows keep available + reserved = total, and blocked
-- starts at 0, so the new constraint below already holds.

-- ------------------------------------------------------------
-- 2. Widen the consistency constraint
-- ------------------------------------------------------------

ALTER TABLE room_inventory_daily
    DROP CHECK chk_inventory_consistency;

ALTER TABLE room_inventory_daily
    ADD CONSTRAINT chk_inventory_blocked
        CHECK (blocked_inventory >= 0);

ALTER TABLE room_inventory_daily
    ADD CONSTRAINT chk_inventory_consistency
        CHECK (
                available_inventory
                + reserved_inventory
                + blocked_inventory
                = total_inventory
            );

-- ------------------------------------------------------------
-- 3. The partner's own record of why rooms are out of service
--
-- room_inventory_daily holds the running totals; this table is the
-- auditable reason. A nightly number with no explanation is not
-- something a partner can act on six months later.
-- ------------------------------------------------------------

CREATE TABLE room_blocks (
                        block_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                        room_type_id BIGINT NOT NULL,

                        blocked_date DATE NOT NULL,

                        rooms_blocked INT NOT NULL DEFAULT 1,

                        reason ENUM(
                            'MAINTENANCE',
                            'PRIVATE_USE',
                            'TEMPORARY_CLOSURE',
                            'DEEP_CLEANING',
                            'OWNER_USE',
                            'OTHER'
                            ) NOT NULL DEFAULT 'MAINTENANCE',

                        notes VARCHAR(500) NULL,

                        created_by_user_id BIGINT NOT NULL,

                        released_at TIMESTAMP NULL,

                        released_by_user_id BIGINT NULL,

                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        CONSTRAINT chk_room_blocks_rooms
                            CHECK (rooms_blocked > 0),

                        -- NOTE: "rooms_blocked must not exceed the
                        -- nightly total" is deliberately NOT a CHECK
                        -- constraint. MySQL forbids subqueries inside
                        -- CHECK, so it cannot be expressed here.
                        --
                        -- It is instead enforced in two places that
                        -- actually work:
                        --   1. RoomInventoryDaily.blockRooms() refuses
                        --      to block more than the free inventory.
                        --   2. chk_inventory_available on
                        --      room_inventory_daily keeps available
                        --      non-negative, so an over-block can
                        --      never be written even if the service
                        --      were bypassed.

                        CONSTRAINT fk_room_blocks_room_type
                            FOREIGN KEY (room_type_id)
                                REFERENCES room_types(room_type_id)
                                ON DELETE CASCADE,

                        CONSTRAINT fk_room_blocks_created_by
                            FOREIGN KEY (created_by_user_id)
                                REFERENCES users(user_id)
                                ON DELETE RESTRICT,

                        CONSTRAINT fk_room_blocks_released_by
                            FOREIGN KEY (released_by_user_id)
                                REFERENCES users(user_id)
                                ON DELETE SET NULL,

                        -- One live block per room type per night.
                        -- Released blocks keep their history, so the
                        -- unique key includes the release state.
                        UNIQUE KEY uq_room_blocks_active (
                            room_type_id,
                            blocked_date,
                            released_at
                        ),

                        INDEX idx_room_blocks_date (blocked_date),
                        INDEX idx_room_blocks_room_date (
                            room_type_id, blocked_date
                        )
);

-- ------------------------------------------------------------
-- 4. The booking flow must be able to read blocks quickly
-- ------------------------------------------------------------

CREATE INDEX idx_inventory_availability
    ON room_inventory_daily (room_type_id, inventory_date, available_inventory);