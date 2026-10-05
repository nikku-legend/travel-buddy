-- ============================================================
-- TRIP PLANNER: BILL AND CENTRALIZED CHECKOUT
--                                    (TP-09, TP-10, section 6)
--
-- Section 6 is the most safety-critical part of SRS 2.2:
-- "If one service becomes unavailable before payment, do not
-- silently substitute it or charge for a different trip."
--
-- Three tables carry that weight:
--
--   trip_checkout    the one central payment lifecycle
--   trip_bill_items  an immutable snapshot of what was agreed
--   payment_events   already exists; checkout reconciles from it
--
-- The snapshot is immutable on purpose. If the bill were a view
-- over current prices, a price change after the traveller
-- confirmed would silently alter a receipt.
-- ============================================================

CREATE TABLE trip_checkout (
                       checkout_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                       -- A trip may be checked out more than once:
                       -- a payment failure must be retryable, and a
                       -- recovery must be reconcilable. One active
                       -- row per trip is enforced below.
                       trip_id BIGINT NOT NULL,

                       -- Shown to the traveller alongside the
                       -- individual booking references.
                       checkout_reference VARCHAR(40) NOT NULL,

                       status ENUM(
                           'CREATED',
                           'REVALIDATING',
                           'PAYMENT_PENDING',
                           'PAID',
                           'CONFIRMED',
                           'RECOVERY_REQUIRED',
                           'FAILED'
                           ) NOT NULL DEFAULT 'CREATED',

                       -- The bill as revalidated at checkout, which
                       -- is the figure the payment is for.
                       total_amount DECIMAL(12, 2)
                           NOT NULL DEFAULT 0.00,

                       currency VARCHAR(3) NOT NULL DEFAULT 'INR',

                       tax_amount DECIMAL(12, 2)
                           NOT NULL DEFAULT 0.00,

                       fee_amount DECIMAL(12, 2)
                           NOT NULL DEFAULT 0.00,

                       discount_amount DECIMAL(12, 2)
                           NOT NULL DEFAULT 0.00,

                       -- What the traveller was quoted in the cart,
                       -- before revalidation. A difference between
                       -- this and total_amount is exactly the "if
                       -- something changed, show the affected line
                       -- item" case from section 6.
                       quoted_total DECIMAL(12, 2)
                           NOT NULL DEFAULT 0.00,

                       razorpay_order_id VARCHAR(100) NULL,

                       razorpay_payment_id VARCHAR(100) NULL,

                       -- Set when payment succeeded but confirming
                       -- the bookings did not. section 6 requires
                       -- recovery from persisted payment events
                       -- rather than charging again.
                       recovery_reason VARCHAR(500) NULL,

                       failure_reason VARCHAR(500) NULL,

                       created_at TIMESTAMP(6)
                           NOT NULL
                           DEFAULT CURRENT_TIMESTAMP(6),

                       updated_at TIMESTAMP(6)
                           NOT NULL
                           DEFAULT CURRENT_TIMESTAMP(6)
                           ON UPDATE CURRENT_TIMESTAMP(6),

                       confirmed_at TIMESTAMP(6) NULL,

                       CONSTRAINT fk_trip_checkout_trip
                           FOREIGN KEY (trip_id)
                               REFERENCES trips(trip_id)
                               ON DELETE CASCADE,

                       CONSTRAINT uq_trip_checkout_reference
                           UNIQUE (checkout_reference),

                       -- Razorpay callbacks can arrive more than
                       -- once. A second callback for the same
                       -- payment must not create a second order.
                       CONSTRAINT uq_trip_checkout_payment
                           UNIQUE (razorpay_payment_id),

                       CONSTRAINT chk_trip_checkout_total
                           CHECK (
                               total_amount >= 0
                                   AND quoted_total >= 0
                                   AND discount_amount >= 0
                               ),

                       -- A trip may be checked out more than once:
                       -- a payment failure must be retryable, and a
                       -- recovery must be reconcilable.
                       --
                       -- "Only one open checkout per trip" is enforced
                       -- in TripCheckoutService under a row lock on
                       -- the trip, not here. It was originally a
                       -- STORED generated column plus a UNIQUE index
                       -- to emulate a partial index, which MySQL lacks.
                       -- That approach works on its own but silently
                       -- prevents the foreign key above from being
                       -- created, so a checkout row would have been
                       -- un-referable from a bill item. A service-level
                       -- guarantee on a locked row is the honest
                       -- trade here.
                       INDEX idx_trip_checkout_trip (trip_id, status),
                       INDEX idx_trip_checkout_status (status)
) ENGINE = InnoDB;


