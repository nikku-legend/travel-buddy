-- ============================================================
-- REVIEWS AND RATINGS  (FR-26)
--
-- The SRS is explicit about who may review:
--
--     Booking -> Completed -> Eligible for review -> Review
--             -> Moderation -> Published
--
-- So the review carries the booking that proves the reviewer
-- actually stayed, travelled or was driven. Without that link
-- anyone could review any hotel from the couch.
--
-- A review covers four kinds of subject:
--
--     HOTEL       -> properties.property_id
--     GUIDE       -> guides.guide_id
--     CAB         -> cabs.cab_id
--     DESTINATION -> tourist_places.place_id
--
-- target_id is deliberately polymorphic with no foreign key.
-- A single FK cannot point at four tables, and faking four
-- nullable columns is worse: it lets a review claim to be about
-- a property while referencing a guide. Integrity is enforced
-- in ReviewService, which resolves the target on every write.
-- ============================================================

CREATE TABLE reviews (
                        review_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                        user_id BIGINT NOT NULL,

                        target_type ENUM(
                            'HOTEL',
                            'GUIDE',
                            'CAB',
                            'DESTINATION'
                            ) NOT NULL,

                        target_id BIGINT NOT NULL,

                        -- The completed booking that proves the
                        -- reviewer is entitled to review. NULL only
                        -- for destination reviews, which need no stay.
                        booking_id BIGINT NULL,

                        -- INT, not TINYINT.
                        --
                        -- The column was originally declared TINYINT,
                        -- but the entity maps rating as a Java
                        -- Integer, and spring.jpa.hibernate.ddl-auto
                        -- is `validate`. Hibernate compares the JDBC
                        -- type, so TINYINT fails validation against
                        -- the expected `integer` and the application
                        -- refuses to start.
                        --
                        -- The unit tests did not catch this because
                        -- the test profile builds the schema FROM the
                        -- entities (create-drop), so they can never
                        -- disagree with them. Only running against
                        -- real MySQL exposes it.
                        --
                        -- The 1-5 range is enforced by chk_reviews_rating
                        -- below, which is the real guard.
                        rating INT NOT NULL,

                        title VARCHAR(150) NULL,

                        comment VARCHAR(2000) NULL,

                        status ENUM(
                            'PENDING',
                            'PUBLISHED',
                            'REJECTED',
                            'FLAGGED'
                            ) NOT NULL DEFAULT 'PENDING',

                        -- Moderation record (FR-29).
                        moderated_by_user_id BIGINT NULL,

                        moderated_at TIMESTAMP NULL,

                        moderation_reason VARCHAR(500) NULL,

                        -- How many users have flagged this review.
                        -- A rising count is a moderation signal.
                        flagged_count INT NOT NULL DEFAULT 0,

                        -- How many other travellers found it useful.
                        helpful_count INT NOT NULL DEFAULT 0,

                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                            ON UPDATE CURRENT_TIMESTAMP,

                        CONSTRAINT chk_reviews_rating
                            CHECK (rating >= 1 AND rating <= 5),

                        CONSTRAINT chk_reviews_flagged
                            CHECK (flagged_count >= 0),

                        CONSTRAINT chk_reviews_helpful
                            CHECK (helpful_count >= 0),

                        CONSTRAINT fk_reviews_user
                            FOREIGN KEY (user_id)
                                REFERENCES users(user_id)
                                ON DELETE CASCADE,

                        CONSTRAINT fk_reviews_booking
                            FOREIGN KEY (booking_id)
                                REFERENCES bookings(booking_id)
                                ON DELETE SET NULL,

                        CONSTRAINT fk_reviews_moderated_by
                            FOREIGN KEY (moderated_by_user_id)
                                REFERENCES users(user_id)
                                ON DELETE SET NULL,

                        -- One review per traveller per subject. Without
                        -- this a guest could review the same stay
                        -- fifty times to drag an average around.
                        UNIQUE KEY uq_reviews_user_target (
                            user_id, target_type, target_id
                        ),

                        -- Public listing reads the published reviews
                        -- for one subject, newest first.
                        INDEX idx_reviews_target (
                            target_type, target_id, status, created_at
                        ),

                        INDEX idx_reviews_user (user_id),
                        INDEX idx_reviews_status (status)
);

-- ============================================================
-- Rating aggregates
--
-- Averaging a review table on every read of a property, guide or
-- cab is a slow query that gets slower as the marketplace grows.
-- These rows are maintained whenever a review is published,
-- rejected, edited or deleted, so the public page is one lookup.
-- ============================================================

CREATE TABLE review_summaries (
                              target_type ENUM(
                                  'HOTEL',
                                  'GUIDE',
                                  'CAB',
                                  'DESTINATION'
                                  ) NOT NULL,

                              target_id BIGINT NOT NULL,

                              average_rating DECIMAL(3,2) NOT NULL DEFAULT 0.00,

                              review_count INT NOT NULL DEFAULT 0,

                              -- Star histogram, so the UI can show a
                              -- distribution instead of only an average.
                              five_star_count INT NOT NULL DEFAULT 0,
                              four_star_count INT NOT NULL DEFAULT 0,
                              three_star_count INT NOT NULL DEFAULT 0,
                              two_star_count INT NOT NULL DEFAULT 0,
                              one_star_count INT NOT NULL DEFAULT 0,

                              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                  ON UPDATE CURRENT_TIMESTAMP,

                              CONSTRAINT chk_review_summary_count
                                  CHECK (review_count >= 0),

                              PRIMARY KEY (target_type, target_id)
);