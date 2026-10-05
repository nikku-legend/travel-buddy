-- ============================================================
-- TRIP SELECTION ROOM COUNT  (SRS 2.3 section 6.2)
--
-- "Change number of rooms where supported."
--
-- The quote previously always assumed one room, so a family of
-- four was priced for a single room and the bill came out at
-- roughly half what they would actually pay. The room count is
-- now stored on the selection rather than recomputed, so the
-- figure the traveller agreed to survives a later change to the
-- party size.
-- ============================================================

ALTER TABLE trip_selections
    ADD COLUMN rooms INT NULL AFTER guests;

-- A room count of zero or less is meaningless and would price a
-- stay at nothing.
ALTER TABLE trip_selections
    ADD CONSTRAINT chk_trip_selections_rooms
        CHECK (rooms IS NULL OR rooms > 0);

-- ----------------------------------------------------
-- Historic rows
--
-- Backfilled rather than left null so existing selections price
-- the same way the quote function does for a party that needs a
-- single room. Nullable stays allowed because an activity or
-- transport selection has no room count at all.
-- ----------------------------------------------------

UPDATE trip_selections
SET rooms = CASE
                WHEN guests IS NULL OR guests <= 2 THEN 1
                ELSE CEIL(guests / 2)
            END
WHERE rooms IS NULL
  AND selection_type = 'HOTEL';