-- ------------------------------------------------------------
-- IMMUTABLE BILL SNAPSHOT
--
-- Written once per checkout attempt, before payment. Never
-- updated, never deleted: this is what the traveller agreed to.
--
-- A new attempt after a price change writes NEW rows with the
-- new figures, so the difference between the two attempts is
-- visible and explainable.
--
-- selection_id is deliberately NOT a foreign key with a cascade.
-- The line item must outlive the selection it describes.
-- ------------------------------------------------------------

CREATE TABLE trip_bill_items (
                          bill_item_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                          checkout_id BIGINT NOT NULL,

                          selection_id BIGINT NULL,

                          line_type ENUM(
                              'HOTEL',
                              'GUIDE',
                              'CAB',
                              'ACTIVITY',
                              'TAX',
                              'FEE',
                              'DISCOUNT'
                              ) NOT NULL,

                          label VARCHAR(200) NOT NULL,

                          -- Secondary grouping shown in the bill,
                          -- e.g. "Puri, 12-14 Mar".
                          detail VARCHAR(200) NULL,

                          quantity INT NOT NULL DEFAULT 1,

                          unit_amount DECIMAL(12, 2)
                              NOT NULL DEFAULT 0.00,

                          amount DECIMAL(12, 2)
                              NOT NULL DEFAULT 0.00,

                          currency VARCHAR(3) NOT NULL DEFAULT 'INR',

                          created_at TIMESTAMP(6)
                              NOT NULL
                              DEFAULT CURRENT_TIMESTAMP(6),

                          CONSTRAINT fk_trip_bill_items_checkout
                              FOREIGN KEY (checkout_id)
                                  REFERENCES trip_checkout(checkout_id)
                                  ON DELETE CASCADE,

                          CONSTRAINT chk_trip_bill_items_amounts
                              CHECK (
                                  amount >= 0
                                      AND unit_amount >= 0
                                      AND quantity > 0
                                  ),

                          INDEX idx_trip_bill_items_checkout (checkout_id)
) ENGINE = InnoDB;


-- ------------------------------------------------------------
-- MILESTONES  (TP-11)
--
-- The treasure map is driven by these rows, not by hard-coded
-- animation progress. A checkpoint is marked complete only when
-- something real happened: a payment cleared, a stay was checked
-- into. That is what section 15 means by "map animation is driven
-- by Trip Milestones rather than hard-coded visual progress".
-- ------------------------------------------------------------

CREATE TABLE trip_milestones (
                        milestone_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                        trip_id BIGINT NOT NULL,

                        trip_city_id BIGINT NULL,

                        milestone_type ENUM(
                            'TRIP_STARTED',
                            'CITY_ARRIVED',
                            'CITY_DEPARTED',
                            'CHECKED_IN',
                            'CHECKED_OUT',
                            'TRIP_COMPLETED',
                            'REVIEW_OPENED'
                            ) NOT NULL,

                        -- How far along the journey this sits, 0-100.
                        -- Drives the dotted route drawing, so it is
                        -- stored rather than derived: deriving it
                        -- would mean a query on every animation
                        -- frame.
                        progress_percent INT NOT NULL DEFAULT 0,

                        label VARCHAR(150) NOT NULL,

                        -- A milestone is only "completed" once this
                        -- is set. Until then it is planned, and the
                        -- UI must not animate it as done.
                        completed BOOLEAN NOT NULL DEFAULT FALSE,

                        completed_at TIMESTAMP(6) NULL,

                        created_at TIMESTAMP(6)
                            NOT NULL
                            DEFAULT CURRENT_TIMESTAMP(6),

                        CONSTRAINT fk_trip_milestones_trip
                            FOREIGN KEY (trip_id)
                                REFERENCES trips(trip_id)
                                ON DELETE CASCADE,

                        CONSTRAINT fk_trip_milestones_trip_city
                            FOREIGN KEY (trip_city_id)
                                REFERENCES trip_cities(trip_city_id)
                                ON DELETE CASCADE,

                        CONSTRAINT chk_trip_milestones_progress
                            CHECK (
                                progress_percent >= 0
                                    AND progress_percent <= 100
                                ),

                        -- A completed milestone without a timestamp
                        -- cannot be ordered against the others, so
                        -- the two are required together.
                        CONSTRAINT chk_trip_milestones_completed
                            CHECK (
                                completed = FALSE
                                    OR completed_at IS NOT NULL
                                ),

                        CONSTRAINT uq_trip_milestones_type
                            UNIQUE (trip_id, milestone_type, trip_city_id),

                        -- Ordered read for the map, which renders
                        -- checkpoints in journey order.
                        INDEX idx_trip_milestones_trip (
                            trip_id,
                            progress_percent
                            )
) ENGINE = InnoDB;