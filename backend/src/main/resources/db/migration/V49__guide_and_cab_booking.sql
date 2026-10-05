-- =====================================================================
-- V49: bookings can be a guide or a cab, not only a hotel
-- =====================================================================
--
-- Two gaps blocked booking a guide or a cab from a trip.
--
-- 1. A booking could not say WHAT it was for. bookings had no type,
--    so a guide reservation and a hotel reservation were identical
--    rows distinguishable only by which child table pointed at them.
--    FR-34 commission cannot be computed from that, because the
--    rate differs by service.
--
-- 2. A cab ride was not a booking at all. cab_rides referenced a
--    user and a cab, never a booking, so a ride carried no payment,
--    could not be cancelled, and could not be refunded. A traveller
--    who paid for transport in a trip would have had nothing to show
--    for it.
--
-- Existing rows are hotels: they are the only kind this system could
-- produce before now. That is a fact about the data, not a guess.
-- =====================================================================

ALTER TABLE bookings
    ADD COLUMN booking_type ENUM('HOTEL', 'GUIDE', 'CAB')
        NOT NULL DEFAULT 'HOTEL'
        AFTER booking_reference;

-- Commission, settlement and refund all ask "of what" first.
CREATE INDEX idx_bookings_type ON bookings (booking_type, booking_status);

-- A ride that was paid for, so it can be cancelled and refunded.
-- RESTRICT, not CASCADE: deleting a paid booking must not silently
-- delete the ride a traveller was given.
ALTER TABLE cab_rides
    ADD COLUMN booking_id BIGINT NULL
        AFTER user_id,
    ADD CONSTRAINT fk_cab_ride_booking
        FOREIGN KEY (booking_id) REFERENCES bookings (booking_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT;

CREATE INDEX idx_cab_ride_booking ON cab_rides (booking_id);

-- One guide cannot be in two places on one day, so this pair has to
-- be unique. Without it two concurrent trip checkouts could both
-- reserve the same guide for the same tour date.
ALTER TABLE guide_reservations
    ADD CONSTRAINT uq_guide_reservation_day
        UNIQUE (guide_id, tour_date);