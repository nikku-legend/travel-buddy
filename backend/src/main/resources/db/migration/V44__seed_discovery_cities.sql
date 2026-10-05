-- ============================================================
-- DISCOVERY CITY SEED
--
-- SRS 2.3 section 2.1 asks Home to offer "Explore by Zone" and
-- "Explore by Region" so a visitor can understand the wider area
-- before choosing. The V38 seed created three cities, all in
-- Odisha, which left the discovery page showing a single zone and
-- three of six seeded attractions with no city context at all.
--
-- This adds the cities the existing attractions actually sit in,
-- plus enough breadth that the zone and region sections have
-- something real to show.
-- ============================================================

-- Odisha: the Udayagiri and Khandagiri caves sit in the Khordha
-- district immediately north of the capital.
INSERT INTO cities (state_id, name, slug, is_capital, description, latitude, longitude)
SELECT s.state_id, 'Khordha', 'khordha', FALSE,
       'District containing Bhubaneswar and the Udayagiri and Khandagiri caves.',
       20.3644000, 85.8141000
FROM states s
WHERE s.name = 'Odisha'
  AND NOT EXISTS (SELECT 1 FROM cities WHERE slug = 'khordha');

-- West Bengal: two of the seeded attractions.
INSERT INTO cities (state_id, name, slug, is_capital, description, latitude, longitude)
SELECT s.state_id, 'Kolkata', 'kolkata', FALSE,
       'The cultural capital of West Bengal, known for its colonial architecture and festivals.',
       22.5726000, 88.3639000
FROM states s
WHERE s.name = 'West Bengal'
  AND NOT EXISTS (SELECT 1 FROM cities WHERE slug = 'kolkata');

INSERT INTO cities (state_id, name, slug, is_capital, description, latitude, longitude)
SELECT s.state_id, 'Darjeeling', 'darjeeling', FALSE,
       'A hill station in the Himalayas known for its tea gardens and mountain railways.',
       27.0410000, 88.2663000
FROM states s
WHERE s.name = 'West Bengal'
  AND NOT EXISTS (SELECT 1 FROM cities WHERE slug = 'darjeeling');

-- One city per remaining zone, so "Explore by Zone" and
-- "Explore by Region" are navigable rather than decorative.
INSERT INTO cities (state_id, name, slug, is_capital, description, latitude, longitude)
SELECT s.state_id, 'Jaipur', 'jaipur', TRUE,
       'The Pink City and capital of Rajasthan.',
       26.9124000, 75.7873000
FROM states s
WHERE s.name = 'Rajasthan'
  AND NOT EXISTS (SELECT 1 FROM cities WHERE slug = 'jaipur');

INSERT INTO cities (state_id, name, slug, is_capital, description, latitude, longitude)
SELECT s.state_id, 'Panaji', 'panaji', TRUE,
       'The capital of Goa, on the Mandovi river.',
       15.4909000, 73.8278000
FROM states s
WHERE s.name = 'Goa'
  AND NOT EXISTS (SELECT 1 FROM cities WHERE slug = 'panaji');

INSERT INTO cities (state_id, name, slug, is_capital, description, latitude, longitude)
SELECT s.state_id, 'Kochi', 'kochi', FALSE,
       'A port city in Kerala known for its backwaters and spice trade.',
       9.9312000, 76.2673000
FROM states s
WHERE s.name = 'Kerala'
  AND NOT EXISTS (SELECT 1 FROM cities WHERE slug = 'kochi');

INSERT INTO cities (state_id, name, slug, is_capital, description, latitude, longitude)
SELECT s.state_id, 'Varanasi', 'varanasi', FALSE,
       'One of the oldest continuously inhabited cities, on the Ganges.',
       25.3176000, 82.9739000
FROM states s
WHERE s.name = 'Uttar Pradesh'
  AND NOT EXISTS (SELECT 1 FROM cities WHERE slug = 'varanasi');


-- ------------------------------------------------------------
-- BACKFILL EXISTING CONTENT
--
-- Matched by attraction name rather than state, because several
-- states now contain more than one city and a state-level match
-- would pick the wrong one.
--
-- COALESCE keeps this idempotent: a place already attached to a
-- city is left alone.
-- ------------------------------------------------------------

UPDATE tourist_places p
JOIN cities c ON c.slug = 'khordha'
SET p.city_id = c.city_id
WHERE p.city_id IS NULL
  AND p.name LIKE 'Udayagiri%';

UPDATE tourist_places p
JOIN cities c ON c.slug = 'kolkata'
SET p.city_id = c.city_id
WHERE p.city_id IS NULL
  AND p.name LIKE 'Victoria Memorial%';

UPDATE tourist_places p
JOIN cities c ON c.slug = 'darjeeling'
SET p.city_id = c.city_id
WHERE p.city_id IS NULL
  AND p.name = 'Darjeeling';


-- ------------------------------------------------------------
-- HOMEPAGE COPY
--
-- The one existing featured property is in Puri, so it already
-- inherits city context. Named explicitly so a property that
-- predates the cities table is not left without one.
-- ------------------------------------------------------------

UPDATE properties pr
JOIN cities c ON c.slug = 'puri'
SET pr.city_id = c.city_id
WHERE pr.city_id IS NULL
  AND pr.name LIKE '%Puri%';

UPDATE properties pr
JOIN cities c ON c.slug = 'puri'
SET pr.city_id = c.city_id
WHERE pr.city_id IS NULL
  AND pr.name LIKE 'Coastal%';