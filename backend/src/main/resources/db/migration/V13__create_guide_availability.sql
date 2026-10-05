CREATE TABLE guide_availability (
                                    availability_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                    guide_id BIGINT NOT NULL,

                                    availability_date DATE NOT NULL,

                                    is_available BOOLEAN NOT NULL DEFAULT TRUE,

                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    CONSTRAINT fk_guide_availability_guide
                                        FOREIGN KEY (guide_id)
                                            REFERENCES guides(guide_id)
                                            ON DELETE CASCADE,

                                    UNIQUE KEY uq_guide_availability_date (
                                                                           guide_id,
                                                                           availability_date
                                        ),

                                    INDEX idx_guide_availability_date (
                                                                       availability_date
                                        )
);