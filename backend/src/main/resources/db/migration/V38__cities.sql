-- ============================================================
-- CITIES  (SRS 2.2 foundation)
--
-- The SRS geography hierarchy is
--     Country -> Region -> State -> City -> Destination -> Attraction
--
-- Cities were missing. They are not a cosmetic addition: the
-- Trip Planner is city-centric. TP-02 is "user chooses the number
-- and identity of cities", TP-03 is city sequence intelligence,
-- and trip_cities is the table that holds an ordered stop. Without
-- this table the planner has nothing to order.
--
-- Cities sit under a state rather than replacing it. State already
-- carries the region_zone enum that every admin filter and the
-- existing property/place seeds depend on, and in India a city can
-- legitimately span states.
-- ============================================================

CREATE TABLE cities (
                      city_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                      state_id INT NOT NULL,

                      name VARCHAR(120) NOT NULL,

                      -- Stable slug for URLs and de-duplicating seed
                      -- data. A unique index on the name alone would
                      -- reject two genuinely different places with a
                      -- shared name.
                      slug VARCHAR(120) NOT NULL,

                      is_capital BOOLEAN NOT NULL DEFAULT FALSE,

                      description TEXT NULL,

                      latitude DECIMAL(10, 7) NULL,

                      longitude DECIMAL(10, 7) NULL,

                      created_at TIMESTAMP(6)
                          NOT NULL
                          DEFAULT CURRENT_TIMESTAMP(6),

                      CONSTRAINT fk_cities_state
                          FOREIGN KEY (state_id)
                              REFERENCES states(state_id)
                              ON DELETE CASCADE,

                      CONSTRAINT uq_cities_slug UNIQUE (slug),

                      CONSTRAINT uq_cities_state_name UNIQUE (state_id, name),

                      -- The planner's primary lookup is "cities in
                      -- this state", so state leads the index.
                      INDEX idx_cities_state (state_id, name)
) ENGINE = InnoDB;


-- ------------------------------------------------------------
-- ATTRACTIONS AND HOTELS BELONG TO A CITY
--
-- Both tables gain a nullable city_id.
--
-- Nullable on purpose. Existing rows predate this table and are
-- not backfilled blindly: a property or place with a NULL city is
-- still addressable through its state, so the planner treats
-- "city unknown" as "cannot be recommended" rather than
-- rejecting the row.
--
-- The indexes are composite and lead with city_id because the
-- recommendation queries are "everything in this city", not
-- "everything, filtered afterwards".
-- ------------------------------------------------------------

ALTER TABLE tourist_places
    ADD COLUMN city_id BIGINT NULL AFTER state_id,
    ADD CONSTRAINT fk_tourist_places_city
        FOREIGN KEY (city_id)
            REFERENCES cities(city_id)
            ON DELETE SET NULL,
    ADD INDEX idx_tourist_places_city (city_id);

ALTER TABLE properties
    ADD COLUMN city_id BIGINT NULL AFTER state_id,
    ADD CONSTRAINT fk_properties_city
        FOREIGN KEY (city_id)
            REFERENCES cities(city_id)
            ON DELETE SET NULL,
    ADD INDEX idx_properties_city (city_id);


-- ------------------------------------------------------------
-- SEED
--
-- The planner is unusable with no cities to select, and the
-- existing demo state and places are all in Odisha. Seeded
-- rather than left empty so the feature is demonstrable
-- immediately, and so a trip across Puri and Bhubaneswar can
-- actually be built.
-- ------------------------------------------------------------

INSERT INTO cities (state_id, name, slug, is_capital, description, latitude, longitude)
SELECT s.state_id, 'Bhubaneswar', 'bhubaneswar', TRUE,
       'The planned capital of Odisha and a hub for temples and culture.',
       20.2961000, 85.8245000
FROM states s
WHERE s.name = 'Odisha'
  AND NOT EXISTS (SELECT 1 FROM cities WHERE slug = 'bhubaneswar');

INSERT INTO cities (state_id, name, slug, is_capital, description, latitude, longitude)
SELECT s.state_id, 'Puri', 'puri', FALSE,
       'A coastal pilgrimage city famed for the Jagannath Temple and its beach.',
       19.8135000, 85.8312000
FROM states s
WHERE s.name = 'Odisha'
  AND NOT EXISTS (SELECT 1 FROM cities WHERE slug = 'puri');

INSERT INTO cities (state_id, name, slug, is_capital, description, latitude, longitude)
SELECT s.state_id, 'Cuttack', 'cuttack', FALSE,
       'A historic city on the Mahanadi delta, once the capital of Odisha.',
       20.4625000, 85.8828000
FROM states s
WHERE s.name = 'Odisha'
  AND NOT EXISTS (SELECT 1 FROM cities WHERE slug = 'cuttack');


/*
 * Attach the seeded attractions and demo properties to their city
 * so the recommendation engine has proximity to work with.
 * Matched on state plus a name list rather than a hard-coded id,
 * because seed ids are not guaranteed between environments.
 */
UPDATE tourist_places p
JOIN cities c ON c.slug = 'puri'
SET p.city_id = c.city_id
WHERE p.city_id IS NULL
  AND p.name IN ('Jagannath Temple', 'Konark Sun Temple', 'Puri Beach');

UPDATE properties pr
JOIN cities c ON c.slug = 'puri'
SET pr.city_id = c.city_id
WHERE pr.city_id IS NULL
  AND pr.name LIKE '%Puri%';