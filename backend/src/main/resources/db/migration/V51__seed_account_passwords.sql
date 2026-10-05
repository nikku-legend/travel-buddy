-- =====================================================================
-- V51: give the seeded demo accounts a password that actually works
-- =====================================================================
--
-- V16 seeded the hotel partner with a bcrypt hash whose plaintext
-- nobody knows, and V50 copied the same hash into the guide and cab
-- partner accounts. None of them can log in: every attempt returns
-- 401, so the seeded guides and cabs exist in listings but have no
-- way to reach the portal that manages them.
--
-- A demo seed whose accounts cannot be used is worse than no seed at
-- all -- it looks like the feature is broken rather than absent.
--
-- Scoped deliberately to these four addresses. This touches
-- credentials, so it must never be able to reach a real account:
-- the WHERE clause is an allow-list, not a pattern, and no account
-- outside it is affected.
--
-- Demo password for all four: Travel!2345
-- (Development/demo data only. Never reuse this in production.)
-- =====================================================================

UPDATE users
SET password_hash = '$2a$10$0.1UalMLn5M8xpQtHfy9AO56eahKLjO34yKBOFvGkuUINHh0Wndo.'
WHERE email IN (
    'hotel.partner@travelbuddy.local',
    'guide.partner@travelbuddy.local',
    'cab.partner@travelbuddy.local'
);