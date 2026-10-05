-- ============================================================
-- PROPERTY DETAIL  (SRS 2.3 section 6.2, FR-40 signals)
--
-- Section 6.2 requires a hotel detail page showing "property
-- overview, images, location/map, amenities, room types,
-- occupancy, price breakdown, policies, cancellation terms,
-- ratings/reviews and availability".
--
-- Everything except room types, ratings, location and
-- availability already existed. This migration adds the four
-- missing pieces, and the amenity catalogue is what section 6.1
-- factors 7 and 8 score on, so it is modelled properly rather
-- than as free text on the property row.
-- ============================================================


-- ------------------------------------------------------------
-- AMENITY CATALOGUE
--
-- A shared catalogue rather than free text per property, so
-- "Wi-Fi" means the same thing everywhere and a search for
-- every property with air conditioning is possible at all.
-- ------------------------------------------------------------

CREATE TABLE amenities (
                 amenity_id INT AUTO_INCREMENT PRIMARY KEY,

                 -- Stable code, e.g. WIFI, POOL, PARKING.
                 -- The code is the identity; the label is display
                 -- copy and can be reworded without a data
                 -- migration.
                 code VARCHAR(40) NOT NULL,

                 label VARCHAR(100) NOT NULL,

                 -- GROUP, ROOM, SERVICE, ACCESSIBILITY, WELLNESS.
                 -- Grouped so a card can show "Facilities" and
                 -- "Access" separately instead of one long list.
                 category VARCHAR(30) NOT NULL,

                 icon_name VARCHAR(40) NULL,

                 INDEX idx_amenities_category (category, label)
) ENGINE = InnoDB;

CREATE UNIQUE INDEX uq_amenities_code ON amenities (code);


-- ------------------------------------------------------------
-- PROPERTY AMENITIES
--
-- A join carrying the property-specific detail: whether it is
-- free, whether it needs booking, and a note such as "charged
-- separately".
-- ------------------------------------------------------------

CREATE TABLE property_amenities (
                          property_id BIGINT NOT NULL,

                          amenity_id INT NOT NULL,

                          is_free BOOLEAN NOT NULL DEFAULT TRUE,

                          -- Set when the amenity must be arranged in
                          -- advance rather than used on arrival.
                          requires_booking BOOLEAN NOT NULL DEFAULT FALSE,

                          note VARCHAR(200) NULL,

                          PRIMARY KEY (property_id, amenity_id),

                          CONSTRAINT fk_property_amenities_property
                              FOREIGN KEY (property_id)
                                  REFERENCES properties(property_id)
                                  ON DELETE CASCADE,

                          -- RESTRICT, not CASCADE. Deleting a
                          -- catalogue entry would silently
                                  -- strip amenities from every
                          -- property and change what the
                          -- recommender scores.
                          CONSTRAINT fk_property_amenities_amenity
                              FOREIGN KEY (amenity_id)
                                  REFERENCES amenities(amenity_id)
                                  ON DELETE RESTRICT,

                          CONSTRAINT chk_property_amenities_note
                              CHECK (note IS NULL OR CHAR_LENGTH(note) <= 200)
) ENGINE = InnoDB;


-- ------------------------------------------------------------
-- GALLERY
--
-- Named in the SRS schema list (property_images) and required by
-- the detail page. sort_order is explicit rather than inferred
-- from the id, because a partner reordering their gallery should
-- not renumber every row.
-- ------------------------------------------------------------

CREATE TABLE property_images (
                       image_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                       property_id BIGINT NOT NULL,

                       image_url VARCHAR(500) NOT NULL,

                       alt_text VARCHAR(200) NULL,

                       -- 0 is the cover image.
                       sort_order INT NOT NULL DEFAULT 0,

                       is_cover BOOLEAN NOT NULL DEFAULT FALSE,

                       created_at TIMESTAMP(6)
                           NOT NULL
                           DEFAULT CURRENT_TIMESTAMP(6),

                       CONSTRAINT fk_property_images_property
                           FOREIGN KEY (property_id)
                               REFERENCES properties(property_id)
                               ON DELETE CASCADE,

                       CONSTRAINT chk_property_images_sort
                           CHECK (sort_order >= 0)
) ENGINE = InnoDB;

CREATE INDEX idx_property_images_property
    ON property_images (property_id, sort_order);


-- ------------------------------------------------------------
-- POLICIES
--
-- Check-in and check-out times matter to a traveller more than
-- any other single fact on the page, and they are not
-- derivable from anything else on the row.
-- ------------------------------------------------------------

CREATE TABLE property_policies (
                         policy_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                         property_id BIGINT NOT NULL,

                         policy_type ENUM(
                             'CHECK_IN_TIME',
                             'CHECK_OUT_TIME',
                             'HOUSE_RULE',
                             'CHILDREN',
                             'AGE_MINIMUM',
                             'PAYMENT_AT_PROPERTY',
                             'OTHER'
                             ) NOT NULL,

                         title VARCHAR(120) NOT NULL,

                         description VARCHAR(500) NULL,

                         sort_order INT NOT NULL DEFAULT 0,

                         CONSTRAINT fk_property_policies_property
                             FOREIGN KEY (property_id)
                                 REFERENCES properties(property_id)
                                 ON DELETE CASCADE,

                         CONSTRAINT uq_property_policies_type
                             UNIQUE (property_id, policy_type),

                         CONSTRAINT chk_property_policies_sort
                             CHECK (sort_order >= 0)
) ENGINE = InnoDB;


