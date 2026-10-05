-- =====================================================================
-- V48: which room a hotel recommendation actually refers to
-- =====================================================================
--
-- The recommender already chooses a specific room type: it walks
-- the property's active rooms, keeps the cheapest one that is
-- available for every night, and both scores and prices the stay
-- against that room. Its explanation even names it ("Standard Room
-- fits 2").
--
-- But nothing recorded WHICH room. A client could only add the
-- property to the cart, never the room, and TripCartService prices a
-- hotel from the attached room type -- so a hotel added without one
-- quoted 0.00, and checkout then refused the whole trip with
-- "Your trip cart is empty". The recommender and the cart could not
-- be connected, even though every unit test passed: they were
-- tested in isolation, each seeding a room type by hand.
--
-- Persisting the id is what makes the recommendation actionable and
-- survives a page refresh, which a recomputed value would not.
--
-- Nullable: a rejected candidate names no room (it was rejected
-- precisely because no room was available), and GUIDE and CAB
-- recommendations have no room at all.
-- =====================================================================

ALTER TABLE trip_recommendations
    ADD COLUMN room_type_id BIGINT NULL
        -- No COMMENT: a semicolon inside a quoted COMMENT breaks
        -- Flyway's statement splitter, which is exactly the bug this
        -- migration had on its first run.
        AFTER target_id;

-- Restrict rather than cascade. A room type deleted while a
-- recommendation still points at it would leave an offer that cannot
-- be added to a cart and cannot be explained.
ALTER TABLE trip_recommendations
    ADD CONSTRAINT fk_trip_rec_room_type
        FOREIGN KEY (room_type_id) REFERENCES room_types (room_type_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT;

-- The recommender ranks and prices per city, so a lookup is always
-- scoped by the city a stop sits in.
CREATE INDEX idx_trip_rec_room_type
    ON trip_recommendations (room_type_id);