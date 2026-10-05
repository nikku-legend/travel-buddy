-- ============================================================
-- HOME DISCOVERY  (SRS 2.3 section 2)
--
-- Section 2.2 sets an Attraction Card Standard: every attraction
-- card must carry an image, name, city context, a category, a
-- short description, an estimated visit duration and a map
-- action. Two of those did not exist, so a card built to the
-- standard would have had to invent them.
-- ============================================================

-- ------------------------------------------------------------
-- ATTRACTIONS
-- ------------------------------------------------------------

ALTER TABLE tourist_places
    -- The attraction type shown on the card, e.g. TEMPLE,
    -- BEACH, MUSEUM. A short VARCHAR rather than an ENUM: the
    -- taxonomy is the platform's to curate and an ENUM would
    -- need a migration for every new kind of place. An unknown
    -- category is a display problem, not a data-integrity one.
    ADD COLUMN category VARCHAR(40) NULL AFTER name,

    -- "Estimated visit duration where available" per section 2.2.
    -- Nullable on purpose: a genuine estimate is better than a
    -- rounded-up guess presented as fact, and the card simply
    -- omits the line when it is null.
    ADD COLUMN estimated_visit_minutes INT NULL AFTER category,

    -- Curation flag for Featured sections. Popularity itself is
    -- derived from review volume rather than stored, because a
    -- counter nobody resets drifts badly. Editorial "featured"
    -- is a deliberate human choice and does belong in the data.
    ADD COLUMN is_featured BOOLEAN NOT NULL DEFAULT FALSE
        AFTER estimated_visit_minutes,

    ADD CONSTRAINT chk_tourist_places_visit_minutes
        CHECK (
            estimated_visit_minutes IS NULL
                OR estimated_visit_minutes > 0
            );

-- ------------------------------------------------------------
-- PROPERTIES
--
-- Same reasoning for Stay Discovery: featured is editorial,
-- quality is derived from the review summary.
-- ------------------------------------------------------------

ALTER TABLE properties
    ADD COLUMN is_featured BOOLEAN NOT NULL DEFAULT FALSE
        AFTER longitude;


-- ------------------------------------------------------------
-- SEED
--
-- Home is the first thing anyone sees, and an empty Home is a
-- failed product launch. The existing demo data is promoted to
-- featured so the discovery page has something real on it.
--
-- Matched by name rather than by id, because seed ids are not
-- stable between environments.
-- ------------------------------------------------------------

UPDATE tourist_places
SET is_featured = TRUE,
    category = CASE
                   WHEN name LIKE '%Temple%'
                       THEN 'TEMPLE'
                   WHEN name LIKE '%Beach%'
                       THEN 'BEACH'
                   WHEN name LIKE '%Sun Temple%'
                       THEN 'HERITAGE'
                   ELSE 'ATTRACTION'
                   END,
    estimated_visit_minutes = CASE
                                  WHEN name LIKE '%Temple%'
                                      THEN 120
                                  WHEN name LIKE '%Beach%'
                                      THEN 180
                                  ELSE 90
                                  END
WHERE is_featured = FALSE
  AND category IS NULL;

-- Column name matches the entity mapping (is_verified), not the
-- Java field name, so the seed and the application read the same
-- column.
UPDATE properties
SET is_featured = TRUE
WHERE is_featured = FALSE
  AND is_verified = TRUE;