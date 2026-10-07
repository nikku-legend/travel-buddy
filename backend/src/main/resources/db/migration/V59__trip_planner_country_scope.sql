ALTER TABLE trips
    ADD COLUMN country_id INT NULL AFTER region_name,
    ADD CONSTRAINT fk_trips_country
        FOREIGN KEY (country_id) REFERENCES countries (country_id);
