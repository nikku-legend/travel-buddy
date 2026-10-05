CREATE TABLE countries (
                           country_id INT AUTO_INCREMENT PRIMARY KEY,

                           name VARCHAR(100) NOT NULL,

                           iso_code VARCHAR(5) NOT NULL UNIQUE,

                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                           INDEX idx_countries_name (name)
);


CREATE TABLE states (
                        state_id INT AUTO_INCREMENT PRIMARY KEY,

                        country_id INT NOT NULL,

                        name VARCHAR(100) NOT NULL,

                        region_zone ENUM(
        'NORTH',
        'SOUTH',
        'EAST',
        'WEST',
        'CENTRAL',
        'NORTH_EAST'
    ) NOT NULL,

                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        CONSTRAINT fk_states_country
                            FOREIGN KEY (country_id)
                                REFERENCES countries(country_id)
                                ON DELETE CASCADE,

                        INDEX idx_states_country (country_id),
                        INDEX idx_states_region (region_zone),
                        INDEX idx_states_country_region (country_id, region_zone)
);