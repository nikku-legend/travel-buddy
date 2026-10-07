-- ============================================================
-- ACTIVITY BOOKINGS  (TP-10, FR-34)
--
-- The trip planner refuses to book activity selections ("is an
-- activity, which cannot be reserved yet"), which meant a paid
-- trip could carry an activity line it never turned into a
-- booking. TripBookingService now books them, and the booking
-- must say what it is for.
--
-- booking_type was created as a MySQL ENUM in V49 with the
-- three partner services. MySQL rejects a string outside the
-- declared values, so the enum itself has to grow before any
-- ACTIVITY row can be written.
-- ============================================================

ALTER TABLE bookings
    MODIFY booking_type ENUM('HOTEL', 'GUIDE', 'CAB', 'ACTIVITY')
        NOT NULL DEFAULT 'HOTEL';