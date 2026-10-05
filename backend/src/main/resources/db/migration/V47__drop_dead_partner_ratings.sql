-- ============================================================
-- DROP DEAD PARTNER RATING COLUMNS  (FR-26)
-- ============================================================
--
-- guides.rating and cabs.rating were added in V25 and V26 as
-- DECIMAL(3,2) NOT NULL DEFAULT 5.00, and were never written by
-- anything in the application. The only setRating calls in the
-- codebase have always been on the review itself.
--
-- Reviews for guides and cabs were aggregated correctly into
-- review_summaries, keyed by (target_type, target_id). Nothing
-- read them: GuideService, CabService and HomeDiscoveryService
-- all read the entity column instead. So the visible consequence
-- was that a guide or cab with forty one-star reviews displayed
-- a flawless 5.00 on its profile, in its listing, and on the
-- home page.
--
-- HomeDiscoveryService additionally SORTED the "top guides"
-- section on that column. Every guide tied at 5.00, so the
-- ordering before LIMIT was whatever the repository returned and
-- the section was an arbitrary selection presented as a
-- ranking.
--
-- All three now read review_summaries, and return NULL when
-- there are no reviews rather than inventing a score.
--
-- Dropped rather than kept in sync because a denormalised rating
-- that silently goes stale is precisely the defect being fixed.
-- The value is not lost: it was always the 5.00 default.
--
-- The frontend had the same flaw independently, rendering
-- `guide.rating || "5.0"`, so those fallbacks were removed too.

ALTER TABLE guides DROP COLUMN rating;

ALTER TABLE cabs DROP COLUMN rating;