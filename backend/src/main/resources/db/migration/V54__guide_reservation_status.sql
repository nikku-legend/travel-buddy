-- =====================================================================
-- V54: guide tours can reach a terminal state
-- =====================================================================
-- guide_reservations was the only bookable service in the system with
-- no status column at all. Hotels have RoomStayStatus and advance a
-- stay to CHECKED_OUT from the front desk; cabs have RideStatus and
-- advance a ride through PATCH /partner/cab/rides/{id}/status.
--
-- A guide had no equivalent. Nothing in the codebase ever moved a
-- GuideReservation anywhere, and nothing ever set its Booking to
-- COMPLETED, so two downstream features were permanently unreachable
-- for any trip containing a guide:
--
--   * TripReviewService gates guide eligibility on a COMPLETED
--     booking, so a guide review card could never unlock.
--   * TripMilestoneService.completeTripIfFinished requires every
--     booked selection to be COMPLETED or CANCELLED, so a trip with a
--     guide could never be marked complete, and the Review Center
--     never opened for it.
--
-- Existing rows backfill to CONFIRMED. A tour that has already been
-- given is still recorded as outstanding until the guide reports it,
-- which is the honest default: the new column is the source of truth
-- and nothing claimed otherwise before.
-- ---------------------------------------------------------------------
ALTER TABLE guide_reservations
    ADD COLUMN status ENUM(
        'CONFIRMED',
        'IN_PROGRESS',
        'COMPLETED',
        'CANCELLED'
        ) NOT NULL DEFAULT 'CONFIRMED';

-- The partner portal lists a guide's tours newest-first and will want
-- "what still needs closing out", which is status-first.
CREATE INDEX idx_guide_reservations_guide_status
    ON guide_reservations (guide_id, status);
