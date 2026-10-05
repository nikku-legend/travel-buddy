-- ============================================================
-- SRS 2.3: CUSTOM TRIP PLANNER INPUTS
-- ============================================================

-- ------------------------------------------------------------
-- trips.version
--
-- The Trip entity carries @Version for optimistic locking. The
-- column was missing, so the application started against a
-- migrated database and failed schema validation. Two moderators
-- or two browser tabs working the same trip is routine, and
-- without this the second write silently overwrites the first.
-- ------------------------------------------------------------

ALTER TABLE trips
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;


-- ------------------------------------------------------------
-- THE 2.3 WIZARD INPUTS
--
-- SRS 2.3 restructures the planner. In 2.2 planning began with
-- dates; in 2.3 the traveller states who is going and roughly
-- where BEFORE choosing dates:
--
--     PERSONS -> ZONE -> REGION -> DATES -> BUDGET/PREMIUM
--
-- These columns are what make that flow possible, and each one
-- feeds a different recommendation decision:
--
--   traveler_count  room occupancy, cab capacity, guide suitability
--   zone/region     the first geographic constraint on what can
--                   be suggested at all
--   travel_style    the Budget/Premium ranking weight
--
-- All nullable with defaults, so a trip created before 2.3 still
-- works. The planner treats absent values as "not chosen yet"
-- rather than invalidating the trip.
-- ------------------------------------------------------------

ALTER TABLE trips
    ADD COLUMN traveler_count INT NULL AFTER planned_city_count,
    ADD COLUMN adult_count INT NULL AFTER traveler_count,
    ADD COLUMN child_count INT NULL AFTER adult_count,

    -- Zone is the existing RegionZone enum, but stored by name
    -- here rather than as a new ENUM column: the zone concept
    -- predates 2.3 and is already the currency of the admin
    -- filters, so duplicating it would create two ways to say
    -- the same thing.
    ADD COLUMN zone VARCHAR(30) NULL AFTER child_count,

    -- Region is free text at this level. The SRS hierarchy
    -- (country -> region -> state -> city) has no region table
    -- yet, and inventing one for a single planner input would be
    -- premature; the state that resolves from it is what
    -- actually narrows recommendations.
    ADD COLUMN region_name VARCHAR(120) NULL AFTER zone,

    ADD COLUMN travel_style VARCHAR(20) NULL AFTER region_name;

-- child_count >= 0 and the two parts never exceed the total.
-- A negative party size is meaningless and a total that does not
-- add up would silently corrupt occupancy maths.
ALTER TABLE trips
    ADD CONSTRAINT chk_trips_traveler_count
        CHECK (
            traveler_count IS NULL OR traveler_count > 0
            ),

    ADD CONSTRAINT chk_trips_party_split
        CHECK (
            adult_count IS NULL
                OR child_count IS NULL
                OR (adult_count >= 0
                    AND child_count >= 0
                    AND adult_count + child_count
                        = COALESCE(traveler_count,
                                   adult_count + child_count))
            ),

    ADD CONSTRAINT chk_trips_travel_style
        CHECK (
            travel_style IS NULL
                OR travel_style IN ('BUDGET', 'PREMIUM')
            );