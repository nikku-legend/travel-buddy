-- ============================================================
-- V50 - Demo guides and cabs
-- Development/demo data only
-- ============================================================
--
-- The planner can now book a guide and a cab, and every code path
-- for it is tested. But a fresh database had NO guides and NO cabs
-- at all, so the feature was unreachable on a clean install and
-- the new planner step would have shown two empty lists.
--
-- Written the way V16 seeds properties: INSERT ... SELECT ...
-- WHERE NOT EXISTS, so re-running it cannot duplicate a row.
--
-- Odisha only, to match the geography and stays already seeded.
-- A guide is linked to a user because Guide has no name of its own;
-- that is the data model, not a shortcut.
-- ============================================================

-- Guide partner account
INSERT INTO users (
    full_name,
    email,
    password_hash,
    phone_number,
    role
)
SELECT
    'Odisha Heritage Guide',
    'guide.partner@travelbuddy.local',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoOeQ5h9j7fW2xL7hXjQ2qJ7pM5q5u7K3K',
    '9000000011',
    'ROLE_GUIDE_PARTNER'
WHERE NOT EXISTS (
    SELECT 1
    FROM users
    WHERE email = 'guide.partner@travelbuddy.local'
);

-- Cab partner account
INSERT INTO users (
    full_name,
    email,
    password_hash,
    phone_number,
    role
)
SELECT
    'Puri Cabs',
    'cab.partner@travelbuddy.local',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoOeQ5h9j7fW2xL7hXjQ2qJ7pM5q5u7K3K',
    '9000000012',
    'ROLE_CAB_PARTNER'
WHERE NOT EXISTS (
    SELECT 1
    FROM users
    WHERE email = 'cab.partner@travelbuddy.local'
);

-- Demo guides, one per seeded state
INSERT INTO guides (
    user_id,
    state_id,
    daily_rate,
    currency_code,
    years_of_experience,
    is_verified,
    is_active,
    bio
)
SELECT
    u.user_id,
    s.state_id,
    2500.00,
    'INR',
    8,
    1,
    1,
    'Licensed Odisha heritage guide covering Puri, Konark and
     Bhubaneswar.'
FROM users u
JOIN states s
    ON s.name = 'Odisha'
WHERE u.email = 'guide.partner@travelbuddy.local'
  AND NOT EXISTS (
      SELECT 1
      FROM guides
      WHERE user_id = u.user_id
  );

-- Demo cabs
INSERT INTO cabs (
    partner_id,
    state_id,
    vehicle_name,
    vehicle_type,
    registration_number,
    seating_capacity,
    driver_name,
    driver_phone,
    price_per_km,
    base_fare,
    is_available,
    is_verified,
    is_active
)
SELECT
    u.user_id,
    s.state_id,
    'Puri Heritage Sedan',
    'SEDAN',
    'OD07AB1001',
    4,
    'Ravi Kumar',
    '9876500011',
    18.00,
    250.00,
    1,
    1,
    1
FROM users u
JOIN states s
    ON s.name = 'Odisha'
WHERE u.email = 'cab.partner@travelbuddy.local'
  AND NOT EXISTS (
      SELECT 1
      FROM cabs
      WHERE partner_id = u.user_id
  );

INSERT INTO cabs (
    partner_id,
    state_id,
    vehicle_name,
    vehicle_type,
    registration_number,
    seating_capacity,
    driver_name,
    driver_phone,
    price_per_km,
    base_fare,
    is_available,
    is_verified,
    is_active
)
SELECT
    u.user_id,
    s.state_id,
    'Puri Family Traveller',
    'TEMPO_TRAVELLER',
    'OD07AB1002',
    8,
    'Suresh Patnaik',
    '9876500012',
    32.00,
    600.00,
    1,
    1,
    1
FROM users u
JOIN states s
    ON s.name = 'Odisha'
WHERE u.email = 'cab.partner@travelbuddy.local'
  AND NOT EXISTS (
      SELECT 1
      FROM cabs
      WHERE partner_id = u.user_id
         AND registration_number = 'OD07AB1002'
  );