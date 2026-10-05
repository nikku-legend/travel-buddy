-- ============================================================
-- V53 -- REVIEW UNLOCKS (SRS 2.3 TP-12, section 8 and section 10)
--
-- Section 8 lists review_unlocks in the Trip Planner data model:
--
--     review_unlocks | Eligibility for post-trip reviews.
--
-- Section 11 gives the state machine:
--
--     LOCKED -> ELIGIBLE -> SUBMITTED -> PUBLISHED / FLAGGED
--
-- and section 10 describes what this makes possible: "the system
-- evaluates which bookings are eligible for review. Review Center
-- becomes available. Each eligible hotel/guide/transport service
-- receives a review card."
--
-- The gap this fills: ReviewService could already decide eligibility,
-- but only for ONE target the client already knew about
-- (GET /reviews/eligibility?targetType=&targetId=). Nothing could
-- answer "what can I review from this trip?", so the Review Center
-- had no data source and the REVIEW_OPEN trip state was unreachable
-- in practice.
--
-- One row per reviewable service per trip. The UNIQUE constraint is
-- what makes opening idempotent: the window is derived repeatedly
-- (every visit to the Review Center re-derives it) and re-running it
-- must not produce a second card for a stay the traveller has not
-- left yet.
-- ------------------------------------------------------------

CREATE TABLE review_unlocks (
                        unlock_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                        trip_id BIGINT NOT NULL,

                        -- Matches reviews.target_type. No foreign key:
                        -- one polymorphic column cannot reference four
                        -- tables, exactly as ReviewTargetType explains.
                        -- Integrity is enforced by TripReviewService,
                        -- which resolves the target on every write.
                        target_type ENUM(
                            'HOTEL',
                            'GUIDE',
                            'CAB',
                            'DESTINATION'
                            ) NOT NULL,

                        -- properties / guides / cabs / tourist_places
                        target_id BIGINT NOT NULL,

                        -- The completed booking proving entitlement.
                        -- NULL for cabs: a ride is not a booking, and
                        -- ReviewService proves cab entitlement from a
                        -- completed ride instead. Mirrors the rule in
                        -- findEntitlement.
                        booking_id BIGINT NULL,

                        status ENUM(
                            'LOCKED',
                            'ELIGIBLE',
                            'SUBMITTED',
                            'PUBLISHED',
                            'FLAGGED'
                            ) NOT NULL DEFAULT 'LOCKED',

                        -- Why this card exists, in words the traveller
                        -- can be shown. Provenance matters here: the
                        -- SRS is emphatic that eligibility is derived
                        -- from completed bookings, not granted freely.
                        unlock_reason VARCHAR(255) NULL,

                        -- Stay context, snapshotted rather than joined
                        -- for. The Review Center is a pure read over
                        -- one trip, and re-deriving the window from
                        -- hotel reservations, guide tours and cab
                        -- rides each time is three different queries
                        -- with three different date shapes. Frozen at
                        -- creation for the same reason TripSelection
                        -- freezes quoted_amount: the card must keep
                        -- describing the stay that happened.
                        check_in DATE NULL,

                        check_out DATE NULL,

                        city_name VARCHAR(120) NULL,

                        -- Set when the window opens, i.e. when the
                        -- underlying service reached COMPLETED.
                        eligible_at TIMESTAMP(6) NULL,

                        review_id BIGINT NULL,

                        created_at TIMESTAMP(6)
                            NOT NULL
                            DEFAULT CURRENT_TIMESTAMP(6),

                        updated_at TIMESTAMP(6)
                            NOT NULL
                            DEFAULT CURRENT_TIMESTAMP(6)
                            ON UPDATE CURRENT_TIMESTAMP(6),

                        CONSTRAINT fk_review_unlocks_trip
                            FOREIGN KEY (trip_id)
                                REFERENCES trips(trip_id)
                                ON DELETE CASCADE,

                        CONSTRAINT fk_review_unlocks_booking
                            FOREIGN KEY (booking_id)
                                REFERENCES bookings(booking_id)
                                ON DELETE SET NULL,

                        -- A withdrawn review must not delete the
                        -- entitlement that produced it.
                        CONSTRAINT fk_review_unlocks_review
                            FOREIGN KEY (review_id)
                                REFERENCES reviews(review_id)
                                ON DELETE SET NULL,

                        -- One card per service per trip. This is the
                        -- idempotency guarantee for re-derivation.
                        CONSTRAINT uq_review_unlocks_target
                            UNIQUE (trip_id, target_type, target_id),

                        -- NOTE: there is deliberately no "status = 'SUBMITTED' implies
                        -- review_id IS NOT NULL" check here. MySQL
                        -- rejects it: a column named in a CHECK
                        -- constraint cannot also carry a foreign key
                        -- with a referential action (error 3823), and
                        -- fk_review_unlocks_review needs ON DELETE
                        -- SET NULL so a withdrawn review does not take
                        -- the traveller's entitlement with it.
                        --
                        -- Keeping the CHECK would also be actively
                        -- wrong: it would make deleting a review fail
                        -- for any card still marked SUBMITTED, so a
                        -- withdrawn review could never be cleaned up.
                        -- The invariant is enforced in ReviewUnlock
                        -- instead, where markSubmitted always links
                        -- the review it names.

                        -- Eligibility is only meaningful once the
                        -- service completed, so the two go together.
                        -- Safe from the same restriction: neither
                        -- column appears in a foreign key.
                        CONSTRAINT chk_review_unlocks_eligible
                            CHECK (
                                status = 'LOCKED'
                                    OR eligible_at IS NOT NULL
                                ),

                        -- The Review Center reads one trip's cards in
                        -- journey order, filtered by status.
                        INDEX idx_review_unlocks_trip_status (
                            trip_id,
                            status
                            ),

                        -- Finds an existing review for this service,
                        -- so re-derivation links rather than
                        -- duplicating.
                        INDEX idx_review_unlocks_target (
                            target_type,
                            target_id
                            )
) ENGINE = InnoDB;