-- ============================================================
-- V16 - Demo hotel/property data
-- Development/demo data only
-- ============================================================

-- Demo partner users
INSERT INTO users (
    full_name,
    email,
    password_hash,
    phone_number,
    role
)
SELECT
    'Travel Buddy Hotel Partner',
    'hotel.partner@travelbuddy.local',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoOeQ5h9j7fW2xL7hXjQ2qJ7pM5q5u7K3K',
    '9000000001',
    'ROLE_HOTEL_PARTNER'
WHERE NOT EXISTS (
    SELECT 1
    FROM users
    WHERE email = 'hotel.partner@travelbuddy.local'
);

-- Demo properties
INSERT INTO properties (
    partner_id,
    state_id,
    name,
    property_type,
    address,
    description,
    latitude,
    longitude,
    is_verified,
    is_active
)
SELECT
    u.user_id,
    s.state_id,
    'Coastal Heritage Resort',
    'RESORT',
    'Puri, Odisha',
    'A demo Travel Buddy resort property near the coast.',
    19.8135,
    85.8312,
    TRUE,
    TRUE
FROM users u
         JOIN states s
              ON s.name = 'Odisha'
WHERE u.email = 'hotel.partner@travelbuddy.local'
  AND NOT EXISTS (
    SELECT 1
    FROM properties
    WHERE name = 'Coastal Heritage Resort'
);

INSERT INTO properties (
    partner_id,
    state_id,
    name,
    property_type,
    address,
    description,
    latitude,
    longitude,
    is_verified,
    is_active
)
SELECT
    u.user_id,
    s.state_id,
    'Puri Heritage Stay',
    'HOMESTAY',
    'Puri, Odisha',
    'A demo homestay for testing the Travel Buddy hospitality module.',
    19.8040,
    85.8180,
    TRUE,
    TRUE
FROM users u
         JOIN states s
              ON s.name = 'Odisha'
WHERE u.email = 'hotel.partner@travelbuddy.local'
  AND NOT EXISTS (
    SELECT 1
    FROM properties
    WHERE name = 'Puri Heritage Stay'
);

-- Demo room types for Coastal Heritage Resort
INSERT INTO room_types (
    property_id,
    category_name,
    max_occupancy,
    base_price_per_night,
    currency_code,
    total_inventory,
    is_active
)
SELECT
    p.property_id,
    'Deluxe Room',
    2,
    3500.00,
    'INR',
    10,
    TRUE
FROM properties p
WHERE p.name = 'Coastal Heritage Resort'
  AND NOT EXISTS (
    SELECT 1
    FROM room_types r
    WHERE r.property_id = p.property_id
      AND r.category_name = 'Deluxe Room'
);

INSERT INTO room_types (
    property_id,
    category_name,
    max_occupancy,
    base_price_per_night,
    currency_code,
    total_inventory,
    is_active
)
SELECT
    p.property_id,
    'Family Suite',
    4,
    6500.00,
    'INR',
    5,
    TRUE
FROM properties p
WHERE p.name = 'Coastal Heritage Resort'
  AND NOT EXISTS (
    SELECT 1
    FROM room_types r
    WHERE r.property_id = p.property_id
      AND r.category_name = 'Family Suite'
);

-- Demo room types for Puri Heritage Stay
INSERT INTO room_types (
    property_id,
    category_name,
    max_occupancy,
    base_price_per_night,
    currency_code,
    total_inventory,
    is_active
)
SELECT
    p.property_id,
    'Standard Room',
    2,
    1800.00,
    'INR',
    6,
    TRUE
FROM properties p
WHERE p.name = 'Puri Heritage Stay'
  AND NOT EXISTS (
    SELECT 1
    FROM room_types r
    WHERE r.property_id = p.property_id
      AND r.category_name = 'Standard Room'
);
