CREATE TABLE cabs (
    cab_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    partner_id BIGINT NOT NULL,
    state_id INT NOT NULL,
    vehicle_name VARCHAR(100) NOT NULL,
    vehicle_type ENUM('HATCHBACK', 'SEDAN', 'SUV', 'TEMPO_TRAVELLER') NOT NULL,
    registration_number VARCHAR(50) NOT NULL UNIQUE,
    seating_capacity INT NOT NULL DEFAULT 4,
    driver_name VARCHAR(100) NOT NULL,
    driver_phone VARCHAR(20) NOT NULL,
    price_per_km DECIMAL(10,2) NOT NULL,
    base_fare DECIMAL(10,2) NOT NULL,
    rating DECIMAL(3,2) NOT NULL DEFAULT 5.00,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_cabs_partner
        FOREIGN KEY (partner_id)
            REFERENCES users(user_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_cabs_state
        FOREIGN KEY (state_id)
            REFERENCES states(state_id)
            ON DELETE RESTRICT,

    INDEX idx_cabs_state (state_id),
    INDEX idx_cabs_type (vehicle_type),
    INDEX idx_cabs_active (is_active)
);

CREATE TABLE cab_rides (
    ride_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    cab_id BIGINT NOT NULL,
    pickup_location VARCHAR(255) NOT NULL,
    drop_location VARCHAR(255) NOT NULL,
    pickup_time DATETIME NOT NULL,
    distance_km DECIMAL(8,2) NOT NULL DEFAULT 10.00,
    fare_amount DECIMAL(12,2) NOT NULL,
    status ENUM('CONFIRMED', 'DRIVER_ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'CONFIRMED',
    otp_code VARCHAR(6) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cab_rides_user
        FOREIGN KEY (user_id)
            REFERENCES users(user_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_cab_rides_cab
        FOREIGN KEY (cab_id)
            REFERENCES cabs(cab_id)
            ON DELETE RESTRICT,

    INDEX idx_cab_rides_user (user_id),
    INDEX idx_cab_rides_cab (cab_id),
    INDEX idx_cab_rides_status (status)
);
