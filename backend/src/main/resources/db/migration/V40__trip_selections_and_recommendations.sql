-- ============================================================
-- TRIP PLANNER: SELECTIONS AND RECOMMENDATIONS
--                                    (TP-05 .. TP-08, FR-40)
--
-- This is where SELECTED != BOOKED is made concrete.
--
-- A trip_selection is a pointer to something the traveller
-- intends to buy, plus the price captured at the moment they
-- chose it. It holds no inventory. Nothing here can make a room
-- unavailable to anybody else, because nothing here is a
-- reservation.
--
-- trip_recommendations is a separate table from trip_selections
-- on purpose. Showing a recommendation and accepting it are
-- different events, and the difference is measurable: it is the
-- only signal available for whether the engine's reasoning was
-- any use.
-- ============================================================

CREATE TABLE trip_selections (
                        selection_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                        trip_id BIGINT NOT NULL,

                        -- Which leg of the route this belongs to.
                        -- Nullable because a traveller can attach a
                        -- guide to a whole trip rather than one stop.
                        trip_city_id BIGINT NULL,

                        selection_type ENUM(
                            'HOTEL',
                            'GUIDE',
                            'CAB',
                            'ACTIVITY'
                            ) NOT NULL,

                        status ENUM(
                            'SELECTED',
                            'REVALIDATING',
                            'READY_FOR_CHECKOUT',
                            'BOOKED',
                            'UNAVAILABLE',
                            'REMOVED'
                            ) NOT NULL DEFAULT 'SELECTED',

                        -- One of property_id / room_type_id /
                        -- guide_id / cab_id / place_id depending on
                        -- selection_type. Deliberately not a
                        -- foreign key: the planner must be able to
                        -- hold a recommendation for a record that
                        -- is later deactivated, and show the
                        -- traveller why, rather than losing the row.
                        target_id BIGINT NOT NULL,

                        room_type_id BIGINT NULL,

                        place_id BIGINT NULL,

                        -- Specific physical room, once a partner
                        -- assigns one. Null until the stay is
                        -- assigned, which is normal.
                        assigned_room_number VARCHAR(20) NULL,

                        check_in DATE NULL,

                        check_out DATE NULL,

                        guests INT NULL,

                        -- Price captured when the traveller chose
                        -- this. Never recomputed on read: the bill
                        -- must show what was agreed, and a recheck
                        -- writes a NEW figure through
                        -- trip_bill_items instead.
                        quoted_amount DECIMAL(12, 2)
                            NOT NULL DEFAULT 0.00,

                        currency VARCHAR(3) NOT NULL DEFAULT 'INR',

                        -- Set when status becomes BOOKED.
                        booking_id BIGINT NULL,

                        -- Why checkout refused or changed this,
                        -- e.g. "no rooms left for 12-14 Mar".
                        unavailability_reason VARCHAR(500) NULL,

                        created_at TIMESTAMP(6)
                            NOT NULL
                            DEFAULT CURRENT_TIMESTAMP(6),

                        updated_at TIMESTAMP(6)
                            NOT NULL
                            DEFAULT CURRENT_TIMESTAMP(6)
                            ON UPDATE CURRENT_TIMESTAMP(6),

                        CONSTRAINT fk_trip_selections_trip
                            FOREIGN KEY (trip_id)
                                REFERENCES trips(trip_id)
                                ON DELETE CASCADE,

                        CONSTRAINT fk_trip_selections_trip_city
                            FOREIGN KEY (trip_city_id)
                                REFERENCES trip_cities(trip_city_id)
                                ON DELETE SET NULL,

                        CONSTRAINT fk_trip_selections_room_type
                            FOREIGN KEY (room_type_id)
                                REFERENCES room_types(room_type_id)
                                ON DELETE SET NULL,

                        CONSTRAINT fk_trip_selections_place
                            FOREIGN KEY (place_id)
                                REFERENCES tourist_places(place_id)
                                ON DELETE SET NULL,

                        CONSTRAINT fk_trip_selections_booking
                            FOREIGN KEY (booking_id)
                                REFERENCES bookings(booking_id)
                                ON DELETE SET NULL,

                        CONSTRAINT chk_trip_selections_amount
                            CHECK (quoted_amount >= 0),

                        CONSTRAINT chk_trip_selections_dates
                            CHECK (
                                check_in IS NULL
                                    OR check_out IS NULL
                                    OR check_out >= check_in
                                ),

                        CONSTRAINT chk_trip_selections_guests
                            CHECK (guests IS NULL OR guests > 0),

                        -- One hotel per property per trip. Without
                        -- this the same room could be selected
                        -- twice, creating a self-conflicting
                        -- itinerary that checkout would then have
                        -- to detect and fail.
                        CONSTRAINT uq_trip_selections_hotel
                            UNIQUE (trip_id, selection_type, target_id),

                        INDEX idx_trip_selections_trip (trip_id, status),
                        INDEX idx_trip_selections_type (
                            selection_type,
                            status
                            )
) ENGINE = InnoDB;


