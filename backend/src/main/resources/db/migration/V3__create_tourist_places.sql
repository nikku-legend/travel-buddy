CREATE TABLE tourist_places (
                                place_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                state_id INT NOT NULL,

                                name VARCHAR(150) NOT NULL,

                                description TEXT,

                                entry_fee DECIMAL(12,2) NOT NULL DEFAULT 0.00,

                                currency_code CHAR(3) NOT NULL DEFAULT 'INR',

                                image_url VARCHAR(500),

                                latitude DECIMAL(10,7),

                                longitude DECIMAL(10,7),

                                is_active BOOLEAN NOT NULL DEFAULT TRUE,

                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    ON UPDATE CURRENT_TIMESTAMP,

                                CONSTRAINT fk_tourist_places_state
                                    FOREIGN KEY (state_id)
                                        REFERENCES states(state_id)
                                        ON DELETE CASCADE,

                                INDEX idx_tourist_places_state (state_id),
                                INDEX idx_tourist_places_name (name),
                                INDEX idx_tourist_places_active (is_active)
);