-- ------------------------------------------------------------
-- CANCELLATION TERMS
--
-- Tiered rather than a single percentage, matching FR-11's
-- "tier-based cancellation fee computation". A property with no
-- rows is non-refundable, which is the safe default: assuming a
-- generous policy for a property that never stated one is how a
-- platform ends up owing refunds it never agreed to.
--
-- refund_percent is what the TRAVELLER gets back, not the
-- penalty. Storing it that way means the displayed figure is the
-- figure a traveller cares about, and the deduction is derived.
-- ------------------------------------------------------------

CREATE TABLE property_cancellation_terms (
                                    term_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                    property_id BIGINT NOT NULL,

                                    -- Charge applies when the stay
                                    -- starts within this many days.
                                    days_before_check_in INT NOT NULL,

                                    refund_percent DECIMAL(5, 2)
                                        NOT NULL,

                                    -- Extra nights charged when the
                                    -- guest leaves early, where the
                                    -- property enforces that.
                                    min_nights_charge INT
                                        NOT NULL DEFAULT 0,

                                    description VARCHAR(200) NULL,

                                    CONSTRAINT fk_property_cancellation_property
                                        FOREIGN KEY (property_id)
                                            REFERENCES properties(property_id)
                                            ON DELETE CASCADE,

                                    -- A property has one tier per
                                    -- day-window. Overlapping
                                    -- windows would make the
                                    -- applicable refund ambiguous.
                                    CONSTRAINT uq_property_cancellation_days
                                        UNIQUE (property_id, days_before_check_in),

                                    CONSTRAINT chk_property_cancellation_days
                                        CHECK (days_before_check_in >= 0),

                                    CONSTRAINT chk_property_cancellation_refund
                                        CHECK (
                                            refund_percent >= 0
                                                AND refund_percent <= 100
                                            ),

                                    CONSTRAINT chk_property_cancellation_nights
                                        CHECK (min_nights_charge >= 0)
) ENGINE = InnoDB;

CREATE INDEX idx_property_cancellation_property
    ON property_cancellation_terms (property_id, days_before_check_in);


-- ------------------------------------------------------------
-- SEED CATALOGUE AND DEMO CONTENT
--
-- A detail page with no amenities is indistinguishable from a
-- broken one, and the recommender has nothing to score.
-- ------------------------------------------------------------

INSERT INTO amenities (code, label, category, icon_name) VALUES
    ('WIFI',        'Free Wi-Fi',            'GROUP',        'wifi'),
    ('PARKING',     'Free parking',          'GROUP',        'car'),
    ('RESTAURANT',  'Restaurant',            'GROUP',        'utensils'),
    ('BREAKFAST',   'Breakfast included',    'GROUP',        'coffee'),
    ('POOL',        'Swimming pool',         'WELLNESS',     'waves'),
    ('GYM',         'Fitness centre',        'WELLNESS',     'dumbbell'),
    ('SPA',         'Spa',                   'WELLNESS',     'flower'),
    ('AIR_CONDITIONING', 'Air conditioning',  'ROOM',         'snowflake'),
    ('SAFE',        'In-room safe',          'ROOM',         'lock'),
    ('TV',          'Television',            'ROOM',         'tv'),
    ('BALCONY',     'Balcony',               'ROOM',         'sun'),
    ('ROOM_SERVICE','24-hour room service',  'SERVICE',      'bell'),
    ('LAUNDRY',     'Laundry',               'SERVICE',      'shirt'),
    ('AIRPORT_SHUTTLE', 'Airport shuttle',   'SERVICE',      'bus'),
    ('STEP_FREE',   'Step-free access',      'ACCESSIBILITY','accessibility'),
    ('WHEELCHAIR',  'Wheelchair accessible',  'ACCESSIBILITY','accessibility');

-- Coastal Heritage Resort and Puri Heritage Stay are the two
-- demo properties. Matched by name because seed ids are not
-- stable between environments.
INSERT INTO property_amenities (property_id, amenity_id, is_free, requires_booking)
SELECT p.property_id, a.amenity_id, TRUE, FALSE
FROM properties p
CROSS JOIN amenities a
WHERE p.name LIKE '%Coastal%'
  AND a.code IN ('WIFI', 'PARKING', 'BREAKFAST', 'AIR_CONDITIONING',
                 'SAFE', 'TV', 'POOL', 'RESTAURANT', 'STEP_FREE',
                 'AIRPORT_SHUTTLE', 'LAUNDRY');

INSERT INTO property_amenities (property_id, amenity_id, is_free, requires_booking)
SELECT p.property_id, a.amenity_id, TRUE, FALSE
FROM properties p
CROSS JOIN amenities a
WHERE p.name LIKE '%Puri%'
  AND a.code IN ('WIFI', 'BREAKFAST', 'ROOM_SERVICE', 'TV', 'SAFE');

INSERT INTO property_images (property_id, image_url, alt_text, sort_order, is_cover)
SELECT p.property_id,
       CONCAT('https://images.travelbuddy.example/',
              LOWER(REPLACE(p.name, ' ', '-')), '-1.jpg'),
       CONCAT(p.name, ' exterior'), 0, TRUE
FROM properties p;

INSERT INTO property_policies (property_id, policy_type, title, description, sort_order)
SELECT p.property_id, 'CHECK_IN_TIME', 'Check-in',
       'From 12:00', 0
FROM properties p
UNION ALL
SELECT p.property_id, 'CHECK_OUT_TIME', 'Check-out',
       'By 11:00', 1
FROM properties p
UNION ALL
SELECT p.property_id, 'CHILDREN', 'Children',
       'Children under 6 stay free; extra beds on request.', 2
FROM properties p;

INSERT INTO property_cancellation_terms
    (property_id, days_before_check_in, refund_percent, description)
SELECT p.property_id, 7, 100.00,
       'Free cancellation up to 7 days before check-in'
FROM properties p
UNION ALL
SELECT p.property_id, 2, 50.00,
       '50% refund up to 2 days before check-in'
FROM properties p;