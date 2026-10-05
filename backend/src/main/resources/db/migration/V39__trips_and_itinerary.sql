-- ============================================================
-- TRIP PLANNER: TRIPS, CITIES, PLACES  (TP-01 .. TP-04)
--
-- The organising idea, stated once because every later table
-- depends on it:
--
--     SELECTED IS NOT BOOKED.
--
-- Adding a hotel to a trip records intent. It reserves nothing.
-- Inventory is only touched at centralized checkout, in
-- trip_checkout, and until then a plan is freely editable and
-- costs the traveller nothing.
--
-- All money columns are DECIMAL and stored as a snapshot, never
-- recomputed from live prices on read. A bill that changed after
-- the traveller agreed to it would be worse than no bill.
-- ============================================================

CREATE TABLE trips (
                   trip_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                   user_id BIGINT NOT NULL,

                   title VARCHAR(150) NOT NULL,

                   start_date DATE NOT NULL,

                   end_date DATE NOT NULL,

                   -- How many city stops the traveller chose in
                   -- TP-02. Kept separately from the actual stop
                   -- count so "pick 3 cities" is recorded even
                   -- while only one has been chosen.
                   planned_city_count INT NOT NULL DEFAULT 1,

                   status ENUM(
                       'DRAFT',
                       'PLANNING',
                       'READY_FOR_CHECKOUT',
                       'CONFIRMED',
                       'IN_PROGRESS',
                       'COMPLETED',
                       'REVIEW_OPEN',
                       'CLOSED'
                       ) NOT NULL DEFAULT 'DRAFT',

                   -- Optional ceiling in the traveller's own
                   -- currency. Drives the budget warning in
                   -- section 4.5 and nothing else; exceeding it
                   -- warns, it never blocks.
                   budget_amount DECIMAL(12, 2) NULL,

                   budget_currency VARCHAR(3) NULL,

                   -- Recomputed whenever a selection changes.
                   -- Denormalised so the trip list can show a
                   -- total without aggregating the bill.
                   estimated_total DECIMAL(12, 2)
                       NOT NULL DEFAULT 0.00,

                   currency VARCHAR(3) NOT NULL DEFAULT 'INR',

                   confirmed_at TIMESTAMP(6) NULL,

                   completed_at TIMESTAMP(6) NULL,

                   created_at TIMESTAMP(6)
                       NOT NULL
                       DEFAULT CURRENT_TIMESTAMP(6),

                   updated_at TIMESTAMP(6)
                       NOT NULL
                       DEFAULT CURRENT_TIMESTAMP(6)
                       ON UPDATE CURRENT_TIMESTAMP(6),

                   CONSTRAINT fk_trips_user
                       FOREIGN KEY (user_id)
                           REFERENCES users(user_id)
                           ON DELETE CASCADE,

                   CONSTRAINT chk_trips_dates
                       CHECK (end_date >= start_date),

                   CONSTRAINT chk_trips_city_count
                       CHECK (planned_city_count > 0),

                   INDEX idx_trips_user_created (user_id, created_at),
                   INDEX idx_trips_user_status (user_id, status)
) ENGINE = InnoDB;


-- ------------------------------------------------------------
-- ORDERED CITY STOPS  (TP-02, TP-03)
--
-- sequence is what makes "city sequence intelligence" possible.
-- The recommendation engine proposes an order; the traveller may
-- reorder; reordering renumbers sequence and nothing else.
--
-- The UNIQUE (trip_id, sequence) constraint is the thing that
-- actually prevents two stops claiming position 2. Application
-- logic that renumbers first and inserts second can still race,
-- and the database is the only place that cannot.
-- ------------------------------------------------------------

CREATE TABLE trip_cities (
                     trip_city_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                     trip_id BIGINT NOT NULL,

                     city_id BIGINT NOT NULL,

                     -- 1-based position in the route.
                     sequence INT NOT NULL,

                     arrival_date DATE NULL,

                     departure_date DATE NULL,

                     -- Why the engine ordered it here, in plain
                     -- language ("closer to Puri than Cuttack").
                     -- Cleared the moment the traveller reorders,
                     -- because it would then be a false claim.
                     sequence_reason VARCHAR(500) NULL,

                     created_at TIMESTAMP(6)
                         NOT NULL
                         DEFAULT CURRENT_TIMESTAMP(6),

                     CONSTRAINT fk_trip_cities_trip
                         FOREIGN KEY (trip_id)
                             REFERENCES trips(trip_id)
                             ON DELETE CASCADE,

                     CONSTRAINT fk_trip_cities_city
                         FOREIGN KEY (city_id)
                             REFERENCES cities(city_id)
                             ON DELETE RESTRICT,

                     CONSTRAINT uq_trip_cities_sequence
                         UNIQUE (trip_id, sequence),

                     -- The same city cannot appear twice in one
                     -- route without a genuinely different stop,
                     -- which the planner does not support.
                     CONSTRAINT uq_trip_cities_city
                         UNIQUE (trip_id, city_id),

                     CONSTRAINT chk_trip_cities_sequence
                         CHECK (sequence > 0),

                     CONSTRAINT chk_trip_cities_dates
                         CHECK (
                             arrival_date IS NULL
                                 OR departure_date IS NULL
                                 OR departure_date >= arrival_date
                             ),

                     INDEX idx_trip_cities_trip (trip_id, sequence)
) ENGINE = InnoDB;


-- ------------------------------------------------------------
-- SELECTED PLACES  (TP-04)
--
-- Optional by design. A trip with no places is valid, and
-- section 4.1 falls back to city-centre distance rather than
-- refusing to recommend anything.
-- ------------------------------------------------------------

CREATE TABLE trip_places (
                     trip_place_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                     trip_id BIGINT NOT NULL,

                     trip_city_id BIGINT NOT NULL,

                     place_id BIGINT NOT NULL,

                     -- Traveller's working note. Not a review.
                     note VARCHAR(500) NULL,

                     created_at TIMESTAMP(6)
                         NOT NULL
                         DEFAULT CURRENT_TIMESTAMP(6),

                     CONSTRAINT fk_trip_places_trip
                         FOREIGN KEY (trip_id)
                             REFERENCES trips(trip_id)
                             ON DELETE CASCADE,

                     CONSTRAINT fk_trip_places_trip_city
                         FOREIGN KEY (trip_city_id)
                             REFERENCES trip_cities(trip_city_id)
                             ON DELETE CASCADE,

                     CONSTRAINT fk_trip_places_place
                         FOREIGN KEY (place_id)
                             REFERENCES tourist_places(place_id)
                             ON DELETE RESTRICT,

                     -- The same attraction twice in one trip adds
                     -- nothing and would double its entry fee.
                     CONSTRAINT uq_trip_places
                         UNIQUE (trip_id, place_id),

                     -- Index leads with trip_city_id: the UI loads
                     -- places per city, one query per stop.
                     INDEX idx_trip_places_trip_city (trip_city_id)
) ENGINE = InnoDB;