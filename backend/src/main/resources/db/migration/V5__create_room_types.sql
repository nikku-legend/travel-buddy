CREATE TABLE room_types (
                            room_type_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                            property_id BIGINT NOT NULL,

                            category_name VARCHAR(50) NOT NULL,

                            max_occupancy INT NOT NULL DEFAULT 4,

                            base_price_per_night DECIMAL(12,2) NOT NULL,

                            currency_code CHAR(3) NOT NULL DEFAULT 'INR',

                            total_inventory INT NOT NULL DEFAULT 0,

                            is_active BOOLEAN NOT NULL DEFAULT TRUE,

                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                ON UPDATE CURRENT_TIMESTAMP,

                            CONSTRAINT chk_room_max_occupancy
                                CHECK (max_occupancy > 0),

                            CONSTRAINT chk_room_price
                                CHECK (base_price_per_night >= 0),

                            CONSTRAINT chk_room_inventory
                                CHECK (total_inventory >= 0),

                            CONSTRAINT fk_room_types_property
                                FOREIGN KEY (property_id)
                                    REFERENCES properties(property_id)
                                    ON DELETE CASCADE,

                            INDEX idx_room_types_property (property_id),
                            INDEX idx_room_types_active (is_active)
);