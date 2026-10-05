INSERT INTO countries (name, iso_code)
VALUES
    ('India', 'IN'),
    ('Nepal', 'NP'),
    ('Bhutan', 'BT'),
    ('Sri Lanka', 'LK'),
    ('Thailand', 'TH');

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Odisha', 'EAST'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'West Bengal', 'EAST'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Bihar', 'EAST'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Jharkhand', 'EAST'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Delhi', 'NORTH'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Rajasthan', 'WEST'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Maharashtra', 'WEST'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Goa', 'WEST'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Karnataka', 'SOUTH'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Kerala', 'SOUTH'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Tamil Nadu', 'SOUTH'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Andhra Pradesh', 'SOUTH'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Telangana', 'SOUTH'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Uttar Pradesh', 'NORTH'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Uttarakhand', 'NORTH'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Himachal Pradesh', 'NORTH'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Gujarat', 'WEST'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Madhya Pradesh', 'CENTRAL'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Assam', 'NORTH_EAST'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Sikkim', 'NORTH_EAST'
FROM countries
WHERE iso_code = 'IN';

INSERT INTO states (country_id, name, region_zone)
SELECT country_id, 'Arunachal Pradesh', 'NORTH_EAST'
FROM countries
WHERE iso_code = 'IN';



INSERT INTO tourist_places
(
    state_id,
    name,
    description,
    entry_fee,
    currency_code,
    image_url,
    latitude,
    longitude
)
SELECT
    state_id,
    'Jagannath Temple',
    'A major temple and pilgrimage destination in Puri, Odisha.',
    0.00,
    'INR',
    NULL,
    19.8049,
    85.8179
FROM states
WHERE name = 'Odisha'
  AND country_id = (
    SELECT country_id
    FROM countries
    WHERE iso_code = 'IN'
);


INSERT INTO tourist_places
(
    state_id,
    name,
    description,
    entry_fee,
    currency_code,
    image_url,
    latitude,
    longitude
)
SELECT
    state_id,
    'Konark Sun Temple',
    'Historic Sun Temple at Konark in Odisha.',
    40.00,
    'INR',
    NULL,
    19.8876,
    86.0945
FROM states
WHERE name = 'Odisha'
  AND country_id = (
    SELECT country_id
    FROM countries
    WHERE iso_code = 'IN'
);


INSERT INTO tourist_places
(
    state_id,
    name,
    description,
    entry_fee,
    currency_code,
    image_url,
    latitude,
    longitude
)
SELECT
    state_id,
    'Puri Beach',
    'Popular coastal destination in Puri, Odisha.',
    0.00,
    'INR',
    NULL,
    19.7983,
    85.8245
FROM states
WHERE name = 'Odisha'
  AND country_id = (
    SELECT country_id
    FROM countries
    WHERE iso_code = 'IN'
);


INSERT INTO tourist_places
(
    state_id,
    name,
    description,
    entry_fee,
    currency_code,
    image_url,
    latitude,
    longitude
)
SELECT
    state_id,
    'Udayagiri and Khandagiri Caves',
    'Historic rock-cut caves near Bhubaneswar.',
    25.00,
    'INR',
    NULL,
    20.2619,
    85.7750
FROM states
WHERE name = 'Odisha'
  AND country_id = (
    SELECT country_id
    FROM countries
    WHERE iso_code = 'IN'
);



INSERT INTO tourist_places
(
    state_id,
    name,
    description,
    entry_fee,
    currency_code,
    latitude,
    longitude
)
SELECT
    state_id,
    'Victoria Memorial',
    'Historic landmark and museum in Kolkata.',
    50.00,
    'INR',
    22.5448,
    88.3426
FROM states
WHERE name = 'West Bengal';


INSERT INTO tourist_places
(
    state_id,
    name,
    description,
    entry_fee,
    currency_code,
    latitude,
    longitude
)
SELECT
    state_id,
    'Darjeeling',
    'Mountain destination known for Himalayan views and tea gardens.',
    0.00,
    'INR',
    27.0410,
    88.2663
FROM states
WHERE name = 'West Bengal';