-- ------------------------------------------------------------
-- RECOMMENDATIONS WITH THEIR REASONING
--
-- Section 4 requires every recommendation to be explainable:
-- "2.1 km from your selected place". That string is generated
-- once, stored here, and shown to the traveller verbatim. It is
-- not recomputed on read, because a reason that changes under
-- the traveller is a lie.
--
-- rank is 1-based. A row whose score was NULL cannot be ranked
-- and must not be persisted.
-- ------------------------------------------------------------

CREATE TABLE trip_recommendations (
                                recommendation_id BIGINT
                                    AUTO_INCREMENT PRIMARY KEY,

                                trip_id BIGINT NOT NULL,

                                trip_city_id BIGINT NULL,

                                recommendation_type ENUM(
                                    'HOTEL',
                                    'GUIDE',
                                    'CAB'
                                    ) NOT NULL,

                                target_id BIGINT NOT NULL,

                                -- Denormalised title and price so an
                                -- abandoned recommendation can still
                                -- be explained after the underlying
                                -- listing is renamed or withdrawn.
                                title VARCHAR(200) NULL,

                                quoted_amount DECIMAL(12, 2)
                                    NOT NULL DEFAULT 0.00,

                                currency VARCHAR(3) NOT NULL DEFAULT 'INR',

                                -- 1-based. NULL only for an offer the
                                -- engine could not score, in which
                                -- case the row is not written.
                                rank_position INT NULL,

                                -- Distance in km from the trip's
                                -- selected places, the input
                                -- section 4.1 is built on. NULL when
                                -- the trip has no places and the
                                -- figure was computed from the city.
                                distance_km DECIMAL(8, 2) NULL,

                                -- The human-readable justification
                                -- shown in the UI.
                                reason VARCHAR(500) NULL,

                                -- Why this was rejected, for offers
                                -- the engine considered and dropped.
                                -- Storing the rejection is what stops
                                -- the engine being a black box.
                                rejection_reason VARCHAR(500) NULL,

                                was_shown BOOLEAN NOT NULL DEFAULT TRUE,

                                -- Set when the traveller accepted it,
                                -- which is the acceptance signal.
                                was_accepted BOOLEAN NULL,

                                created_at TIMESTAMP(6)
                                    NOT NULL
                                    DEFAULT CURRENT_TIMESTAMP(6),

                                CONSTRAINT fk_trip_recommendations_trip
                                    FOREIGN KEY (trip_id)
                                        REFERENCES trips(trip_id)
                                        ON DELETE CASCADE,

                                CONSTRAINT fk_trip_recommendations_trip_city
                                    FOREIGN KEY (trip_city_id)
                                        REFERENCES trip_cities(trip_city_id)
                                        ON DELETE SET NULL,

                                CONSTRAINT chk_trip_recommendations_rank
                                    CHECK (
                                        rank_position IS NULL
                                            OR rank_position > 0
                                        ),

                                CONSTRAINT chk_trip_recommendations_amount
                                    CHECK (quoted_amount >= 0),

                                -- One recommendation per target per
                                -- refresh, so a re-run replaces the
                                -- list instead of accumulating it.
                                CONSTRAINT uq_trip_recommendations
                                    UNIQUE (
                                        trip_id,
                                        recommendation_type,
                                        target_id
                                        ),

                                INDEX idx_trip_recommendations_trip (
                                    trip_id,
                                    recommendation_type,
                                    rank_position
                                    )
) ENGINE = InnoDB;