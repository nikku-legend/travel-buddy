CREATE TABLE properties (
                            property_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                            partner_id BIGINT NOT NULL,

                            state_id INT NOT NULL,

                            name VARCHAR(150) NOT NULL,

                            property_type ENUM(
                                'HOTEL',
                                'VILLA',
                                'RESORT',
                                'HOMESTAY'
                                ) NOT NULL,

                            address TEXT NOT NULL,

                            description TEXT,

                            latitude DECIMAL(10,7),

                            longitude DECIMAL(10,7),

                            is_verified BOOLEAN NOT NULL DEFAULT FALSE,

                            is_active BOOLEAN NOT NULL DEFAULT TRUE,

                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                ON UPDATE CURRENT_TIMESTAMP,

                            CONSTRAINT fk_properties_partner
                                FOREIGN KEY (partner_id)
                                    REFERENCES users(user_id)
                                    ON DELETE RESTRICT,

                            CONSTRAINT fk_properties_state
                                FOREIGN KEY (state_id)
                                    REFERENCES states(state_id)
                                    ON DELETE RESTRICT,

                            INDEX idx_properties_partner (partner_id),
                            INDEX idx_properties_state (state_id),
                            INDEX idx_properties_verified (is_verified),
                            INDEX idx_properties_active (is_active),
                            INDEX idx_properties_state_active (state_id, is_active)
);